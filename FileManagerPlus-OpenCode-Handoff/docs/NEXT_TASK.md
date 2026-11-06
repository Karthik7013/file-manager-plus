# Next development task

## First action: establish a trustworthy baseline
Do not immediately add features. Inspect the actual repository and determine whether the uploaded source was copied into the correct project root.

1. Run `git status --short --branch` and inspect the top-level tree.
2. Read `README.md`, Gradle files, manifest, and all Kotlin source.
3. Confirm the project root is the directory containing `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, and `app/`.
4. Check whether a Gradle wrapper exists. If absent and Gradle 8.11.1 is installed, generate a wrapper using `gradle wrapper --gradle-version 8.11.1`; do not add wrapper files by hand.
5. Run `gradle --no-daemon --max-workers=2 test`.
6. Run `gradle --no-daemon --max-workers=2 :app:assembleDebug`.
7. Record exact failures and distinguish source errors from missing SDK, network, or toolchain setup.

## After baseline
Choose the highest-value issue revealed by inspection. Recommended order:
1. Fix compile/test failures and stabilize the baseline.
2. Improve parent-folder navigation and SAF operation error handling.
3. Add operation progress/cancellation for long copy/move/recursive scan work.
4. Complete Media3 in-app controls and background playback.
5. Implement functional SMB browsing.
6. Improve TV focus and remote behavior.
7. Validate USB OTG/SD on real devices.

Do one coherent increment at a time. Update `PROJECT_STATUS.md` and this file after verified changes.
