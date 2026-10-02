#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "=== ReadyCast: Building Debug APK ==="

./fetch-deps.sh

./gradlew app:assembleDebug --no-daemon

APP_VERSION=$(grep -oP 'val readycastVersionName = "\K[^"]+' app/build.gradle.kts)
APK_NAME="com.serifpersia.readycast-${APP_VERSION}-debug.apk"
APK_PATH="app/build/outputs/apk/debug/${APK_NAME}"

if [ ! -f "$APK_PATH" ]; then
    echo ""
    echo "[!] Expected APK not found: $APK_PATH"
    ls app/build/outputs/apk/debug/ || true
    exit 1
fi

echo ""
echo "[Done] APK: android-app/${APK_PATH}"
echo "Install: adb install -r android-app/${APK_PATH}"
