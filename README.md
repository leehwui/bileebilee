# Bileebilee

A lightweight, remote-first Bilibili client for Android TV, initially targeting the Xiaomi Mi Box 3 Enhanced Edition running Android 5.1 (API 22).

## Current milestone

The first diagnostic build provides:

- Android, display, ABI, and AVC/HEVC decoder diagnostics
- D-pad focus navigation
- Public Bilibili live-room discovery
- AVC live playback with HLS preferred and FLV fallback
- Back-button return from playback to diagnostics

Authentication, recommendations, ordinary video playback, and watch history will be added after validating this build on the physical box.

## Requirements

- JDK 17
- Android SDK platform 35
- An Android TV device or emulator

The project compiles against API 35 and supports Android 5.1/API 22. Media3 is deliberately pinned to the last API-22-compatible release line.

## Build

```bash
./gradlew assembleDebug lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run on the configured emulator

```bash
$ANDROID_HOME/emulator/emulator -avd Bileebilee_TV_API_34
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/platform-tools/adb shell am start -n com.bileebilee.tv/.MainActivity
```

The emulator is ARM64 Android TV API 34 at 1920x1080 with 2 GB RAM.

## Security boundary

Do not commit Bilibili passwords, cookies, access tokens, refresh tokens, QR authorization codes, or HAR files. Authentication will use QR login and store session material only in application-private device storage.
