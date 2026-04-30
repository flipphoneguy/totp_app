# Authenticator

A lightweight, offline TOTP (RFC 6238) generator for Android. Built to compile in Termux with no Gradle, no AndroidX, and no Google Play dependencies. The whole APK is around 320 KB.

## Features

- **Add accounts** by typing a Base32 seed, scanning a QR with the camera, or picking an image of a QR from your gallery.
- **Live codes** on the main screen with a large monospaced display and a per-row countdown of seconds remaining.
- **Always encrypted on disk** with AES-GCM-256. By default the key is held in the Android Keystore (hardware-backed where the device supports it). Turn on the optional password lock and the key is instead derived from your password via PBKDF2-HMAC-SHA256 (100k iterations) and the app re-prompts whenever you return to it from another app.
- **Backups.** Export plaintext JSON or AES-GCM encrypted backups; import either format.
- **Update checker.** Looks at the latest GitHub release of [flipphoneguy/totp_app](https://github.com/flipphoneguy/totp_app), downloads the APK if newer, and launches the system installer.
- **`otpauth://` deep links.** Any QR scanner can hand off scan results straight into the app.
- **Full D-pad / keyboard navigation.** Every interactive element is focusable with explicit focus order, so the app works on devices without a touchscreen.

## Install

Grab the latest APK from [Releases](https://github.com/flipphoneguy/totp_app/releases) and sideload it, or:

```sh
adb install -r TotpApp.apk
```

`min-SDK 23` (Android 6.0) through `target 35`. Android Keystore-backed encryption is the at-rest default and requires API 23. Older Android (≤ 9) is asked once for storage permission so the update download can land in app-external storage.

## Build (Termux)

The repo carries one shell script that drives the whole pipeline using packages available in Termux: `aapt2`, `ecj`, `d8`, `apksigner`, `zip`, plus `curl` (or `wget`) to fetch the QR library on first build.

```sh
pkg install aapt2 ecj d8 apksigner zip openjdk-17 curl
mkdir -p ~/.android
cp /system/framework/framework-res.apk ~/.android/        # one time
# Place an android.jar (API 35) at ~/.android/android.jar
# Place a debug.keystore at ~/.android/debug.keystore

./build.sh
```

The script:
1. Reads `VERSION` and rewrites `android:versionCode` / `android:versionName` in `AndroidManifest.xml`.
2. Downloads `zxing-core` to `libs/` if it isn't there yet (used for QR decoding).
3. Compiles resources with `aapt2`, Java with `ecj`, dexes with `d8`, packages and signs with `apksigner`.
4. Drops `TotpApp.apk` next to the script.

Bumping the app version is just `echo 1.2.3 > VERSION && ./build.sh`.

## Source Layout

```
AndroidManifest.xml
VERSION                         # single-line semver, drives manifest versions
build.sh                        # the entire build pipeline
libs/                           # zxing-core jar (downloaded on demand)
res/
  drawable/                     # buttons, cards, ring, icon
  layout/                       # one XML per screen + list item
  values/                       # strings, colors, themes
  mipmap-anydpi*/               # adaptive launcher icon
src/com/flipphoneguy/totp/
  App.java                      # Application class — tracks foreground state for re-lock
  MainActivity.java             # account list + tick loop
  AddActivity.java              # manual / camera / picture entry flow
  SettingsActivity.java         # toggles, backups, update check
  PasswordActivity.java         # set + unlock screen
  InfoActivity.java             # about / GitHub links
  EntryAdapter.java             # list row binding
  EntryStore.java               # always-encrypted disk read/write
  CryptoUtil.java               # AES-GCM (Keystore + PBKDF2 modes)
  TotpGenerator.java            # HMAC-SHA1 TOTP
  Base32.java                   # tiny RFC 4648 base32 decoder
  OtpAuthUri.java               # otpauth:// URI parser
  QrDecoder.java                # bitmap → text via zxing-core
  UpdateChecker.java            # GitHub releases poll + download + install
  SharedFileProvider.java       # minimal FileProvider replacement (for APK install)
  JsonCodec.java, TotpEntry.java
```

## License & credits

- Code: see [LICENSE](LICENSE).
- QR decoding: [ZXing core](https://github.com/zxing/zxing) (Apache 2.0), fetched at build time.
- Author: [flipphoneguy](https://github.com/flipphoneguy).
