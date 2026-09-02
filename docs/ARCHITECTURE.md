# Architecture baseline

The approved production architecture is a single-activity Kotlin Android app
using Jetpack Compose and unidirectional state flow.

## Layer boundaries

- `ui`: Compose screens, reusable components, navigation and ViewModels.
- `domain`: file, duplicate, transfer and scan use cases. Added from Phase 2.
- `data`: unified storage repositories, Room metadata cache and operation log.
- `platform`: phone file APIs, MediaStore, SAF USB and optional Shizuku adapters.
- `worker`: resilient transfer and scan work coordinated through WorkManager.

The UI will only consume a unified internal file model. Direct phone paths,
SAF document URIs and Shizuku-backed paths remain implementation details of
their storage adapters.

## Safety invariants

1. A source is never deleted until the destination has been fully verified.
2. Similar matches are never represented as exact duplicates.
3. Duplicate discovery never deletes automatically.
4. Shizuku is optional; normal phone, MediaStore and SAF modes remain usable.
5. USB disconnect keeps source data intact and leaves work recoverable.
6. Manual copy/move always asks the user to choose the destination folder;
   Smart Auto-Sync rules are the only operations allowed to remember a target.
