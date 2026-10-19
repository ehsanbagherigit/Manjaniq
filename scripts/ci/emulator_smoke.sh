#!/usr/bin/env bash
# Emulator release gate for Manjaniq.
#
# WHY THIS IS A FILE: reactivecircus/android-emulator-runner splits its
# `script:` input into lines and runs EACH LINE in a separate `sh -c`
# (src/script-parser.ts + main.ts). Multi-line `if`/`while` blocks become
# syntax errors ("exit code 2"), and `$?` / shell variables never survive
# to the next line. That is the historical "/usr/bin/sh failed with exit
# code 2" failure. The workflow therefore invokes this script with ONE
# line (`bash scripts/ci/emulator_smoke.sh`) and all logic lives here,
# under bash with strict mode.
#
# Phases
#   1. Install the exact APK artifact produced by the build job for this
#      commit, verify package/version, launch the real launcher activity,
#      wait (bounded polling, no fixed sleeps) for RESUMED, confirm the
#      process stays alive and no fatal exception was logged.
#   2. Run the on-device instrumentation suite (real UI: LaunchSmokeTest,
#      NavigationTest, LocaleRtlTest, EngineLifecycleTest) and require the
#      JUnit XML to show every test passed.
#
# Diagnostic commands that are allowed to fail are handled explicitly
# (their failure is reported, never silently ignored) and never replace
# the gate's own exit status.
set -euo pipefail

PKG="com.manjaniq"
ACTIVITY="$PKG/.MainActivity"
WS="${GITHUB_WORKSPACE:-$(pwd)}"
OUT="$WS/emulator-evidence"
APK="${TESTED_APK:-$WS/tested-apk/app-debug.apk}"
RESUME_TIMEOUT_S="${RESUME_TIMEOUT_S:-60}"
STABILITY_WINDOW_S="${STABILITY_WINDOW_S:-8}"

mkdir -p "$OUT"
cd "$WS"

log()  { printf '\n=== %s ===\n' "$*"; }
fail() { printf '::error title=Emulator gate::%s\n' "$*"; exit 1; }
# Record a diagnostic step that may legitimately fail without masking it.
diag() {
  local label="$1"; shift
  if "$@"; then
    return 0
  else
    local rc=$?
    printf '::warning title=Diagnostic failed::%s (exit %s) - continuing, gate result is unaffected\n' "$label" "$rc"
    return 0
  fi
}

# ---------------------------------------------------------------- commit
HEAD_SHA="$(git rev-parse HEAD)"
log "Commit under test"
echo "HEAD_SHA=$HEAD_SHA"
echo "GITHUB_SHA=${GITHUB_SHA:-<unset>}"
if [ -n "${GITHUB_SHA:-}" ] && [ "$GITHUB_SHA" != "$HEAD_SHA" ]; then
  fail "checkout ($HEAD_SHA) does not match GITHUB_SHA ($GITHUB_SHA)"
fi
echo "$HEAD_SHA" > "$OUT/commit-sha.txt"

# ---------------------------------------------------------------- device
log "Device"
adb wait-for-device
adb devices -l
SDK_LEVEL="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "SDK=$SDK_LEVEL ABI=$ABI"
{ echo "sdk=$SDK_LEVEL"; echo "abi=$ABI"; } > "$OUT/device.txt"
BOOTED="$(adb shell getprop sys.boot_completed | tr -d '\r')"
[ "$BOOTED" = "1" ] || fail "emulator did not report sys.boot_completed=1 (got '$BOOTED')"
diag "clear logcat" adb logcat -c

# ================================================================ PHASE 1
log "Phase 1: install and launch the exact build artifact"
[ -f "$APK" ] || fail "APK artifact not found at $APK"
sha256sum "$APK" | tee "$OUT/apk-sha256.txt"
if ! unzip -l "$APK" | grep -q "lib/$ABI/libmanjaniq_native.so"; then
  fail "APK does not contain lib/$ABI/libmanjaniq_native.so for this emulator ABI"
fi
unzip -l "$APK" | grep -E 'mipmap.*ic_launcher|drawable/ic_manjaniq_logo' > "$OUT/apk-brand-entries.txt" || true
[ -s "$OUT/apk-brand-entries.txt" ] || fail "APK is missing launcher/brand resources"
cat "$OUT/apk-brand-entries.txt"

