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
- SAF folder selection and nested directory listing; persistable read/write grants are attempted.
- Create folders; search current folder; sort by name/date/size/type; show/hide dot-prefixed entries.
- File actions: details, rename, copy to selected SAF folder, move to selected SAF folder, delete with confirmation.
- Generic file opening using Android `ACTION_VIEW`.
- File details: name, MIME type, provider-reported size/date, URI.
- Recursive selected-folder usage summary off the main thread. This is not device-wide free-space reporting.
- SAF copy/move helpers, including provider-native move with copy/delete fallback.
- Filename validation helper and a unit-test source file.
- Media3 ExoPlayer URI playback foundation.
- ZIP extraction into app cache with path-traversal protection.

## Explicitly incomplete / not to be represented as done
- Full in-app audio/video transport controls, seek bar, queue/playlists, subtitle/audio-track selection, and MediaSessionService background playback.
- Real SMB server/share connection and directory browsing. `SmbRepository` is only an integration contract in this snapshot.
- True parent-folder navigation stack.
- Operation progress/cancellation, undo, and robust multi-select.
- TV focus restoration, remote transport controls, and physical Android TV validation.
- USB OTG/SD-card validation on real devices; behavior depends on Android and the SAF provider.
- Android/Compose integration tests and release signing.

## Validation status
- Archive integrity was checked previously.
- No verified Gradle compile, complete Gradle test run, APK install, physical phone test, Android TV test, USB OTG test, or SD-card test is established by the handoff context.
- Treat all build/test status as unknown until OpenCode runs the commands in the current environment and records exact output.

## Important caveats
- SAF providers may not support every rename/delete/move/metadata operation.
- Recursive scans/copy/move can be slow on large or remote trees.
- The current usage summary only counts sizes reported by the selected folder's provider.
- Do not infer feature completeness from dependencies or interface names.
