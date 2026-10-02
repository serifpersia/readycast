@echo off
setlocal
cd /d "%~dp0"

set REF=2f7ebbc7eb909321ff41b2c6bcdf6b0881427579
set BASE=https://raw.githubusercontent.com/ConnectSDK/Connect-SDK-Android-Core/%REF%

echo === readycast: fetching binary dependencies ===

if not exist "connectsdk\libs\lgcast-android-lib.jar" (
    echo [1/7] lgcast-android-lib.jar
    curl -fsSL -o "connectsdk\libs\lgcast-android-lib.jar" "%BASE%/libs/lgcast-android-lib.jar"
    if errorlevel 1 goto failed
)

for %%A in (arm64-v8a armeabi-v7a) do call :fetch_native %%A

echo === dependencies ready ===
exit /b 0

:fetch_native
set ABI=%1
if exist "connectsdk\jniLibs\%ABI%\libgstreamer_android.so" exit /b 0
echo [n] %ABI% natives
if not exist "connectsdk\jniLibs\%ABI%" mkdir "connectsdk\jniLibs\%ABI%"
curl -fsSL -o "connectsdk\jniLibs\%ABI%\libc++_shared.so" "%BASE%/jniLibs/%ABI%/libc++_shared.so"
if errorlevel 1 goto failed
curl -fsSL -o "connectsdk\jniLibs\%ABI%\libgstreamer_android.so" "%BASE%/jniLibs/%ABI%/libgstreamer_android.so"
if errorlevel 1 goto failed
curl -fsSL -o "connectsdk\jniLibs\%ABI%\libgstreamer-appcast.so" "%BASE%/jniLibs/%ABI%/libgstreamer-appcast.so"
if errorlevel 1 goto failed
exit /b 0

:failed
echo.
echo [!] Download failed. These binaries are not redistributable, so they are fetched at
echo     build time from the Connect SDK repository (commit %REF%).
echo     Get them there, or from a Connect SDK checkout, and place:
echo       connectsdk\libs\lgcast-android-lib.jar
echo       connectsdk\jniLibs\arm64-v8a\   (libc++_shared.so, libgstreamer_android.so, libgstreamer-appcast.so)
echo       connectsdk\jniLibs\armeabi-v7a\  (same three)
exit /b 1
