# Phase status

## Phase 0 — Project Foundation

- Kotlin, Android API and Gradle baseline
- Version catalog and Gradle wrapper
- Package identity `com.nagram.usbbridge`
- Java 17, Compose, Material 3 and Navigation setup
- English/Bangla resource framework
- Unit and instrumentation test foundations
- CI build, lint, test and APK workflow
- Architecture and Room schema plan

## Phase 1 — UI Foundation

- Premium light/dark design system
- Bottom navigation above system navigation
- Home, Files, Videos, Duplicates and Transfer screens
- Focused Player and Shizuku screens
- Clear Phone / USB storage switch
- Side-by-side duplicate specification comparison
- Keep Newest / Keep Oldest / Delete confirmation interaction
- Smart Auto-Sync rules and status UI
- Manual copy/move contract: the user always chooses the destination folder

## Phase 2 — Storage Access Foundation

- Phone shared-storage access through the Android All files access setting
- Phone folder listing and safe up-navigation inside shared storage
- User-selected USB/OTG SAF tree grant with persisted read/write permission
- Clear Phone / USB switch with the current grant and path status
- Optional Shizuku user service for read-only `Android/data` and `Android/obb`
- Protected-path allowlist: only `Android/data` and `Android/obb`; no write,
  rename or delete API is exposed by the Shizuku service
- Smart Auto-Sync source and target folder pickers, saved as explicit user
  choices; no fixed destination is assumed
- Unit coverage for the protected-path allowlist

## Phase 3 — Safe Smart Sync core

- One-tap video sync from a user-selected SAF source to a user-selected target
- Optional Shizuku protected-folder source, always read-only
- Recursive video discovery for common video MIME types and extensions
- Existing target files are matched by size then full SHA-256; only a byte-
  identical file is skipped as a duplicate
- Copy to a temporary destination, read-back SHA-256 verification, then rename
  to the final safe filename
- Cancellation and any failure leave the source untouched

## Verification

- The recovered checkout is not being represented as build-verified: this
  workspace has no JDK 17 and the Gradle distribution cannot be downloaded.
- Phase 2 source has been statically reviewed and checkpointed. A fresh Android
  build must run in a JDK 17 + Android SDK / CI environment before any APK is
  declared ready.

Milestone A remains in progress. The Smart Sync core is ready for a fresh CI
build and real-device Phone/USB tests. Manual file operations, duplicate scan
and player work remain after this checkpoint.

Manual operations, Media3 playback and duplicate scanning are not implemented
in this checkpoint.
