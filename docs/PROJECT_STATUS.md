# Project status — File Manager +

Last reconciled against the supplied source archive: `FileManagerPlus-MVP1-increment-2.zip` (35 ZIP entries; project root `FileManagerPlus/`).

> This is a source-level status summary, not a claim that the project has compiled or been tested on hardware. Re-check every item against the current working tree before relying on it.

## Project configuration observed
- Namespace/application ID: `com.filemanagerplus`
- Kotlin + Jetpack Compose + Material 3
- Android Gradle Plugin 8.9.1
- Kotlin 2.1.10
- compileSdk 35, targetSdk 35, minSdk 26
- JDK 17; Gradle 8.11.1 expected
- Compose BOM 2025.02.00
- Media3 1.5.1
- SMBJ 0.13.0
- JUnit 4.13.2
- Gradle low-memory defaults: 1.5 GB heap, max 2 workers, parallel disabled, Kotlin in-process
- The supplied archive does not include Gradle wrapper scripts/JAR.

## Present in the source snapshot
- Compose/Material 3 app foundation and separate mobile/TV presentation functions.
- Light/dark theme switching.
- `MANAGE_EXTERNAL_STORAGE` permission for full storage access; storage roots listed on first launch.
- Direct file-based directory listing, create folders; search current folder; sort by name/date/size/type; show/hide dot-prefixed entries.
- File actions: details, rename, copy to folder, move to folder, delete with confirmation.
- Generic file opening using Android `ACTION_VIEW` with FileProvider.
- File details: name, MIME type, size/date, path.
- Recursive selected-folder usage summary off the main thread. This is not device-wide free-space reporting.
- Repository helpers for recursive copy and move (rename fallback to copy-then-delete).
- Filename validation helper and a unit-test source file.
- Media3 ExoPlayer URI playback foundation.
- ZIP extraction into app cache with path-traversal protection.
- FileProvider configured for sharing files with other apps.

## Explicitly incomplete / not to be represented as done
- Full in-app audio/video transport controls, seek bar, queue/playlists, subtitle/audio-track selection, and MediaSessionService background playback.
- Real SMB server/share connection and directory browsing. `SmbRepository` is only an integration contract in this snapshot.
- Operation progress/cancellation, undo, and robust multi-select.
- TV focus restoration, remote transport controls, and physical Android TV validation.
- USB OTG/SD-card validation on real devices.
- Android/Compose integration tests and release signing.

## Validation status
- Archive integrity was checked previously.
- No verified Gradle compile, complete Gradle test run, APK install, physical phone test, Android TV test, USB OTG test, or SD-card test is established by the handoff context.
- Treat all build/test status as unknown until the agent runs the commands in the current environment and records exact output.

## Important caveats
- `MANAGE_EXTERNAL_STORAGE` permission is required on Android 10+; without it, no files are visible.
- App-private directories of other apps (`Android/data/<package>/`) may not be accessible even with this permission on Android 11+.
- Recursive scans/copy/move can be slow on large or remote trees.
- The current usage summary only counts sizes reported by the file system.
- Do not infer feature completeness from dependencies or interface names.
