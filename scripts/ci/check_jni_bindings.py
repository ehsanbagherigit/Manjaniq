#!/usr/bin/env python3
"""JNI binding gate: every Kotlin `external fun` must have a native symbol.

The Kotlin side of both bridges declares `external fun`s; the native side exports
`Java_<package>_<Class>_<method>` symbols (Rust in `aether-wrapper/src/lib.rs`, Go
via cgo in `native/`). A rename on either side compiles fine and fails only on the
device, with an `UnsatisfiedLinkError` at the first call - which is exactly how the
Aether start path can look correct in review and still be dead at runtime.

This script reads both sides statically:

  * every `external fun` in a Kotlin object, paired with its file's package and
    object name, becomes the symbol JNI will look for;
  * every `Java_...` export in the native sources is collected.

It fails when a Kotlin declaration has no symbol (a call that would throw), and it
warns when an exported symbol has no Kotlin caller (dead native code, left behind by
an earlier design). Exit status is non-zero only for the first case.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

KOTLIN_SOURCES = [
    ROOT / "app" / "src" / "main" / "java" / "com" / "manjaniq" / "AetherBridge.kt",
    ROOT / "app" / "src" / "main" / "java" / "com" / "manjaniq" / "NativeBridge.kt",
]
NATIVE_SOURCES = [
    ROOT / "aether-wrapper" / "src" / "lib.rs",
    *sorted((ROOT / "native").glob("*.go")),
]

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
OBJECT_RE = re.compile(r"^\s*(?:object|class)\s+(\w+)", re.MULTILINE)
EXTERNAL_RE = re.compile(r"^\s*(?:private\s+)?external\s+fun\s+(\w+)\s*\(", re.MULTILINE)
RUST_EXPORT_RE = re.compile(r"pub\s+extern\s+\"C\"\s+fn\s+(Java_[\w]+)")
GO_EXPORT_RE = re.compile(r"^//export\s+(Java_[\w]+)", re.MULTILINE)


def kotlin_symbols() -> dict[str, Path]:
    """`Java_pkg_Class_method` for every Kotlin external, mapped to its file."""
    found: dict[str, Path] = {}
    for path in KOTLIN_SOURCES:
        text = path.read_text(encoding="utf-8")
        package = PACKAGE_RE.search(text)
        if package is None:
            print(f"::error file={path}::no package declaration")
            continue
        # The bridge files each hold one object; take the first declared one.
        holder = OBJECT_RE.search(text)
        if holder is None:
            print(f"::error file={path}::no object or class declaration")
            continue
        prefix = "Java_" + package.group(1).replace(".", "_") + "_" + holder.group(1) + "_"
        for name in EXTERNAL_RE.findall(text):
            found[prefix + name] = path
    return found


def native_symbols() -> dict[str, Path]:
    found: dict[str, Path] = {}
    for path in NATIVE_SOURCES:
        text = path.read_text(encoding="utf-8")
        pattern = RUST_EXPORT_RE if path.suffix == ".rs" else GO_EXPORT_RE
        for symbol in pattern.findall(text):
            found[symbol] = path
    return found


def main() -> int:
    kotlin = kotlin_symbols()
    native = native_symbols()

    missing = sorted(sym for sym in kotlin if sym not in native)
    unused = sorted(sym for sym in native if sym not in kotlin)

    print(f"Kotlin externals: {len(kotlin)}")
    for symbol in sorted(kotlin):
        print(f"  {symbol}  ({kotlin[symbol].relative_to(ROOT)})")
    print(f"Native exports: {len(native)}")

    for symbol in unused:
        print(
            f"::warning title=Unused JNI export::{symbol} is exported by "
            f"{native[symbol].relative_to(ROOT)} but no Kotlin `external fun` calls it"
        )

    if missing:
        for symbol in missing:
            print(
                f"::error title=Missing JNI symbol::{symbol} is declared in "
                f"{kotlin[symbol].relative_to(ROOT)} but no native source exports it: "
                "the call would fail with UnsatisfiedLinkError on the device"
            )
        return 1

    print("Every Kotlin external has a native symbol.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
