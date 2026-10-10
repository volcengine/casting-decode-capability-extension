#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_ID="com.example.upnpdemo"
MAIN_ACTIVITY=".MainActivity"
APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$SCRIPT_DIR"

echo "[1/4] Building debug APK..."
./gradlew :app:assembleDebug

echo "[2/4] Checking adb devices..."
DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "No adb device is connected."
  exit 1
fi

echo "[3/4] Installing APK..."
adb install -r "$APK_PATH"

echo "[4/4] Restarting app..."
adb shell am force-stop "$APP_ID" || true
adb shell am start -n "$APP_ID/$MAIN_ACTIVITY"

echo "Done."
