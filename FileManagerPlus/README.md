# File Manager +

A native Android file manager and media player for Android phones and Android TV.

## MVP 1.0 implementation status

### Implemented in this source increment
- Kotlin + Jetpack Compose + Material 3 foundation, with separate mobile and TV presentation functions.
- Light/dark theme switching.
- Storage Access Framework (SAF) folder selection and nested directory listing; persistable read/write grants are attempted.
- Create folders; search the current folder; sort by name, date, size, or type; optionally show dot-prefixed hidden entries.
- File-item actions exposed in both mobile and TV layouts: details, rename, copy to a user-selected SAF folder, move to a user-selected SAF folder, and delete with confirmation.
- Generic non-media file opening through Android's `ACTION_VIEW` intent when a compatible app is installed.
- File details show name, MIME type, provider-reported size and modification time, and URI.
- Recursive selected-folder usage summary, calculated off the main thread. It reports known file bytes and counts; it is not a device-wide free-space measurement.
- SAF repository helpers for recursive copy and move (provider-native move with copy/delete fallback).
- Platform-independent filename validation with unit-test source coverage.
- Media3 ExoPlayer URI playback foundation.
- ZIP extraction to app cache with path-traversal protection.

### Still incomplete for MVP 1.0
- In-app audio/video transport controls, seek bar, queue/playlists, subtitle/audio-track selection, and MediaSessionService background playback.
- Functional SMB server/share connection and directory browsing. `SmbRepository` remains an integration contract only; do not treat SMB as implemented.
- A true parent-folder navigation stack, operation progress/cancellation, undo, and robust multi-select.
- TV focus restoration, remote transport controls, and physical Android TV validation.
- USB OTG and SD-card validation on actual devices. Availability and write support depend on the Android device and SAF document provider.
- Automated Android/Compose integration tests and release signing.

RAR extraction, ZIP creation, cloud storage, and FTP remain outside MVP 1.0.

## Build on an 8 GB RAM computer

Recommended environment:
- Android Studio stable, or command-line Gradle
- JDK 17
- Android SDK Platform 35 and matching build-tools/platform-tools
- Gradle 8.11.1 (required by the Android Gradle Plugin version in this project)
- At least 10 GB free disk space for the SDK, Gradle caches, and build outputs

The repository intentionally uses conservative Gradle memory defaults in `gradle.properties`:
- 1.5 GB Gradle heap
- Gradle parallel execution disabled
- Maximum of two Gradle workers
- Kotlin compilation in-process

This source snapshot does not include Gradle wrapper scripts or the wrapper JAR. If Gradle 8.11.1 is installed on the machine, run from this directory:

```bash
gradle --no-daemon --max-workers=2 :app:assembleDebug
```

To create a wrapper locally once Gradle 8.11.1 is installed:

```bash
gradle wrapper --gradle-version 8.11.1
gradlew :app:assembleDebug
```

On Windows, use `gradle.bat` and then `gradlew.bat`. The included helper scripts run the same low-memory debug build (`scripts/build-debug.sh` on macOS/Linux or `scripts\build-debug.bat` on Windows). The debug APK output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For an 8 GB machine, close memory-heavy apps, build one variant at a time, and test on a physical phone/TV rather than running an Android emulator. No successful Android build is claimed for this source snapshot until it is built with the Android SDK and Gradle on a configured machine.

## Storage behavior and limitations

The app uses SAF instead of requesting broad `MANAGE_EXTERNAL_STORAGE` access. The user chooses a folder. Android and individual document providers can restrict root folders, app-private directories, removable volumes, and particular mutation operations. Providers may not support rename, delete, move, or report size/date metadata. Recursive copy and usage scans can take time on large or remote folders; copy/move currently do not yet expose progress or cancellation.

The usage summary is computed recursively for the selected SAF folder. It sums only sizes reported by the provider and separately counts files whose sizes are unavailable. It cannot report total device free space through the SAF tree alone.

## Privacy and product principles

- Free, no ads, no tracking/analytics, and no account registration.
- Local file management remains offline-first.
- No broad all-files permission is requested in this implementation.
- SMB credentials must not be logged or stored in plaintext when SMB is implemented.

## Next development priorities

1. Add operation progress/cancellation and improve parent-folder navigation.
2. Finish Media3 in-app controls and background playback with a media session service.
3. Implement actual SMB connection and share browsing.
4. Improve TV D-pad focus behavior and test remote controls.
5. Run Android build/tests and validate USB/SD storage on physical phone and TV hardware.
