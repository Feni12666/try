# Video & Storage Pro — File Manager + Player V3

Version: `4.0.0-alpha04-file-video` (`versionCode 19`)

## Implemented in this build

- Premium Blue/White UI plus System / Light / Dark / AMOLED themes.
- Personal splash remains isolated to launch; the normal app UI uses the neutral `Video & Storage Pro` name.
- Bottom navigation: Home / Files / Videos / Duplicates / More.
- Manual-first file manager. No fixed Nagram source and no automatic destination.
- Phone and selected USB/SSD browsing.
- Optional Shizuku browsing for Android/data and Android/obb.
- List/Grid, breadcrumb navigation, search, category filters, sorting, hidden-file toggle, multi-select and file properties.
- Create folder, rename, delete, manual Copy and safe Move.
- User always chooses the destination storage/folder.
- Same-name policy: Keep Both / Replace / Skip.
- Transfer progress with foreground service, Pause / Resume / Cancel.
- Safe Move: Copy -> verify -> selected 1/3/5 minute delay -> destination revalidation -> source delete.
- Two active transfer workers and queue reporting are retained.
- Videos tab scans phone MediaStore and the selected USB tree and groups videos by folder.
- Built-in Media3/ExoPlayer player.
- Player features: normal controls, next/previous, double tap left/right ±10s, left vertical swipe brightness, right vertical swipe volume, playback speed, resize modes, rotate, PiP and resume position.
- Video files opened from Files can launch the built-in player for Phone/USB sources.

## Safety / intentional behavior

- Auto Sync remains OFF by default.
- Restricted Shizuku video paths are browsable in Files, but the player does not directly consume restricted Shizuku file descriptors in this build.
- Duplicates tab keeps the existing exact-duplicate design/requirements; the full duplicate cleanup workflow remains a separate hardening milestone.
- UI/functionality is original and is not a pixel-for-pixel copy of RS File Manager or MX Player.

## Build note

This source is GitHub Actions ready and uses the existing permanent test-signing secret `DEBUG_KEYSTORE_B64` in the workflow.