if adb shell pm list packages | tr -d '\r' | grep -qx "package:$PKG"; then
  echo "Removing pre-existing $PKG"
  adb uninstall "$PKG"
fi
adb install -r -t "$APK"

INSTALLED_PATH="$(adb shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -n1)"
[ -n "$INSTALLED_PATH" ] || fail "$PKG is not installed after adb install"
adb pull "$INSTALLED_PATH" "$OUT/installed.apk" >/dev/null
INSTALLED_SHA="$(sha256sum "$OUT/installed.apk" | cut -d' ' -f1)"
ARTIFACT_SHA="$(cut -d' ' -f1 < "$OUT/apk-sha256.txt")"
rm -f "$OUT/installed.apk"
echo "installed sha256=$INSTALLED_SHA"
[ "$INSTALLED_SHA" = "$ARTIFACT_SHA" ] || fail "installed APK ($INSTALLED_SHA) differs from artifact ($ARTIFACT_SHA)"

adb shell dumpsys package "$PKG" | tr -d '\r' | grep -E 'versionCode|versionName' | head -n 2 | tee "$OUT/installed-version.txt"
grep -q 'versionName=0.1.0' "$OUT/installed-version.txt" || fail "installed versionName is not 0.1.0"

LAUNCHER="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PKG" | tr -d '\r' | tail -n1)"
echo "launcher resolves to: $LAUNCHER"
[ "$LAUNCHER" = "$ACTIVITY" ] || fail "launcher intent resolves to '$LAUNCHER', expected $ACTIVITY"

adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$ACTIVITY" | tr -d '\r' | tee "$OUT/am-start.txt"
grep -q 'Status: ok' "$OUT/am-start.txt" || fail "am start did not report Status: ok"

is_resumed() {
  adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|ResumedActivity' | grep -q "$ACTIVITY"
}
log "Waiting (<= ${RESUME_TIMEOUT_S}s) for $ACTIVITY to be RESUMED"
deadline=$(( $(date +%s) + RESUME_TIMEOUT_S ))
until is_resumed; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    diag "dump activities" sh -c "adb shell dumpsys activity activities | tr -d '\r' | grep -E 'Resumed|com.manjaniq' | head -n 40"
    diag "dump logcat" sh -c "adb logcat -d > '$OUT/logcat-phase1.txt'"
    fail "$ACTIVITY did not reach RESUMED within ${RESUME_TIMEOUT_S}s"
  fi
  sleep 1
done
PID="$(adb shell pidof "$PKG" | tr -d '\r')"
[ -n "$PID" ] || fail "RESUMED reported but no process for $PKG"
echo "RESUMED with pid $PID"

# Stability window: the process must keep the same pid and stay resumed
# while the UI performs its first engine polls (1 s cadence).
log "Stability window ${STABILITY_WINDOW_S}s"
for _ in $(seq 1 "$STABILITY_WINDOW_S"); do
  sleep 1
  NOW_PID="$(adb shell pidof "$PKG" | tr -d '\r')"
  [ "$NOW_PID" = "$PID" ] || fail "process changed/died during stability window (was $PID, now '${NOW_PID:-none}')"
  is_resumed || fail "activity left RESUMED during stability window"
done
adb logcat -d > "$OUT/logcat-phase1.txt"
if grep -E "FATAL EXCEPTION|Process $PKG .*has died|Force finishing activity $PKG" "$OUT/logcat-phase1.txt" | grep -q "$PKG"; then
  grep -E -A 25 "FATAL EXCEPTION" "$OUT/logcat-phase1.txt" | head -n 80 || true
  fail "fatal exception or process death logged during manual launch"
fi
# Evidence that the real app composed: EngineRepository polling is wired
# from ManjaniqApp and logs a native-bridge line only if the library is
# missing. Native must have loaded, so that line must be absent.
if grep -q "Failed to load manjaniq_native" "$OUT/logcat-phase1.txt"; then
  fail "native library failed to load in the installed artifact"
fi
echo "Phase 1 PASSED: artifact $ARTIFACT_SHA launched, RESUMED, pid $PID stable for ${STABILITY_WINDOW_S}s"
echo "phase1=PASS" >> "$OUT/summary.txt"

