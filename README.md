# USB Video Manager

Native Android application for safely browsing phone and USB storage,
reviewing duplicate videos, previewing media and synchronizing only new files.

## Current delivery

Phase 0, Phase 1 and the Phase 2 storage-access foundation:

- Kotlin + Jetpack Compose + Material 3
- Android 11 minimum (`minSdk 30`)
- Android API 37 compile/target baseline
- Premium light/dark UI and English/Bangla resources
- Home, Files, Videos, Duplicates, Smart Auto-Sync, Player and Shizuku screens
- Working navigation and persisted UI state
- Real Phone shared-storage browser after the user grants All files access
- User-selected USB/OTG SAF tree permission, persisted across app restarts
- Clear Phone / USB switch and real folder listing/navigation
- Optional Shizuku user service for read-only `Android/data` and `Android/obb` browsing
- User-selected Smart Auto-Sync source and target route, persisted across restarts
- One-tap Smart Sync for selected SAF folders and optional Shizuku protected
  folders: scans videos recursively, skips byte-identical copies using SHA-256,
  copies through a temporary file, and verifies the destination SHA-256 again
- Duplicate keep-policy unit tests
- CI build, lint and debug APK artifact
- Manual copy/move destination selection is a fixed safety requirement

The Smart Sync engine never deletes a source file. Manual copy/move, rename,
delete, duplicate scanning and in-app playback remain separate planned work.
Shizuku access is read-only by design; it never writes to protected Android
folders.

## Build

Requirements:

- JDK 17
- Android SDK Platform 37.0
- Android SDK Build Tools 36.0.0

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

See `docs/ARCHITECTURE.md`, `docs/UI_SPEC.md` and `docs/PHASE_STATUS.md`.
