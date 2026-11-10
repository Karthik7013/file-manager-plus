# Next development task

## Baseline
- CI is green on `kilo/adept-beacon-wln` (latest: run `42742824580`, commit `61e253a`). Confirm with `gh run list --branch kilo/adept-beacon-wln --limit 3` and inspect failures with `gh run view <id> --log-failed` if needed.
- Keep changes small, buildable, and verified by CI (`gradle --no-daemon --max-workers=2 test` and `:app:assembleDebug`).

## Recommended next increments (pick highest value)
1. **Browser robustness & UX:** improve SAF copy/move error handling and user feedback (show failures, handle conflicts/overwrite intentionally), and add progress/cancellation for long copy/move or recursive scans.
2. **Navigation/state:** preserve/restore browser state predictably (back stack, root/path edge cases) and ensure “recent file” opens navigate to a useful location/action.
3. **Media playback:** evolve `PlayerScreen`/`MediaPlaybackController` toward fuller controls (seek bar, duration/position), and plan `MediaSessionService` for background playback.
4. **Codebase maintainability:** continue splitting large UI files only when it improves clarity (e.g. extract home/settings/player/TV into focused files if they grow), add KDoc for non-obvious flows, and add unit tests for repository/scan logic where platform-independent.
5. **SMB:** replace `SmbRepository` placeholder with a minimal, testable connection/browse implementation (no credential logging/plaintext storage).
6. **TV polish:** audit D-pad/focus order and remote behavior on key screens.

## Device-only checks still needed
- Physical phone and Android TV (focus/remote, playback, permissions).
- USB OTG and SD-card storage roots/operations.
