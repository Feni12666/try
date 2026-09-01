# File-by-file audit summary

## Build / CI
- `build.gradle` — Android/Kotlin plugin versions retained.
- `settings.gradle` — repositories/module structure retained.
- `app/build.gradle` — version bumped to 17 / `4.0.0-alpha02-bluewhite`; Compose icons dependency added; Direct/Play flavors retained.
- `.github/workflows/build-apk.yml` — permanent signing restore retained; artifact renamed for BlueWhite test build.
- `gradle.properties` — AndroidX compatibility retained.

## App entry / UI
- `MainActivity.kt` — legacy data namespace preserved; default cleanup 3 min; duplicate guard forced on; configured parallel transfers set to 2.
- `ShahadatProApp.kt` — rebuilt to approved Design #8; animated personal splash; no personal name after splash; Home/Files/Videos/Duplicates/Sync; Phone/USB switch; duplicate compare layout; Shizuku/Sync screen; cleanup 1/3/5 selector.
- `Theme.kt` — old dark/neon palette replaced with premium blue + white Material 3 palette.
- `MainViewModel.kt` — exposes active/ready queue, safe mode and cleanup-delay state; persists delay selection.
- `strings.xml` / Manifest — normal app label changed to neutral `Video & Storage Pro`; personal name remains splash-only.

## Existing transfer safety core
- `BridgeService.java` — audited and upgraded to 2 active workers + 10 prepared queue, automatic 1-worker safe fallback, same-content concurrent lock, existing verification/cleanup revalidation retained.
- `BridgeDatabase.java` — synchronized legacy safety journal retained.
- `FingerprintUtils.java` — sampled SHA-256 quick fingerprint + full SHA-256 candidate confirmation retained.
- `DocumentTreeUtils.java` — SAF document safety helpers retained.
- `StorageUtils.java` — USB space/filesystem preflight retained.
- `NagramPrivilegedService.java` / AIDL — Shizuku protected-source access retained.
- `BootReceiver.java` — recovery hook retained.
- `BridgeSnapshotReader.kt` — history/stat snapshot bridge retained.

## New architecture foundation
- `ProDatabase.kt`, `MediaIndexEntity.kt`, `MediaIndexDao.kt`, `Repositories.kt` — Room media/duplicate-cache foundation retained.
- `ReconcileWorker.kt`, `WorkScheduler.kt`, `TransferServiceFacade.kt` — WorkManager + foreground transfer hybrid retained.
- `ShahadatProApplication.kt` — Room repository application scope retained.

## Static checks completed
- All Android XML resources parse successfully.
- Personal-name search in app source finds `SHAHADAT` only in the splash composable.
- Obsolete single-transfer symbols are absent from BridgeService.
- Invalid Compose `layout.weight` import is absent.
- Version and queue constants verified.

Actual Android compilation still requires the GitHub Actions Android toolchain.
