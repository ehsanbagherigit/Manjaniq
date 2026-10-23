#!/usr/bin/env bash
# Build the helper executables Aether spawns, for one Android ABI.
#
# Aether spawns two kinds of helper process:
#
#   * a Tor pluggable transport - `bridges.rs` starts the binary named by
#     `--tor-pt` for obfs4/snowflake/webtunnel/meek bridges. Upstream's
#     `pt-build.sh` builds lyrebird, which serves all of them from one binary;
#   * `psiphon-tunnel-core` - `psiphon.rs:749` spawns it with piped
#     stdout/stderr and `kill_on_drop(true)`, and is pointed at it by
#     `--psiphon-bin`.
#
# Both are built exactly the way upstream's own Android release job builds them
# (third_party/Aether/.github/workflows/release.yml, `android` job): as static
# `GOOS=linux CGO_ENABLED=0` ELFs. A pure-Go binary needs no libc at all, so the
# same file runs under Android and Termux - which is why the check in that job is
# `GOOS=linux`, not `GOOS=android` (android would need cgo).
#
# WHERE THEY MAY LIVE (Android 10+, API 29+, W^X)
# ------------------------------------------------
# The platform refuses to `execve` a file inside the app's writable data
# directory. The read-only native library directory is the only place a shipped
# executable may be started from, so these are packaged as `lib*.so` entries in
# `jniLibs` and the app is installed with `extractNativeLibs=true`
# (`useLegacyPackaging = true` in app/build.gradle.kts). `AetherHelpers` looks
# for them there and never in `filesDir`.
#
# USAGE
#   scripts/ci/build_helpers.sh <goarch> <dest-dir> [log-file]
#
#   goarch    arm64 | amd64   (the Go name for the ABI's CPU)
#   dest-dir  e.g. app/src/main/jniLibs/arm64-v8a
#
# FAILURE POLICY
# A helper that cannot be built (upstream unreachable, pinned commit gone) is
# reported as a GitHub `::warning::` and does not fail the job: Aether itself
# still runs, and `AetherHelpers` reports the missing helper in the UI instead of
# silently offering a route that cannot work. The final `helpers:` line lists
# exactly what this run produced, so a silently missing binary is visible in the
# log rather than inferred.
set -uo pipefail

goarch="${1:-}"
dest="${2:-}"
log="${3:-/tmp/aether_helpers.log}"

if [ -z "$goarch" ] || [ -z "$dest" ]; then
  echo "usage: $0 <goarch> <dest-dir> [log-file]" >&2
  exit 64
fi

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$dest" "$(dirname "$log")"
: > "$log"

work="$(mktemp -d)"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT INT TERM

# Build one helper from upstream's own script, then install it under the name the
# app looks for. The `lib` prefix and `.so` suffix are what makes the Android
# packager put the file in lib/<abi>/ inside the APK, which is the directory the
# platform both extracts and permits exec from.
build_one() {
  local label="$1" script="$2" produced="$3" packaged="$4"
  local out="$work/pt"

  printf '\n=== %s (%s) ===\n' "$label" "$goarch" >> "$log"
  if ! bash "$repo/third_party/Aether/$script" linux "$goarch" "$out" >> "$log" 2>&1; then
    echo "::warning title=Helper not built::$label for $goarch - upstream script $script failed, see $log"
    echo "$label=no"
    return 1
  fi
  if [ ! -f "$out/$produced" ]; then
    echo "::warning title=Helper not built::$script produced no $produced for $goarch, see $log"
    echo "$label=no"
    return 1
  fi
  if ! install -m 0755 "$out/$produced" "$dest/$packaged" >> "$log" 2>&1; then
    echo "::warning title=Helper not installed::could not copy $produced to $dest/$packaged"
    echo "$label=no"
    return 1
  fi
  # A static Go ELF must not ask for an interpreter; that is what lets it run on
  # bionic. Only checked when a reader is available, and never fatal.
  local reader=""
  for candidate in llvm-readelf readelf; do
    if command -v "$candidate" > /dev/null 2>&1; then reader="$candidate"; break; fi
  done
  if [ -n "$reader" ]; then
    if "$reader" -l "$dest/$packaged" 2>/dev/null | grep -q 'INTERP'; then
      echo "::warning title=Helper is dynamic::$packaged links an interpreter; it may not run on Android"
    else
      echo "  static ELF confirmed for $packaged"
    fi
  fi
  echo "$label=yes"
}

results=()
results+=("$(build_one lyrebird pt-build.sh lyrebird liblyrebird.so)")
results+=("$(build_one psiphon psiphon-build.sh psiphon-tunnel-core libpsiphon_tunnel_core.so)")

echo "=== helpers for $goarch ==="
ls -l "$dest" 2>/dev/null || true
echo "helpers: ${results[*]} -> $dest"
echo "helpers log: $log"
