#!/usr/bin/env python3
"""Cross-checks the JNI contract between the Kotlin bridge and
the Go native library — without needing the built .so.

Derives the required JNI export names from the Kotlin
`external fun` declarations (package com.manjaniq, class
NativeBridge) and compares them with the Go `//export`
directives in native/bridge.go. Catches drift between the
two sides before the native build runs.

Exit code 0 = contract holds, 1 = mismatch.
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
KOTLIN = REPO / "app/src/main/java/com/manjaniq/NativeBridge.kt"
GO = REPO / "native/bridge.go"

JNI_PREFIX = "Java_com_manjaniq_NativeBridge_"


def kotlin_external_methods() -> set:
    text = KOTLIN.read_text(encoding="utf-8")
    methods = set(re.findall(r"external\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)", text))
    if not methods:
        sys.exit("FATAL: no external native methods found in NativeBridge.kt")
    return methods


def go_exports() -> set:
    text = GO.read_text(encoding="utf-8")
    names = set()
    for match in re.finditer(r"//export\s+(\S+)", text):
        symbol = match.group(1)
        if symbol.startswith(JNI_PREFIX):
            names.add(symbol[len(JNI_PREFIX):])
    return names


def main() -> int:
    kotlin = kotlin_external_methods()
    go = go_exports()

    ok = True
    for method in sorted(kotlin - go):
        print(f"FAIL: Kotlin declares external fun {method}() "
              f"but Go has no matching //export "
              f"{JNI_PREFIX}{method}")
        ok = False
    for symbol in sorted(go - kotlin):
        print(f"FAIL: Go exports {JNI_PREFIX}{symbol} but the Kotlin "
              f"bridge declares no matching external fun")
        ok = False

    if ok:
        print(f"OK: JNI contract holds — {len(kotlin)} methods match "
              f"on both sides: {', '.join(sorted(kotlin))}")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
