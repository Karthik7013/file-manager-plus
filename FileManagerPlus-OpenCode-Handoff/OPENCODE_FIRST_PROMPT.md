You are taking over development of File Manager +.

First read `AGENTS.md`, `README.md`, `docs/PROJECT_STATUS.md`, `docs/MVP_SPEC.md`, and `docs/NEXT_TASK.md`. Then inspect the actual source tree and Git status. Do not assume the handoff notes are more authoritative than the current code; reconcile them with the code.

Important project context:
- Native Android app: Kotlin, Jetpack Compose, Material 3, MVVM + Repository.
- Free, no ads/tracking, offline-first, SAF-based storage access.
- Target use: Android phones and Android TV.
- Developer machine has 8 GB RAM; keep Gradle memory/workers conservative.
- Current supplied source snapshot is incomplete. SMB is not functional yet; Media3 playback is only a foundation; TV focus and USB/SD hardware behavior are not verified.
- Out of scope: RAR extraction, ZIP creation, cloud storage, FTP.
- Never claim a build or test passed unless you run it and see the successful result.

Your first task is ONLY to establish the baseline:
1. Verify project root and repository layout.
2. Inspect Gradle/JDK/Android SDK/wrapper availability.
3. Run unit tests and debug APK build with low-memory settings if the environment supports them.
4. Fix only blockers necessary to get a trustworthy baseline; do not start a broad rewrite.
5. Report exact commands, results, failures, and the next best focused task.
6. Update `docs/PROJECT_STATUS.md` and `docs/NEXT_TASK.md` only with verified facts.

Ask me before destructive Git operations, deleting files, changing package/application ID, adding large dependencies, or pushing changes to GitHub.
