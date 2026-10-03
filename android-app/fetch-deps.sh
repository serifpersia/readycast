#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

REF=2f7ebbc7eb909321ff41b2c6bcdf6b0881427579
BASE="https://raw.githubusercontent.com/ConnectSDK/Connect-SDK-Android-Core/$REF"

echo "=== readycast: fetching binary dependencies ==="

mkdir -p connectsdk/libs connectsdk/jniLibs

i=0
total=7

if [ ! -f connectsdk/libs/lgcast-android-lib.jar ]; then
  i=$((i + 1))
  echo "[$i/$total] lgcast-android-lib.jar"
  curl -fsSL -o connectsdk/libs/lgcast-android-lib.jar "$BASE/libs/lgcast-android-lib.jar" || {
    echo ""
    echo "[!] Download failed. These binaries are not redistributable, so they are fetched"
    echo "    at build time from the Connect SDK repository (commit $REF)."
    echo "    Get them there, or from a Connect SDK checkout, and place:"
    echo "      connectsdk/libs/lgcast-android-lib.jar"
    echo "      connectsdk/jniLibs/arm64-v8a/   (libc++_shared.so, libgstreamer_android.so, libgstreamer-appcast.so)"
    echo "      connectsdk/jniLibs/armeabi-v7a/  (same three)"
    exit 1
  }
fi

for abi in arm64-v8a armeabi-v7a; do
  mkdir -p "connectsdk/jniLibs/$abi"
  for lib in libc++_shared.so libgstreamer_android.so libgstreamer-appcast.so; do
    if [ ! -f "connectsdk/jniLibs/$abi/$lib" ]; then
      i=$((i + 1))
      echo "[$i/$total] $abi/$lib"
      curl -fsSL -o "connectsdk/jniLibs/$abi/$lib" "$BASE/jniLibs/$abi/$lib" || exit 1
    fi
  done
done

echo "=== dependencies ready ==="
