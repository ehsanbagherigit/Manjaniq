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
#   1. Kotlin side: package, external native methods, System.loadLibrary name.
#   2. Library file exists and is non-empty.
#   3. Library file name matches the System.loadLibrary argument.
#   4. Every Kotlin external method has a matching exported JNI symbol
#      (symbol list is derived from the Kotlin source, so the two sides
#      cannot drift apart unnoticed).
#   5. C wrapper helpers from bridge.c are present in the library
#      (regression guard for building only bridge.go and silently
#      dropping bridge.c).
#   6. Undefined dynamic symbols that belong to this project (JNI exports,
#      C wrappers) never appear as unresolved; legitimate libc/system
#      symbols resolved at dlopen time are expected and allowed.
#   7. ELF header: valid ELF, correct class and machine for the ABI
#      directory the library sits in (arm64-v8a -> ELF64 / AArch64).
#   8. Dynamic dependencies look like an Android (bionic) build, not a
#      plain Linux libc build. This is a best-effort static indicator:
#      full proof requires running the library on a device.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SO="${1:-$REPO_ROOT/app/src/main/jniLibs/arm64-v8a/libmanjaniq_native.so}"
KOTLIN="$REPO_ROOT/app/src/main/java/com/manjaniq/NativeBridge.kt"

EXPECTED_PACKAGE="package com.manjaniq"
EXPECTED_METHOD_REGEXP='external[[:space:]]+fun[[:space:]]+[A-Za-z_][A-Za-z0-9_]*'
EXPECTED_LOADLIB='System.loadLibrary("manjaniq_native")'

JBRIDGE_PACKAGE="com/manjaniq/NativeBridge"

# Defined here, before any use: every failure path exits nonzero.
function fail() {
  echo "FAIL: $*" >&2
  exit 1
}

function tool() {
  # tool <env-var> <candidate>...
  local envvar="$1"; shift
  local t="${!envvar:-}"
  if [ -n "$t" ]; then
    if command -v "$t" >/dev/null 2>&1; then
      echo "$t"
      return 0
    fi
    fail "$envvar is set to '$t' but the command was not found"
  fi
  local cand
  for cand in "$@"; do
    if command -v "$cand" >/dev/null 2>&1; then
      echo "$cand"
      return 0
    fi
  done
  return 1
}

NM_BIN="$(tool NM nm llvm-nm)" || fail "nm/llvm-nm not found (set NM=...)"
READELF_BIN="$(tool READELF readelf llvm-readelf)" || fail "readelf/llvm-readelf not found (set READELF=...)"

echo "== Verifying Kotlin bridge source =="
test -f "$KOTLIN" || fail "missing $KOTLIN"
grep -qF "$EXPECTED_PACKAGE" "$KOTLIN" || fail "unexpected package declaration in NativeBridge.kt"
grep -qF "$EXPECTED_LOADLIB" "$KOTLIN" || fail 'missing System.loadLibrary("manjaniq_native")'

# Derive the required JNI symbol set from the Kotlin declarations so the
# verification fails if either side is out of sync.
mapfile -t KOTLIN_METHODS < <(grep -oE "$EXPECTED_METHOD_REGEXP" "$KOTLIN" | awk '{print $NF}' | sort -u)
if [ "${#KOTLIN_METHODS[@]}" -eq 0 ]; then
  fail "no external native methods found in NativeBridge.kt"
fi
echo "OK: package and System.loadLibrary(\"manjaniq_native\") found; native methods: ${KOTLIN_METHODS[*]}"

echo "== Verifying library file: $SO =="
test -f "$SO" || fail "native library not found: $SO"
test -s "$SO" || fail "native library is empty: $SO"

BASENAME="$(basename "$SO")"
case "$BASENAME" in
  lib*.so) LIB_STEM="${BASENAME#lib}"; LIB_STEM="${LIB_STEM%.so}" ;;
  *) fail "library file must look like lib<name>.so: $BASENAME"
esac

LOAD_NAME="$(grep -oE 'System\.loadLibrary\("[A-Za-z0-9_]+"\)' "$KOTLIN" | head -n 1 | sed -E 's/System\.loadLibrary\("([^"]+)"\)/\1/')"
test "$LIB_STEM" = "$LOAD_NAME" || fail "library file '$BASENAME' does not match System.loadLibrary('$LOAD_NAME')"
echo "OK: file name matches System.loadLibrary('$LOAD_NAME')"

echo "== Verifying ELF header =="
ELF_LINE="$("$READELF_BIN" -h "$SO" 2>/dev/null | grep -E '^  Class:|^  Machine:|^  Type:' || true)"
[ -n "$ELF_LINE" ] || fail "$SO is not a valid ELF file (empty ELF header)"
report="$(echo "$ELF_LINE" | paste -sd' ' -)"
echo "ELF header: $report"

ELF_CLASS="$(echo "$ELF_LINE" | sed -nE 's/^  Class:[[:space:]]+//p' | head -n 1)"
ELF_TYPE="$(echo "$ELF_LINE" | sed -nE 's/^  Type:[[:space:]]+//p' | head -n 1)"
test -n "$ELF_CLASS" || fail "cannot read ELF class of $SO"
test -n "$ELF_TYPE" || fail "cannot read ELF type of $SO"

# The library must be a shared object, not an executable or object file.
case "$ELF_TYPE" in
  DYN*) echo "OK: ELF type DYN (shared object)" ;;
  *) fail "ELF type is '$ELF_TYPE', expected DYN (shared object)" ;;
esac

