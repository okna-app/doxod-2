#!/usr/bin/env bash
# Runs on the CI emulator: installs the app, walks through all screens, pulls screenshots,
# then smoke-tests the minified release APK.
set -x
OUT=out
PKG=kg.depter.app
mkdir -p "$OUT/screens"

adb wait-for-device
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

adb shell am instrument -w -r -e class kg.depter.app.ScreenshotTour $PKG.test/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrument.txt" 2>&1
if grep -q "^OK (" "$OUT/instrument.txt"; then echo PASS > "$OUT/instrument.status"; else echo FAIL > "$OUT/instrument.status"; fi

adb exec-out run-as $PKG tar -cf - -C files screens > "$OUT/screens.tar" || true
tar -xf "$OUT/screens.tar" -C "$OUT" || true
rm -f "$OUT/screens.tar"
adb logcat -d -v time AndroidRuntime:E DepterApp:V TestRunner:V '*:S' > "$OUT/logcat-test.txt" || true

# Release smoke test: the shrunk APK must start and survive a few seconds.
adb uninstall $PKG.test || true
adb uninstall $PKG || true
adb logcat -c
REL=$(ls "$OUT"/Depter-*.apk | head -1)
adb install -r "$REL"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 8
adb exec-out screencap -p > "$OUT/screens/release_launch.png"
if adb shell pidof $PKG > /dev/null; then echo PASS > "$OUT/release.status"; else echo FAIL > "$OUT/release.status"; fi
adb logcat -d -v time AndroidRuntime:E '*:S' > "$OUT/logcat-release.txt" || true
exit 0
