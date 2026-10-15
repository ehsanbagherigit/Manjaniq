#!/usr/bin/env bash
# Verifies the JNI shared library and its consistency with the Kotlin bridge.
#
# Usage:
#   native/verify_native_lib.sh [path/to/libmanjaniq_native.so]
#
# Default path is the JNI library produced by the CI native build:
#   app/src/main/jniLibs/arm64-v8a/libmanjaniq_native.so
#
# Checks:
#   1. Kotlin side: package, external native method, System.loadLibrary name.
#   2. Library file exists and is non-empty.
#   3. Library file name matches the System.loadLibrary argument.
#   4. JNI symbol Java_com_manjaniq_NativeBridge_GoHelloWorld is exported.
#   5. NewStringUTF_wrapper is NOT an undefined symbol (regression guard for
#      building only bridge.go and silently dropping bridge.c).
#   6. ELF machine matches the ABI directory the library sits in.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SO="${1:-$REPO_ROOT/app/src/main/jniLibs/arm64-v8a/libmanjaniq_native.so}"
KOTLIN="$REPO_ROOT/app/src/main/java/com/manjaniq/NativeBridge.kt"

EXPECTED_PACKAGE="package com.manjaniq"
EXPECTED_METHOD="external fun GoHelloWorld(): String?"
EXPECTED_LOADLIB='System.loadLibrary("manjaniq_native")'
EXPECTED_SYMBOL="Java_com_manjaniq_NativeBridge_GoHelloWorld"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

NM_BIN="${NM:-}"
if [ -z "$NM_BIN" ]; then
  if command -v nm >/dev/null 2>&1; then NM_BIN="nm"
  elif command -v llvm-nm >/dev/null 2>&1; then NM_BIN="llvm-nm"
  else fail "nm/llvm-nm not found (set NM=...)"; fi
fi
READELF_BIN="${READELF:-}"
if [ -z "$READELF_BIN" ]; then
  if command -v readelf >/dev/null 2>&1; then READELF_BIN="readelf"
  elif command -v llvm-readelf >/dev/null 2>&1; then READELF_BIN="llvm-readelf"
  else fail "readelf/llvm-readelf not found (set READELF=...)"; fi
fi

echo "== Verifying Kotlin bridge source =="
test -f "$KOTLIN" || fail "missing $KOTLIN"
grep -qF "$EXPECTED_PACKAGE" "$KOTLIN" || fail "unexpected package declaration in NativeBridge.kt"
grep -qF "$EXPECTED_METHOD" "$KOTLIN" || fail "missing native declaration: $EXPECTED_METHOD"
grep -qF "$EXPECTED_LOADLIB" "$KOTLIN" || fail 'missing System.loadLibrary("manjaniq_native")'
echo "OK: package, native method and library-loading name found"

echo "== Verifying library file: $SO =="
test -f "$SO" || fail "native library not found: $SO"
test -s "$SO" || fail "native library is empty: $SO"

BASENAME="$(basename "$SO")"
case "$BASENAME" in
  lib*.so) LIB_STEM="${BASENAME#lib}"; LIB_STEM="${LIB_STEM%.so}" ;;
  *) fail "library file name must look like lib<name>.so: $BASENAME" ;;
esac
LOAD_NAME="manjaniq_native"
test "$LIB_STEM" = "$LOAD_NAME" \
  || fail "library file '$BASENAME' does not match System.loadLibrary(\"$LOAD_NAME\")"
echo "OK: file name matches System.loadLibrary(\"$LOAD_NAME\")"

echo "== Verifying exported symbols =="
DEFINED="$("$NM_BIN" -D --defined-only "$SO")"
UNDEFINED="$("$NM_BIN" -D --undefined-only "$SO")"

echo "$DEFINED" | grep -qE "[[:space:]]T[[:space:]]+$EXPECTED_SYMBOL\$" \
  || fail "JNI symbol $EXPECTED_SYMBOL is not exported"
echo "OK: $EXPECTED_SYMBOL is exported"

if echo "$UNDEFINED" | grep -q "NewStringUTF_wrapper"; then
  fail "NewStringUTF_wrapper is undefined: bridge.c was not part of the native build"
fi
echo "OK: no undefined NewStringUTF_wrapper (bridge.c linked in)"

echo "== Verifying ELF header =="
ELF_HDR="$("$READELF_BIN" -h "$SO")"
ABI_DIR="$(basename "$(dirname "$SO")")"
case "$ABI_DIR" in
  arm64-v8a) EXPECTED_MACHINE="AArch64" ;;
  armeabi-v7a) EXPECTED_MACHINE="ARM" ;;
  x86_64) EXPECTED_MACHINE="X86-64" ;;
  x86) EXPECTED_MACHINE="Intel 80386" ;;
  *) EXPECTED_MACHINE="" ;;
esac
echo "$ELF_HDR" | grep -q "ELF" || fail "not an ELF file: $SO"
if [ -n "$EXPECTED_MACHINE" ]; then
  echo "$ELF_HDR" | grep -q "Machine:.*$EXPECTED_MACHINE" \
    || fail "ELF machine does not match ABI directory $ABI_DIR (expected $EXPECTED_MACHINE)"
  echo "OK: ELF machine matches $ABI_DIR ($EXPECTED_MACHINE)"
fi

echo "PASS: native library verification succeeded"
