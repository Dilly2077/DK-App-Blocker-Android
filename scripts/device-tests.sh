#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results
trap 'adb logcat -d > device-results/logcat.txt || true' EXIT
apk=$(find "$1" -name app-debug.apk -print -quit)
tests=$(find "$1" -name app-debug-androidTest.apk -print -quit)
adb install -r "$apk" | tee device-results/install.txt
grep -q Success device-results/install.txt
adb install -r "$tests"
if [[ "$2" == gestural ]]; then
  adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  adb shell cmd overlay list | tee device-results/navigation.txt
  grep -q '\[x\] com.android.internal.systemui.navbar.gestural' device-results/navigation.txt
fi
adb shell am instrument -w -r com.dk.appblocker.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/instrumentation.txt
grep -q 'OK (' device-results/instrumentation.txt
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' device-results/instrumentation.txt; then exit 1; fi
adb pull /sdcard/Android/data/com.dk.appblocker/files device-results/screenshots
adb shell am start -W -n com.dk.appblocker/.MainActivity | tee device-results/launch.txt
grep -q 'Status: ok' device-results/launch.txt