# Tie the ELF class/machine to the ABI directory the library sits in, when
# that directory name is a known Android ABI.
ABI_DIR="$(basename "$(cd "$(dirname "$SO")" && pwd)")"
case "$ABI_DIR" in
  arm64-v8a) EXPECTED_CLASS="ELF64"; EXPECTED_MACHINE="AArch64" ;;
  armeabi-v7a) EXPECTED_CLASS="ELF32"; EXPECTED_MACHINE="ARM" ;;
  x86_64) EXPECTED_CLASS="ELF64"; EXPECTED_MACHINE="Advanced Micro Devices X86-64" ;;
  x86) EXPECTED_CLASS="ELF32"; EXPECTED_MACHINE="Intel 80386" ;;
  *) EXPECTED_CLASS=""; echo "INFO: ABI directory '$ABI_DIR' is not a standard Android ABI; skipping class/machine check" ;;
esac
if [ -n "$EXPECTED_CLASS" ]; then
  test "$ELF_CLASS" = "$EXPECTED_CLASS" || fail "ELF class is '$ELF_CLASS', expected '$EXPECTED_CLASS' for ABI $ABI_DIR"
  ELF_MACHINE="$(echo "$ELF_LINE" | sed -nE 's/^  Machine:[[:space:]]+//p' | head -n 1)"
  test "$ELF_MACHINE" = "$EXPECTED_MACHINE" \
    || fail "ELF machine is '$ELF_MACHINE', expected '$EXPECTED_MACHINE' for ABI $ABI_DIR"
  echo "OK: ELF class '$ELF_CLASS' and machine '$ELF_MACHINE' match ABI $ABI_DIR"
fi

echo "== Verifying Android runtime target indicators =="
NEEDED="$("$READELF_BIN" -d "$SO" 2>/dev/null | grep -E 'NEEDED' | sed -E 's/.*\[(.*)\].*/\1/' || true)"
NON_ANDROID_NEEDED="$(echo "$NEEDED" | grep -oE '^(ld-linux|libc\.so\.[0-9]+|libpthread\.so\.0|libdl\.so\.2|libm\.so\.6|libstdc\+\+\.so\.6|musl)' || true)"
if [ -n "$NON_ANDROID_NEEDED" ]; then
  fail "library links plain Linux system libraries ([$NON_ANDROID_NEEDED]); it must target Android (bionic: libc.so, liblog.so, ...). Did the build run without the NDK/NativeAPI toolchain?"
fi
if [ -n "$NEEDED" ]; then
  echo "INFO: DT_NEEDED entries: $(echo "$NEEDED" | paste -sd' ' -)"
  if echo "$NEEDED" | grep -qE '^(libc\.so|liblog\.so)$'; then
    echo "OK: DT_NEEDED is consistent with an Android (bionic) build"
  else
    echo "WARN: DT_NEEDED entries did not match a known bionic pattern; verify with runtime testing on a device"
  fi
else
  echo "WARN: no DT_NEEDED entries found; library builds its dependencies statically and must be validated by runtime testing"
fi

echo "== Verifying JNI symbols =="
DEFINED_SYMBOLS="$("$NM_BIN" -D --defined-only "$SO" 2>/dev/null | awk '{print $NF}' || true)"
[ -n "$DEFINED_SYMBOLS" ] || fail "no defined dynamic symbols found in $SO (is it a valid shared library, and does $NM_BIN read this ELF format?)"

# 1. Every Kotlin external method must have a matching exported symbol
#    Java_com_manjaniq_NativeBridge_<Method> for the NativeBridge host class.
for m in "${KOTLIN_METHODS[@]}"; do
  if echo "$m" | grep -qE '[^A-Za-z0-9_]'; then
    fail "invalid Kotlin method name: $m"
  fi
  sym="Java_${JBRIDGE_PACKAGE//\//_}_${m}"
  echo "$DEFINED_SYMBOLS" | grep -qE "^${sym}\$" \
    || fail "Kotlin native method $m has no matching exported JNI symbol $sym"
done
echo "OK: all ${#KOTLIN_METHODS[@]} Kotlin native methods have exported JNI symbols"

# 2. C wrapper helpers implemented in bridge.c must exist in the library.
WRAPPERS=(NewStringUTF_wrapper GetStringUTFChars_wrapper ReleaseStringUTFChars_wrapper jstringIsNil)
for sym in "${WRAPPERS[@]}"; do
  echo "$DEFINED_SYMBOLS" | grep -qE "^${sym}\$" \
    || fail "C wrapper symbol $sym (native/bridge.c) is not defined in $SO. bridge.c was probably not linked into the library."
done
echo "OK: bridge.c wrapper symbols are defined"

# 3. Undefined symbols from this project must never appear unresolved.
#    System symbols (libc, pthread, netdb, ...) are resolved at dlopen
#    time and are expected; failures here target dropped JNI code.
UNDEFINED_SYMBOLS="$("$NM_BIN" -D --undefined-only "$SO" 2>/dev/null | awk '{print $NF}' | sed -E 's/@.*$//' || true)"
PROJECT_PROBLEM="$(echo "$UNDEFINED_SYMBOLS" | grep -E '^(Java_[A-Za-z0-9_]+|NewStringUTF_wrapper|GetStringUTFChars_wrapper|ReleaseStringUTFChars_wrapper|jstringIsNil)$' || true)"
if [ -n "$PROJECT_PROBLEM" ]; then
  fail "undefined project symbols found (missing implementation code): $PROJECT_PROBLEM"
fi
UND_COUNT="$(echo "$UNDEFINED_SYMBOLS" | grep -c . || true)"
echo "OK: no undefined project/JNI symbols ($UND_COUNT dynamic system symbols are resolved at load time, which is expected)"

echo "PASS: native library verification succeeded"
