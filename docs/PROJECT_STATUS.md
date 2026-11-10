# Project status — File Manager +

Last reconciled: 2026-11-10 (branch `kilo/adept-beacon-wln`, commit `61e253a`, CI run `42742824580` **success**).

## Build & CI
- GitHub Actions CI (`.github/workflows/ci.yml`) on `kilo/adept-beacon-wln`: latest run `42742824580` (“Fix SettingsSwitch dark theme callback signature”) completed successfully (unit tests + debug APK assembled; artifact `file-manager-plus-debug-apk`).
- Local Gradle/SDK builds not verified in the current sandbox environment (dependency/SDK constraints); rely on CI for baseline verification when possible.

## Project configuration
- Namespace/application ID: `com.filemanagerplus`
- Kotlin + Jetpack Compose + Material 3
- AGP 8.9.1, Kotlin 2.1.10, Gradle 8.11.1
- compileSdk/targetSdk 35, minSdk 26, Java/Kotlin 17
- Compose BOM 2025.02.00, Media3 1.5.1, coroutines 1.9.0, documentfile 1.0.1, smbj 0.13.0, JUnit 4.13.2
- Low-memory Gradle defaults (1.5 GB heap, 2 workers, parallel off, Kotlin in-process)

## Implemented (source-level)
- App shell with mobile bottom navigation and dedicated TV `NavigationRail` layout (`Screen.HOME/BROWSER/CATEGORY/SETTINGS/PLAYER`).
- Home: internal storage usage (StatFs), category shortcuts, storage roots, recent files (bounded scan off main thread).
- File browser: breadcrumbs, search, sort/list-grid toggle, refresh/new folder/usage, details/rename/copy/move/delete, SAF destination picker for copy/move with basic guards, empty/loading/error states.
- Category browsing by extension (bounded), recent files scan (bounded, skips dot dirs/`Android`).
- Settings persisted in `SharedPreferences` (`fm_prefs`: dark theme, show hidden, grid view, default sort).
- Basic in-app media playback screen via Media3 `MediaPlaybackController`.
- `MANAGE_EXTERNAL_STORAGE` permission flow, `FileProvider`, phone+TV launcher intents.
- Storage/repository helpers: direct file listing/ops, recursive copy/move/delete, usage calculation, ZIP extraction helper, filename validation helper, SMB placeholder contract.
- Unit tests: `StorageNameValidationTest` (JVM-friendly via nullable context in repository).

## Maintainability notes (this pass)
- UI split for clarity/extensibility: `FileManagerModels.kt` (shared screen/action/transfer types), `FileOperations.kt` (external file open helper), `BrowserScreen.kt` (browser screen extracted from monolith), `UiUtils.kt` (formatting/icons/category helpers), `FileManagerRoot.kt` (app shell, home/category/settings/player/TV and shared components).
- `StorageRepository` delegates name validation to shared `validateDisplayName` helper to avoid duplication.

## Incomplete / MVP gaps
- Full media UX: transport controls/seek bar UI polish, queue/playlists, subtitles/audio tracks, background `MediaSessionService`.
- Real SMB browsing/connection (currently contract/placeholder).
- Operation progress/cancellation, undo, multi-select, robust error surfacing for long SAF/file operations.
- TV focus/remote behavior hardening and physical device validation.
- USB OTG/SD-card behavior verified on real devices.
- Android/Compose integration tests, release signing, broader test coverage.

## Validation status
- CI: unit tests + debug assemble green on latest commit (`42742824580`).
- No verified physical phone/TV, USB OTG, or SD-card testing in this environment.
- Treat device-dependent behavior as unverified until tested on hardware.
