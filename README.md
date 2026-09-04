# Bileebilee

A lightweight, remote-first Bilibili client for Android TV, initially targeting the Xiaomi Mi Box 3 Enhanced Edition running Android 5.1 (API 22).

## Current milestone (0.9.0)

The current build provides:

- A recommendation-first landing page with persistent TV navigation tabs
- Compact, focus-activated navigation with no extra confirmation click
- Focus and loaded-content preservation while switching sections
- Two complete content rows with row-aligned TV scrolling
- Consistent TV safe-area padding and comfortably spaced content cards
- D-pad focus navigation
- Bilibili QR-code login with private on-device session storage
- A signed-in account view with nickname and UID
- Paginated browsing of followed creators from the Account page
- Recent-video browsing and playback for each followed creator
- Focus-preserving Back navigation from playback to creator videos, followed creators, and Account
- Remote-friendly, paginated browsing of popular Bilibili live rooms
- Account-aware browsing of currently live followed streamers
- D-pad switching between Following and Popular live views
- Live-room covers, streamer names, categories, and popularity
- Return from playback to the previously selected live room
- AVC live playback with HLS preferred and FLV fallback
- Personalized recommendations from Bilibili's mobile feed
- Persistent mobile feed identity, app-style request context, and cursor-based refreshes
- A four-column TV video grid with covers, metadata, and remote focus states
- Progressive video playback for recommended videos
- Account-synced, cursor-paginated watch history
- Stored progress display and resume playback for history videos
- Periodic and final playback heartbeats that update Bilibili history
- Back-button return from playback to the originating screen and card

These features have been tested on a Xiaomi Mi Box 3 Enhanced Edition running Android 5.1.

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
