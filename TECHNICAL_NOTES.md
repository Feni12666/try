# Technical Notes — Winner Base

## Source
Nagram package: `xyz.nextalone.nagram`

Allowed source base:
`/storage/emulated/0/Android/data/xyz.nextalone.nagram/files`

Allowed media subfolders currently include:
- videos
- documents
- images
- audios
- stories
- emojis

Source access is performed through a Shizuku UserService. Destination access is performed through Android Storage Access Framework (SAF).

## Safety model
The source is never removed during the copy operation. The destination is first written as an incomplete `.part` object. The copy must pass verification and be finalized. Source cleanup is a later, separate stage.

Default cleanup delay: 5 minutes.

Before cleanup, the service revalidates the exact recorded destination URI, destination size/readability, and the recorded content quick-fingerprint; it also confirms the source still has the same size/mtime. If any check is uncertain, the source remains.

## Same Video Duplicate Guard
The guard deliberately ignores filename as a duplicate identity. Exact size narrows candidates; a sampled SHA-256 fingerprint narrows them further; a full SHA-256 comparison confirms content before a copy is skipped.

This prevents a renamed copy of the same downloaded media from being offloaded twice while avoiding unsafe skips based only on rounded MB values.

## Filesystem preflight
The app attempts a best-effort mapping from the SAF tree volume to a mounted StorageVolume, then checks `/proc/mounts` when available. If the filesystem is confidently FAT32/vfat and the file exceeds the FAT32 single-file limit, the transfer is blocked before writing. If filesystem type cannot be established, the operation remains copy-first/non-destructive and any write failure preserves the source.

## Recovery
Intermediate journal states are persisted in SQLite. On service restart, interrupted temporary destination references are cleaned where possible and jobs are returned to a retry-safe state. No recovery path authorizes source deletion by assumption.

## Build compatibility fixes
- `android.useAndroidX=true`
- `android.enableJetifier=true`
- `buildFeatures { aidl true }`
- AndroidX annotation dependency
- `rikka.shizuku.ShizukuProvider` declared in the manifest
- `openRead(String)` AIDL implementation does not expose a checked Java exception


## Mobile UI / Android 15 insets
The activity applies status/navigation system-bar insets to the root shell. The custom bottom navigation is laid out above the real Android navigation/gesture area, with additional bottom margin. Compact-width sizing is applied at <=380dp.

## Retry behavior
Safety/IO failures use bounded in-memory backoff instead of immediate endless retry loops. Space/incompatible-destination conditions use longer waits. A service restart may retry again, but destructive cleanup still requires full revalidation.
