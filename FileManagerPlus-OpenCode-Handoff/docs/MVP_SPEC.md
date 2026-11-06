# File Manager + — MVP 1.0 scope

## Product
A native Android file manager and media player for Android phones and Android TV.

## Principles
- Free, no advertisements, no analytics/tracking, no account.
- Offline-first local file management.
- Use SAF and user-granted access; avoid broad storage permissions.
- Keep UI separate from core storage, networking, and media logic.
- Minimal build/dependency footprint; development must remain practical on an 8 GB RAM computer.
- TV navigation must work with a physical remote/D-pad.
- Removable USB and SD storage should work through Android-supported document providers.

## Core file management
- Browse user-selected storage and nested directories.
- Create folders.
- Copy, move, rename, delete files/folders.
- Search.
- Sort by name, date, size, and type.
- Show/hide hidden entries.
- File details (name, type, size, modified date, location/URI).
- Storage usage summary for the selected folder, with clear limitations.
- Handle permission/provider errors and unsupported operations safely.
- Progress/cancellation and undo are desired MVP completion work.

## Media
- Play common audio/video formats through Media3.
- In-app play/pause, seek, timeline, and basic transport controls.
- Queue/playlist basics.
- Background audio playback with MediaSessionService and notification/media controls.
- Handle audio focus and lifecycle correctly.

## SMB
- Connect to SMB server/share using user-supplied connection details.
- Browse directories and open/download supported files as feasible.
- Do not log or persist passwords in plaintext.
- A dependency or interface alone is not a completed SMB feature.

## Android TV
- Adapt layout for TV viewing distance.
- D-pad navigation with visible focus and predictable focus restoration.
- Remote-friendly actions and media controls.
- Validate on an actual Android TV/Google TV device or emulator where practical.

## USB OTG / SD
- Access removable storage through SAF when offered by the device.
- Gracefully handle provider-specific limitations.
- Test on physical phone hardware and document device/Android version.

## Explicitly out of scope for MVP 1.0
- RAR extraction.
- ZIP creation.
- Cloud storage.
- FTP.

## Definition of done
A feature is complete only when:
1. It is implemented end-to-end (not just an interface, dependency, or TODO).
2. Errors and permission/provider limitations are handled.
3. Relevant automated tests pass where practical.
4. Build succeeds in the stated environment.
5. Device-specific behavior is explicitly tested or clearly marked unverified.
6. README and project status reflect reality.
