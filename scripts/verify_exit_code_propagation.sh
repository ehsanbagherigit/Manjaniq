#!/usr/bin/env bash
# Regression check for the CI shell-portability defect
# ("/usr/bin/sh: 1: Bad substitution", exit code 2).
#
# Verifies, in a controlled way, that the exit-code
# capture pattern used by the workflow:
#   * propagates a failing command's exit code,
#   * propagates a failing command's exit code when
#     the output is captured to a log file first,
#   * never reports success for a failed stage,
#   * works under BOTH bash and POSIX sh (dash),
#     so no step depends on bash-only constructs like
#     PIPESTATUS inside an sh-executed script block.
#
# Usage: scripts/verify_exit_code_propagation.sh
# Exit code: 0 when all checks pass, 1 otherwise.

set -u

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

failures=0

check() {
    # check <description> <expected-exit> <actual-exit>
    if [ "$2" = "$3" ]; then
        echo "OK: $1 (exit $3)"
    else
        echo "FAIL: $1 — expected exit $2, got $3" >&2
        failures=$((failures + 1))
    fi
}

# A deliberately failing command and a succeeding one.
printf '#!/bin/sh\nexit 7\n' > "$WORKDIR/fail.sh"
printf '#!/bin/sh\nexit 0\n' > "$WORKDIR/ok.sh"
chmod +x "$WORKDIR/fail.sh" "$WORKDIR/ok.sh"

for shell in bash sh; do
    command -v "$shell" >/dev/null 2>&1 || continue

    # 1. Plain failure propagation.
    "$shell" -c '"$1"' _ "$WORKDIR/fail.sh" >/dev/null 2>&1
    check "$shell: plain failing command propagates exit code" 7 $?

    # 2. The workflow pattern: capture output to a log file,
    #    then test the saved status. (This replaces the
    #    non-portable `cmd | tee log; EXIT=${PIPESTATUS[0]}`
    #    construct that broke under /usr/bin/sh.)
    "$shell" -c '"$1" > "$2" 2>&1; code=$?; echo "code=$code"; test $code -ne 0' \
        _ "$WORKDIR/fail.sh" "$WORKDIR/one.log" >/dev/null 2>&1
    check "$shell: redirect+status pattern detects failure" 0 $?

    # 3. The same pattern must NOT report failure for a
    #    succeeding command.
    "$shell" -c '"$1" > "$2" 2>&1; code=$?; test $code -eq 0' \
        _ "$WORKDIR/ok.sh" "$WORKDIR/two.log" >/dev/null 2>&1
    check "$shell: redirect+status pattern keeps success" 0 $?

    # 4. A pipeline whose last command succeeds must not
    #    mask a failing first command when the pattern is
    #    used (the tee-masking hazard).
    "$shell" -c '"$1" > "$2" 2>&1; code=$?; cat "$2" >/dev/null; test $code -ne 0' \
        _ "$WORKDIR/fail.sh" "$WORKDIR/three.log" >/dev/null 2>&1
    check "$shell: log-then-tail pattern keeps failure visible" 0 $?
done

if [ "$failures" -ne 0 ]; then
    echo "EXIT-CODE PROPAGATION CHECK FAILED ($failures failures)" >&2
    exit 1
fi
echo "Exit-code propagation verified under bash and sh."
exit 0
