@echo off
REM readycast Android - Build Release APK
setlocal

echo === readycast: Building Release APK ===

cd /d "%~dp0"

call fetch-deps.bat
if errorlevel 1 (
    echo [!] Cannot build without the fetched dependencies.
    exit /b 1
)

call gradlew.bat app:assembleRelease --no-daemon
if errorlevel 1 (
    echo [!] Build failed.
    exit /b 1
)

for /f "tokens=2 delims==" %%V in ('findstr /b /c:"val readycastVersionName =" "app\build.gradle.kts"') do set RAW_VERSION=%%V
set "APP_VERSION=%RAW_VERSION: =%"
set "APP_VERSION=%APP_VERSION:"=%"

set APK_NAME=com.serifpersia.readycast-%APP_VERSION%-release.apk
set APK_PATH=app\build\outputs\apk\release\%APK_NAME%

if not exist "%APK_PATH%" (
    echo.
    echo [!] Expected APK not found: %APK_PATH%
    dir /b app\build\outputs\apk\release\
    exit /b 1
)

echo.
echo [Done] APK: android-app\%APK_PATH%
echo Version: %APP_VERSION%
echo ABIs: arm64-v8a, armeabi-v7a (universal - works on any phone)
echo.
echo Install: adb install -r android-app\%APK_PATH%
echo.
