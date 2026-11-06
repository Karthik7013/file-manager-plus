# Contributing to File Manager +

Thank you for considering a contribution to File Manager +. This is a free, privacy-first, offline-first Android file manager and media player.

## Before you start

1. Read `AGENTS.md` (project instructions), `README.md`, `docs/MVP_SPEC.md`, `docs/PROJECT_STATUS.md`, and `docs/NEXT_TASK.md`.
2. Inspect the actual source tree and Git status. The working tree may differ from the docs.
3. Identify the highest-priority incomplete MVP item from `docs/NEXT_TASK.md`.
4. Make a small, coherent, buildable change. Avoid broad rewrites.

## How to contribute

1. **Fork the repository** and create a feature branch from `main` (or the current development branch).
2. **Inspect before editing** — reuse sound code; do not create fake implementations, placeholder success states, or claim unfinished features are complete.
3. **Keep changes small and focused** — one coherent increment per pull request.
4. **Run checks** — run unit tests and, if the environment supports them, a debug APK build:
   ```
   gradle --no-daemon --max-workers=2 test
   gradle --no-daemon --max-workers=2 :app:assembleDebug
   ```
   Note: this project does not ship a Gradle wrapper. Install Gradle 8.11.1 and run `gradle wrapper --gradle-version 8.11.1` locally to generate it.
5. **Update docs** — keep `docs/PROJECT_STATUS.md` and `docs/NEXT_TASK.md` aligned with verified behavior.
6. **Commit with a clear conventional message** (e.g. `feat: ...`, `fix: ...`, `docs: ...`).
7. **Open a pull request** describing what changed, how you verified it, and any known limitations.

## Project constraints

- Free, no ads, no tracking/analytics, no account requirement.
- Offline-first for local files.
- Android Storage Access Framework (SAF) for user-selected storage; do not add broad `MANAGE_EXTERNAL_STORAGE` without a compelling, documented requirement.
- SMB credentials must never be logged or stored in plaintext.
- Android TV must be usable with a D-pad/remote; focus must be visible and predictable.
- Out of MVP scope: RAR extraction, ZIP creation, cloud storage, FTP.

## Engineering rules

- Avoid blocking the main thread; use coroutines/appropriate dispatchers.
- Add unit tests for platform-independent logic and tests for bug fixes.
- Keep dependencies minimal; explain any new dependency.
- Never hardcode secrets, credentials, personal paths, or machine-specific SDK paths.
- Handle SAF provider errors, null metadata, permissions, cancellation, and large directories.

## Ask before

Ask the maintainer before:
- Destructive Git operations or deleting files
- Changing the package/application ID
- Adding large dependencies
- Pushing changes to GitHub

## License

By contributing, you agree that your contributions will be licensed under the MIT License (see `LICENSE`).
