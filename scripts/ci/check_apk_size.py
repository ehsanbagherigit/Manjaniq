#!/usr/bin/env python3
"""APK size gate: report what is inside the artifact and fail when it is too big.

WHY A GATE, NOT A PRINT
-----------------------
The application is a proxy: almost all of its bytes are native
(`libaether.so` - Rust, BoringSSL and quiche - plus the Go engine, the C++ STL and
the Psiphon helper), one copy of each *per ABI*. A second architecture, an
unstripped shared library or a vector-icon pack is invisible in a source review
and shows up only as a download nobody wants. So the size is measured, printed,
and enforced on the one APK a physical device installs.

WHAT IT PRINTS
--------------
* the APK size in MiB, and the summed uncompressed/compressed sizes of its
  `lib/` entries, so the native share is obvious;
* the ten largest entries by uncompressed size;
* one `::warning title=APK size::` annotation with the headline numbers, so the
  figure is readable from the check-run annotations without downloading a log
  (GitHub keeps at most ten warning annotations per step).

WHAT IT ENFORCES
----------------
`--limit-mb` (default 40) applies to every APK given on the command line. Going
over is an `::error` annotation and exit status 1.

Usage:
    scripts/ci/check_apk_size.py [--limit-mb 40] APK [APK ...]
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

MIB = 1024 * 1024


def human(size: int) -> str:
    return f"{size / MIB:.2f} MiB"


def inspect(apk: Path) -> tuple[int, list[tuple[str, int, int]], tuple[int, int]]:
    """(apk bytes, [(name, uncompressed, compressed)], (lib uncompressed, lib compressed))"""
    total = apk.stat().st_size
    entries: list[tuple[str, int, int]] = []
    lib_raw = lib_zip = 0
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            entries.append((info.filename, info.file_size, info.compress_size))
            if info.filename.startswith("lib/"):
                lib_raw += info.file_size
                lib_zip += info.compress_size
    entries.sort(key=lambda item: item[1], reverse=True)
    return total, entries, (lib_raw, lib_zip)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit-mb", type=float, default=40.0)
    parser.add_argument("apks", nargs="+", type=Path)
    args = parser.parse_args()

    limit = int(args.limit_mb * MIB)
    failures: list[str] = []

    for apk in args.apks:
        if not apk.is_file():
            print(f"::error title=APK size::no APK at {apk}")
            failures.append(f"{apk}: missing")
            continue

        total, entries, (lib_raw, lib_zip) = inspect(apk)
        verdict = "OK" if total <= limit else "OVER"
        print(f"\n=== {apk.name}: {human(total)} ({total} bytes) - limit "
              f"{human(limit)} [{verdict}] ===")
        print(f"    native (lib/): {human(lib_raw)} uncompressed, {human(lib_zip)} in the APK "
              f"({(lib_zip / total * 100) if total else 0:.1f}% of the file)")
        print("    largest entries (uncompressed):")
        for name, raw, packed in entries[:10]:
            print(f"      {human(raw):>12}  ({human(packed):>12} stored)  {name}")

        # One annotation carries the headline numbers; a second names the biggest
        # contributor, which is what a size regression almost always is.
        print(f"::warning title=APK size::{apk.name} is {human(total)} "
              f"(native {human(lib_zip)}), limit {human(limit)}, {verdict}")
        if entries:
            biggest = entries[0]
            print(f"::warning title=APK largest entry::{apk.name}: {biggest[0]} is "
                  f"{human(biggest[1])} uncompressed ({human(biggest[2])} stored)")

        if total > limit:
            failures.append(
                f"{apk.name} is {human(total)}, over the {human(limit)} limit"
            )

    if failures:
        for failure in failures:
            print(f"::error title=APK size gate::{failure}")
        return 1

    print("\nAPK size gate: every artifact is within the limit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
