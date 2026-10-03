<p align="center">
  <img src="docs/logo-readme.png" alt="readycast" width="380">
</p>

<h1 align="center">readycast</h1>

Cast a phone's **secondary display** (Motorola Ready For, Samsung DeX, or any
HDMI desktop) to an LG webOS TV. The phone stays free to use while the desktop
is on the big screen. The phone screen itself can also be mirrored, with no
root needed.

## Install

Grab the APK from the [releases page](../../releases) and install it on your
phone. There is no Play Store version.

1. Launch **readycast** and pick a source: **Main display** (phone screen) or
   **External display** (desktop on your second display).
2. The external display needs a second display to exist (a real monitor, a dummy
   HDMI plug, or desktop mode) plus root or Shizuku.
3. Tap **Start mirroring**, accept the screen-capture prompt on the phone and the
   pairing prompt on the TV.

## Building

Requires the Android SDK, a JDK, and network access on the first build.

```bash
git clone <this repo> readycast
cd readycast/android-app
./build-debug.sh          # or build-debug.bat on Windows
adb install -r app/build/outputs/apk/debug/com.serifpersia.readycast-*-debug.apk
```

For a signed release APK, generate a keystore once (never committed) and run
`./build-release.sh` (or `.bat`):

```bash
keytool -genkeypair -v -keystore readycast-release.keystore -alias readycast \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass readycast -keypass readycast
```

The build fetches LG's `lgcast` jar and GStreamer natives at build time (they
cannot be redistributed). Everything else, including the `caster` capture
helper, is built from source in this repository.

## Credits

- **[Connect SDK](https://github.com/ConnectSDK/Connect-SDK-Android-Core)**
  (Apache-2.0), vendored under `android-app/connectsdk/`. Our fork injects
  pre-encoded frames in place of its `MediaProjection` capture.
- **[Shizuku](https://github.com/RikkaApps/Shizuku)** (Apache-2.0). Optional;
  gives non-rooted devices the shell privileges the capture helper needs.

## License

See [LICENSE](LICENSE). The binaries fetched at build time remain the property
of their respective owners.
