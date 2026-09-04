# Bileebilee

A lightweight, remote-first Bilibili client for Android TV, initially targeting the Xiaomi Mi Box 3 Enhanced Edition running Android 5.1 (API 22).

## Current milestone (0.4.0)

The current build provides:

- Android, display, ABI, and AVC/HEVC decoder diagnostics
- D-pad focus navigation
- Bilibili QR-code login with private on-device session storage
- Public Bilibili live-room discovery
- AVC live playback with HLS preferred and FLV fallback
- Personalized recommendations from Bilibili's mobile feed
- Persistent mobile feed identity, app-style request context, and cursor-based refreshes
- A four-column TV video grid with covers, metadata, and remote focus states
- Progressive video playback for recommended videos
- Account-synced, cursor-paginated watch history
- Stored progress display and resume playback for history videos
- Back-button return from playback to the originating screen and card

These features have been tested on a Xiaomi Mi Box 3 Enhanced Edition running Android 5.1. History is currently read-only; playback heartbeat reporting is not implemented yet.

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

Do not commit Bilibili passwords, cookies, access tokens, refresh tokens, QR authorization codes, or HAR files. QR login stores session material only in application-private device storage.
