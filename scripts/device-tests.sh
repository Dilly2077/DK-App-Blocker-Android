#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results
trap 'adb logcat -d > device-results/logcat.txt || true' EXIT
apk=$(find "$1" -name app-debug.apk -print -quit)
tests=$(find "$1" -name app-debug-androidTest.apk -print -quit)
adb install -r "$apk" | tee device-results/install.txt
grep -q Success device-results/install.txt
adb install -r -t "$tests"
if [[ "$2" == gestural ]]; then
  adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural
  adb shell cmd overlay list | tee device-results/navigation.txt
  grep -q '\[x\] com.android.internal.systemui.navbar.gestural' device-results/navigation.txt
fi
adb shell am instrument -w -r -e notClass com.dk.appblocker.PersistenceDeviceTest com.dk.appblocker.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/instrumentation.txt
grep -q 'OK (' device-results/instrumentation.txt
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' device-results/instrumentation.txt; then exit 1; fi
adb pull /sdcard/Android/data/com.dk.appblocker/files device-results/screenshots
for phase in prepare process; do
  adb shell am instrument -w -r -e class com.dk.appblocker.PersistenceDeviceTest -e phase "$phase" com.dk.appblocker.test/androidx.test.runner.AndroidJUnitRunner | tee "device-results/persistence-$phase.txt"
  grep -q 'OK (1 test)' "device-results/persistence-$phase.txt"
done
adb reboot
adb wait-for-device
for attempt in $(seq 1 120); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; then break; fi
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb shell input keyevent 82
adb shell am instrument -w -r -e class com.dk.appblocker.PersistenceDeviceTest -e phase reboot com.dk.appblocker.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/persistence-reboot.txt
grep -q 'OK (1 test)' device-results/persistence-reboot.txt
adb shell am start -W -n com.dk.appblocker/.MainActivity | tee device-results/launch.txt
grep -q 'Status: ok' device-results/launch.txt
