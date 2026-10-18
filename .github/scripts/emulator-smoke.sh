#!/usr/bin/env bash
set -euxo pipefail
mkdir -p diagnostics
exec > >(tee diagnostics/launch.txt) 2>&1
trap 'adb logcat -d > diagnostics/test-logcat.txt 2>&1 || true' EXIT
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell getprop ro.product.cpu.abi
sha256sum tested-apk/*.apk | tee diagnostics/apk-sha256.txt
adb install -r tested-apk/*.apk
adb install -r app/build/outputs/apk/androidTest/debug/*-androidTest.apk
adb shell am force-stop com.manjaniq
set +e
adb shell am start -W -n com.manjaniq/.MainActivity | tee diagnostics/activity-start.txt
START_EXIT=${PIPESTATUS[0]}
sleep 5
LAUNCH_EXIT=0
adb shell pidof com.manjaniq || LAUNCH_EXIT=$?
adb shell am instrument -w -r -e class com.manjaniq.LaunchSmokeTest \
  com.manjaniq.test/androidx.test.runner.AndroidJUnitRunner | tee diagnostics/instrumentation.txt
INSTRUMENT_EXIT=${PIPESTATUS[0]}
set -e
test "$START_EXIT" -eq 0
test "$INSTRUMENT_EXIT" -eq 0
grep -q 'Status: ok' diagnostics/activity-start.txt
grep -q 'OK (2 tests)' diagnostics/instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' diagnostics/instrumentation.txt
test "$LAUNCH_EXIT" -eq 0
printf 'Verified commit: %s\n' "$GITHUB_SHA" | tee diagnostics/commit.txt
