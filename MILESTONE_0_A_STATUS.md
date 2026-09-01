# SHAHADAT PRO — Milestone 0 + A Test Foundation

## Milestone 0 — Data & Signing Safety

- applicationId intentionally remains `com.nagram.usbbridge` so the existing data namespace is preserved.
- legacy SharedPreferences name remains `bridge`.
- legacy journal remains `bridge_journal.db`; it is not renamed, deleted, or destructively migrated.
- current 3-minute cleanup setting and always-on duplicate guard are preserved.
- GitHub Actions still requires the permanent `DEBUG_KEYSTORE_B64` secret already created by SHAHADAT.
- Direct and Play Store distributions are separated by Gradle flavors.
- Direct flavor may request `MANAGE_EXTERNAL_STORAGE`; Play Store flavor intentionally does not declare it.

## Milestone A — New Foundation

- Kotlin + Jetpack Compose + Material 3 shell.
- MVVM state layer (`MainViewModel`).
- Room database foundation: `shahadat_pro_index.db` for future phone/USB video index and duplicate cache.
- WorkManager reconciliation worker + existing foreground BridgeService hybrid.
- existing Java safety/transfer engine remains in place during migration instead of being rewritten at once.
- five mobile-safe bottom tabs: Home, Files, Videos, Duplicates, Sync.
- phone / USB storage switch is visible in Files.
- Shizuku grant, USB SAF selection, Start/Pause Smart Sync remain functional from Compose.
- existing SHAHADAT profile image retained.
- current fast queue core remains: one active transfer + up to ten prepared files.

## Intentionally NOT claimed complete yet

These belong to later approved milestones:

- Full Copy/Move/Rename/Delete/New Folder file manager — Milestone B.
- Exact + Similar duplicate resolution UI/engine — Milestone C.
- General source-folder Smart Sync profiles — Milestone D.
- Media3 player and gesture controls — Milestone E.
- notification Pause/Cancel polish, release AAB and final documentation — Milestone F.

## First test build

GitHub Actions workflow builds:

`app/build/outputs/apk/direct/debug/app-direct-debug.apk`

Artifact name:

`SHAHADAT-PRO-M0-A-Test-APK`
