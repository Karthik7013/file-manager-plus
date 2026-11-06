# File Manager + — project instructions

## Mission
Build File Manager + as a free, privacy-first, offline-first Android file manager and media player for Android phones and Android TV.

## Before changing code
1. Read `README.md`, `docs/PROJECT_STATUS.md`, `docs/MVP_SPEC.md`, and `docs/NEXT_TASK.md` if present.
2. Inspect the actual source tree and current Git status. The source snapshot may differ from this handoff.
3. Identify the highest-priority incomplete MVP item. Do not assume a feature works because a class, dependency, note, or interface exists.
4. Make a small, coherent, buildable change. Avoid broad rewrites.

## Technology and architecture
- Kotlin, Jetpack Compose, Material 3.
- MVVM + Repository; keep UI separate from storage, network, and media logic.
- Android Storage Access Framework (SAF) for user-selected storage. Do not add broad `MANAGE_EXTERNAL_STORAGE` permission without a compelling, documented requirement.
- Minimum SDK 26; compile/target SDK 35 in the current snapshot.
- JDK 17; Android Gradle Plugin 8.9.1; Kotlin 2.1.10; Gradle 8.11.1.
- Preserve low-memory settings for an 8 GB development machine: Gradle heap 1.5 GB, max 2 workers, parallel disabled, Kotlin in-process.
- Prefer platform APIs and small dependencies. Do not introduce heavyweight build tooling without need.

## Product constraints
- Free, no ads, no tracking/analytics, no account requirement.
- Offline-first for local files.
- SMB credentials must never be logged or stored in plaintext.
- Respect SAF provider limitations and handle unsupported operations gracefully.
- Android TV must be usable with a D-pad/remote; focus must be visible and predictable.
- Support removable USB/SD storage through SAF where the device/provider allows it.
- Out of MVP scope: RAR extraction, ZIP creation, cloud storage, FTP.

## MVP feature priorities
1. Reliable file browsing and operations: create, copy, move, rename, delete, search, sorting, details, hidden files, selected-folder usage summary.
2. Parent navigation, robust error handling, operation progress/cancellation, and undo where feasible.
3. Media3 in-app audio/video controls, seeking, queue basics, and background playback via MediaSessionService.
4. Real SMB server/share connection and directory browsing (not a placeholder contract).
5. Android TV D-pad/focus behavior and phone/TV responsive layouts.
6. USB OTG and SD-card behavior verified on physical devices.

## Engineering rules
- Inspect existing implementation before editing; reuse sound code.
- Do not create fake implementations, placeholder success states, or claim unfinished features are complete.
- Handle SAF provider errors, null metadata, permissions, cancellation, and large directories.
- Avoid blocking the main thread; use coroutines/appropriate dispatchers.
- Add unit tests for platform-independent logic and tests for bug fixes.
- Keep dependencies minimal and explain any new dependency.
- Never hardcode secrets, credentials, personal paths, or machine-specific SDK paths.
- Do not modify generated build output.
- Keep README and status docs aligned with actual behavior.

## Verification and reporting
- Run the narrowest relevant tests first, then broader tests/build if the environment allows.
- Preferred checks from project root:
  - `gradle --no-daemon --max-workers=2 test`
  - `gradle --no-daemon --max-workers=2 :app:assembleDebug`
- If Gradle/SDK/network/device is unavailable, state exactly what could not be run. Never claim a build, test, APK install, TV test, or hardware test unless actually performed.
- At the end of each task report:
  1. What changed (files and behavior).
  2. Tests/build commands run and exact results.
  3. Known limitations and blockers.
  4. Device-only checks still needed.
  5. The next recommended task.
- Update `docs/PROJECT_STATUS.md` and `docs/NEXT_TASK.md` when meaningful progress is made.

## Git discipline
- Inspect `git status` and branch before edits.
- Do not discard user changes or rewrite history.
- Use focused commits with clear conventional messages when asked to commit.
- Do not push or publish without user authorization.
