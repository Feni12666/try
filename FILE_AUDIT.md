# File Audit — Manual File Manager Alpha 03

## Active app entry / UI
- `MainActivity.kt` — permission launchers, SAF root selection, theme host, no auto watcher start.
- `ShahadatProApp.kt` — splash, Home, Videos/Duplicates/Sync shell, settings.
- `ManualFilesScreen.kt` — real Phone/USB/Shizuku file browser and destination chooser.
- `Theme.kt` — blue/white light theme + dark + AMOLED + System.

## Manual file engine
- `FileBrowserModels.kt` — storage/file/operation models.
- `FileManagerRepository.kt` — Phone, USB SAF and Shizuku file operations.
- `FileManagerViewModel.kt` — browsing, selection, manual destination flow and operation controls.
- `ManualTransferService.kt` — 2-worker foreground copy/move engine, verification, cleanup delay, progress notification.

## Shizuku restricted access
- `IRestrictedFileService.aidl`
- `RestrictedFileService.java`
- `RestrictedShizukuClient.kt`

Restricted service is constrained to `/storage/emulated/0/Android/data` and `/storage/emulated/0/Android/obb`.

## Legacy fixed automation
Old `BridgeService`, `BootReceiver`, Nagram-only privileged service and Nagram AIDL are removed from this source and are not registered in the manifest.

## Persistent compatibility
Legacy Room/SQLite/cache classes retained where they do not start automation, to avoid unnecessary migration breakage during the project transition.
