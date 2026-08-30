# Technical notes

- `NagramPrivilegedService` is a Shizuku UserService. On non-root Shizuku it runs as shell UID 2000.
- AIDL exposes only list/stat/open/delete operations and enforces a canonical-path whitelist.
- `BridgeService` is an Android foreground service (`specialUse`) and polls every 3 seconds.
- A file is eligible after 3 unchanged observations after first sighting.
- SAF tree URI is persisted with `takePersistableUriPermission`.
- Copy destination is the selected tree root itself (choose the `ok` folder), not a hard-coded `/mnt/media_rw` path.
- Deletion happens only after copied byte count equals the expected source length and source size/mtime still match.
