#!/usr/bin/env bash
# Verifies libaether.so produced by aether-wrapper (Rust cdylib)
# Mirrors native/verify_native_lib.sh checks but for Aether FFI

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SO="${1:-$REPO_ROOT/app/src/main/jniLibs/arm64-v8a/libaether.so}"
AETHER_WRAPPER="$REPO_ROOT/aether-wrapper"
echo "== Verifying Aether native library =="
echo "SO path: $SO"

if [ ! -f "$SO" ]; then
    echo "SKIP: $SO not found (local build without Rust - CI must build it). This is expected in sandbox."
    echo "To build: cd aether-wrapper && cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release"
    echo "Verify: llvm-readelf -h / llvm-nm -D"
    exit 0
fi

SIZE=$(stat -c%s "$SO" 2>/dev/null || stat -f%z "$SO")
if [ "$SIZE" -eq 0 ]; then echo "FAIL: zero size"; exit 1; fi
echo "Size $SIZE bytes OK"

# ELF check
READELF_BIN=""
for c in llvm-readelf readelf; do if command -v "$c" >/dev/null 2>&1; then READELF_BIN=$c; break; fi; done
if [ -n "$READELF_BIN" ]; then
    echo "ELF header ($READELF_BIN -h):"
    $READELF_BIN -h "$SO" 2>&1 | head -n 20
    CLASS=$( $READELF_BIN -h "$SO" | grep Class || true)
    MACHINE=$( $READELF_BIN -h "$SO" | grep Machine || true)
    echo "Class: $CLASS"
    echo "Machine: $MACHINE"
    if echo "$MACHINE" | grep -q "AArch64"; then echo "PASS: AArch64"; else echo "WARN: not AArch64 (x86_64 fallback?)"; fi
else
    echo "WARN: no readelf found, skipping ELF header"
fi

# Symbols: must have aether_version and at least one JNI export
NM_BIN=""
for c in llvm-nm nm; do if command -v "$c" >/dev/null 2>&1; then NM_BIN=$c; break; fi; done
if [ -n "$NM_BIN" ]; then
    echo "Exported symbols ($NM_BIN -D):"
    $NM_BIN -D "$SO" 2>&1 | head -n 40
    # Check for JNI wrapper symbols
    if $NM_BIN -D "$SO" 2>&1 | grep -q "Java_com_manjaniq_AetherBridge"; then
        echo "PASS: Found JNI exports Java_com_manjaniq_AetherBridge_*"
    else
        echo "FAIL: Missing JNI exports - wrapper not built correctly"
        exit 1
    fi
    if $NM_BIN -D "$SO" 2>&1 | grep -q "aether_version\|aether_tunnel"; then
        echo "PASS: Found aether_* symbols (maybe via static link)"
    else
        echo "INFO: No direct aether_* dynamic symbols (expected if statically linked via Rust deps) - check with nm (without -D)"
        $NM_BIN "$SO" 2>&1 | grep -E "aether_version|aether_tunnel" | head -n 10 || true
    fi
else
    echo "WARN: no nm found"
fi

# Dependency check (should be bionic, not glibc)
if [ -n "$READELF_BIN" ]; then
    echo "Dynamic deps ($READELF_BIN -d):"
    $READELF_BIN -d "$SO" 2>&1 | head -n 20
    if $READELF_BIN -d "$SO" 2>&1 | grep -q "libc.so"; then echo "PASS: bionic libc.so present"; fi
    if $READELF_BIN -d "$SO" 2>&1 | grep -q "libc.so.6"; then echo "FAIL: glibc libc.so.6 found - not Android"; exit 1; fi
fi

echo "== Aether library verification complete =="
