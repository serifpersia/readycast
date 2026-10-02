#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "=== readycast: Building Release APK ==="

./fetch-deps.sh

./gradlew app:assembleRelease --no-daemon

APP_VERSION=$(grep -oP 'val readycastVersionName = "\K[^"]+' app/build.gradle.kts)
APK_NAME="com.serifpersia.readycast-${APP_VERSION}-release.apk"
APK_PATH="app/build/outputs/apk/release/${APK_NAME}"

if [ ! -f "$APK_PATH" ]; then
    echo ""
    echo "[!] Expected APK not found: $APK_PATH"
    ls app/build/outputs/apk/release/ || true
    exit 1
fi

echo ""
echo "[Done] APK: android-app/${APK_PATH}"
echo "Version: ${APP_VERSION}"
echo "ABIs: arm64-v8a, armeabi-v7a (universal - works on any phone)"
echo ""
echo "Install: adb install -r android-app/${APK_PATH}"
echo ""
