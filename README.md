# File Manager +

A native Android file manager and media player for Android phones and Android TV.

## MVP 1.0 implementation status

### Implemented in this source increment
- Kotlin + Jetpack Compose + Material 3 foundation, with separate mobile and TV presentation functions.
- Light/dark theme switching.
- **Direct file access via `MANAGE_EXTERNAL_STORAGE`** — browse all of internal and external storage without a per-folder SAF picker.
- Storage roots (internal storage, SD card) shown on first launch; navigate freely through directories including `DCIM`, `Download`, `Android/`, etc.
- Create folders; search the current folder; sort by name, date, size, or type; optionally show dot-prefixed hidden entries.
- File-item actions exposed in both mobile and TV layouts: details, rename, copy to a folder, move to a folder, and delete with confirmation.
- Generic non-media file opening through Android's `ACTION_VIEW` intent with FileProvider when a compatible app is installed.
- File details show name, MIME type, size, modification time, and path.
- Recursive selected-folder usage summary, calculated off the main thread. It reports known file bytes and counts; it is not a device-wide free-space measurement.
- Repository helpers for recursive copy and move (rename fallback to copy-then-delete).
- Platform-independent filename validation with unit-test source coverage.
- Media3 ExoPlayer URI playback foundation.
- ZIP extraction to app cache with path-traversal protection.

### Still incomplete for MVP 1.0
- In-app audio/video transport controls, seek bar, queue/playlists, subtitle/audio-track selection, and MediaSessionService background playback.
- Functional SMB server/share connection and directory browsing. `SmbRepository` remains an integration contract only; do not treat SMB as implemented.
- Operation progress/cancellation, undo, and robust multi-select.
- TV focus restoration, remote transport controls, and physical Android TV validation.
- USB OTG and SD-card validation on actual devices.
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

### `MANAGE_EXTERNAL_STORAGE` access

File Manager + requests the **`MANAGE_EXTERNAL_STORAGE`** permission ("all files access") so it can browse the full internal and external storage without requiring a per-folder SAF picker. This works like many Play Store file managers:

- **Storage roots shown on first launch.** The app lists available storage locations (internal storage, SD card, etc.) — pick one to start browsing.
- **Navigate freely.** Browse directories including `DCIM/`, `Download/`, `Android/`, and other locations subject to Android's scoped storage rules.
- **Runtime permission required.** On Android 10 (API 29) and above, the app asks you to grant "All files access" in system settings before showing any files. Without this permission, the app cannot list or modify files.
- **FileProvider for sharing.** Non-media files are opened via `ACTION_VIEW` using `FileProvider` so other apps can access them.

### What you can and can't see

With `MANAGE_EXTERNAL_STORAGE`:

- **You can see:** Most of internal and external storage, including user files, `DCIM/`, `Download/`, and many system directories.
- **You may not see:** App-private directories of other apps under `Android/data/<package>/` (even with this permission, Android 11+ protects other apps' private data), and some system-protected directories.

### Operation limitations

Copy, move, rename, and delete operations use direct file I/O. On large or remote folders, recursive scans can take time; copy/move currently do not yet expose progress or cancellation. The usage summary is computed recursively for the selected folder and sums file sizes; it cannot report total device free space.

## Privacy and product principles

- Free, no ads, no tracking/analytics, and no account registration.
- Local file management remains offline-first.
- Requests `MANAGE_EXTERNAL_STORAGE` for full storage access; files are not logged or transmitted.
- SMB credentials must not be logged or stored in plaintext when SMB is implemented.

## Next development priorities

1. Add operation progress/cancellation and undo for delete/move operations.
2. Finish Media3 in-app controls and background playback with a media session service.
3. Implement actual SMB connection and share browsing.
4. Improve TV D-pad focus behavior and test remote controls.
5. Run Android build/tests and validate USB/SD storage on physical phone and TV hardware.