adb shell am force-stop "$PKG"
adb uninstall "$PKG"   # the test build is signed by this runner's debug key

# ================================================================ PHASE 2
log "Phase 2: instrumentation suite against the real UI"
chmod +x gradlew
diag "clear logcat" adb logcat -c
GRADLE_EXIT=0
./gradlew connectedDebugAndroidTest --stacktrace > "$OUT/connected.log" 2>&1 || GRADLE_EXIT=$?
echo "gradle connectedDebugAndroidTest exit=$GRADLE_EXIT"
tail -n 60 "$OUT/connected.log"
adb logcat -d > "$OUT/logcat-phase2.txt" || echo "::warning::could not capture phase-2 logcat"

RESULTS_DIR="app/build/outputs/androidTest-results/connected"
mkdir -p "$OUT/androidTest-results"
if [ -d "$RESULTS_DIR" ]; then
  find "$RESULTS_DIR" -name 'TEST-*.xml' -exec cp {} "$OUT/androidTest-results/" \;
fi

log "Instrumentation results"
PY_EXIT=0
python3 - "$OUT/androidTest-results" "$GRADLE_EXIT" <<'PY' || PY_EXIT=$?
import glob, os, sys
import xml.etree.ElementTree as ET
d, gradle_exit = sys.argv[1], int(sys.argv[2])
files = sorted(glob.glob(os.path.join(d, 'TEST-*.xml')))
required = {
    'com.manjaniq.LaunchSmokeTest': 'launcherActivityReachesResumedAndRendersRealHome',
    'com.manjaniq.NavigationTest': 'drawerReachesSettingsLogsAboutAndBackReturnsHome',
    'com.manjaniq.LocaleRtlTest': 'persianHomeIsRtlWithPersianStrings',
    'com.manjaniq.EngineLifecycleTest': 'startBindsRealListenerAndStopReleasesIt',
}
seen = {}
total = failed = 0
problems = []
if not files:
    problems.append('no TEST-*.xml produced (gradle exit %d)' % gradle_exit)
for f in files:
    root = ET.parse(f).getroot()
    suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
    for s in suites:
        for tc in s.findall('testcase'):
            total += 1
            name, cls = tc.get('name'), tc.get('classname')
            bad = tc.find('failure') is not None or tc.find('error') is not None
            skipped = tc.find('skipped') is not None
            status = 'FAIL' if bad else ('SKIP' if skipped else 'PASS')
            if bad:
                failed += 1
                msg = (tc.find('failure') if tc.find('failure') is not None else tc.find('error'))
                problems.append('%s.%s: %s' % (cls, name, (msg.get('message') or (msg.text or '').strip().splitlines()[0] if msg is not None else '')[:300]))
            seen.setdefault(cls, {})[name] = status
            print('  %-4s %s.%s (%ss)' % (status, cls, name, tc.get('time')))
for cls, method in required.items():
    if seen.get(cls, {}).get(method) != 'PASS':
        problems.append('required test did not pass: %s.%s (status=%s)' % (cls, method, seen.get(cls, {}).get(method)))
print('\ntotal=%d failed=%d gradle_exit=%d' % (total, failed, gradle_exit))
with open(os.path.join(d, '..', 'summary.txt'), 'a') as fh:
    fh.write('phase2_total=%d\nphase2_failed=%d\nphase2_gradle_exit=%d\n' % (total, failed, gradle_exit))
for p in problems:
    print('::error title=Instrumentation::%s' % p)
if problems or gradle_exit != 0 or total == 0:
    sys.exit(1)
PY
if [ "$PY_EXIT" -ne 0 ]; then
  log "Failure context from connected.log"
  grep -n -A 30 "What went wrong" "$OUT/connected.log" | head -n 120 || true
  grep -n -E "FAILED|AssertionError|Exception" "$OUT/connected.log" | head -n 60 || true
  grep -E "FATAL EXCEPTION|AndroidRuntime|TestRunner" "$OUT/logcat-phase2.txt" | tail -n 80 || true
  fail "instrumentation gate failed (gradle exit $GRADLE_EXIT)"
fi

echo "phase2=PASS" >> "$OUT/summary.txt"
log "EMULATOR GATE PASSED for commit $HEAD_SHA"
exit 0
