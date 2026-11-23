#!/usr/bin/env bash
# Installs each APK on the running emulator, launches it, and saves what happened.
set +e
OUT=debug-logs
mkdir -p "$OUT"

run() {
  name="$1"
  apk="$2"
  adb uninstall com.purify.app >/dev/null 2>&1
  adb install -r "$apk" > "$OUT/$name-install.txt" 2>&1
  adb logcat -c
  adb shell am start -W -n com.purify.app/.MainActivity > "$OUT/$name-start.txt" 2>&1
  sleep 12
  adb shell pidof com.purify.app > "$OUT/$name-pid.txt" 2>&1
  adb logcat -d > "$OUT/$name-logcat-full.txt" 2>&1
  grep -B2 -A45 "FATAL EXCEPTION" "$OUT/$name-logcat-full.txt" > "$OUT/$name-crash.txt"
  grep -i -E "purify|AndroidRuntime|media3|ExoPlayer" "$OUT/$name-logcat-full.txt" | tail -150 > "$OUT/$name-logcat-purify.txt"
  rm -f "$OUT/$name-logcat-full.txt"
  adb exec-out screencap -p > "$OUT/$name-screen.png"
}

run release app/build/outputs/apk/release/app-release.apk
run debug app/build/outputs/apk/debug/app-debug.apk
