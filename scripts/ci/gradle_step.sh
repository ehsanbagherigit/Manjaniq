#!/usr/bin/env bash
# Run one Gradle invocation for CI, keep the full log, and on failure
# surface the relevant lines as GitHub error annotations.
#
# Usage: scripts/ci/gradle_step.sh <log-file> <gradle task/args...>
#
# The exit code is always Gradle's own exit code (never masked). The
# annotations exist because workflow logs/artifacts may not be reachable
# from every reviewing environment, while check-run annotations are.
set -uo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <log-file> <gradle args...>" >&2
  exit 64
fi

LOG="$1"; shift
mkdir -p "$(dirname "$LOG")"

echo "+ ./gradlew $*"
GRADLE_EXIT=0
./gradlew "$@" 2>&1 | tee "$LOG" || GRADLE_EXIT=$?

if [ "$GRADLE_EXIT" -eq 0 ]; then
  exit 0
fi

echo "::error title=Gradle failed (exit $GRADLE_EXIT)::./gradlew $* — full log: $LOG"

# Encode a block of lines as ONE annotation (newlines -> %0A) so the
# per-step annotation cap does not hide the actual compiler output.
annotate_block() {
  local title="$1" file="$2"
  [ -s "$file" ] || return 0
  local payload
  payload=$(cut -c1-400 "$file" | sed 's/%/%25/g; s/\r/%0D/g' | paste -sd '\n' - | sed ':a;N;$!ba;s/\n/%0A/g')
  printf '::error title=%s::%s\n' "$title" "$payload"
}

TMP=$(mktemp -d)
# Kotlin/Java compiler diagnostics
grep -E '^e: |^w: .*(unresolved|deprecated API)|error: ' "$LOG" | head -n 60 > "$TMP/compiler.txt"
split -l 12 -d "$TMP/compiler.txt" "$TMP/compiler.part." 2>/dev/null || true
for part in "$TMP"/compiler.part.*; do
  [ -e "$part" ] && annotate_block "Compiler diagnostics" "$part"
done

# Gradle's own failure summary
awk '/FAILURE: Build failed|What went wrong/{flag=1} /\* Try:/{flag=0} flag' "$LOG" | head -n 40 > "$TMP/summary.txt"
annotate_block "Gradle failure summary" "$TMP/summary.txt"

# Failing test names (JUnit console output), if any
grep -E '^[A-Za-z0-9_.$]+ > [^ ]+ FAILED$|^[A-Za-z0-9_.$]+ > [^ ]+ FAILED' "$LOG" | head -n 30 > "$TMP/tests.txt"
annotate_block "Failed tests" "$TMP/tests.txt"
grep -E -A6 '^[A-Za-z0-9_.$]+ > .* FAILED$' "$LOG" | grep -vE ' FAILED$|^--$' | head -n 40 > "$TMP/testdetail.txt"
annotate_block "Failed test details" "$TMP/testdetail.txt"

# Fallback: if nothing above matched, show the tail of the log.
if [ ! -s "$TMP/compiler.txt" ] && [ ! -s "$TMP/summary.txt" ] && [ ! -s "$TMP/tests.txt" ]; then
  tail -n 60 "$LOG" > "$TMP/tail.txt"
  annotate_block "Gradle log tail" "$TMP/tail.txt"
fi

exit "$GRADLE_EXIT"
