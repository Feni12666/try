# Technical Notes — Blue/White Foundation

## Existing protected-source bridge
The current proven automation source is Nagram package `xyz.nextalone.nagram`. Source access uses a Shizuku UserService; destination access uses SAF. The final product architecture is broader and will also support normal user-selected phone folders.

## Transfer safety
Cross-storage move is non-destructive until the destination passes all required gates:
1. source stable/unchanged check
2. free-space and compatibility preflight
3. temporary destination write
4. destination size/readability verification
5. destination finalization
6. selected cleanup delay (1/3/5 min; default 3)
7. cleanup-time destination fingerprint revalidation
8. source unchanged revalidation
9. source delete

Any uncertainty preserves the source.

## Queue
Normal mode allows up to 2 active transfers and maintains up to 10 prepared files. Repeated safety failures reduce new-transfer capacity to 1. The prepared queue keeps scanning while transfers run so file-to-file idle gaps are minimized.

## Anti-duplicate sync
Filename and rounded MB are never trusted as identity. Quick fingerprinting is used for candidate filtering; full SHA-256 is computed only for candidates requiring confirmation. Concurrent same-content candidates are serialized by an in-memory content key so two workers do not race to create the same content.

## Storage architecture
- Direct flavor: may request MANAGE_EXTERNAL_STORAGE for full shared-storage file-manager use.
- USB/SSD: SAF tree URI only.
- Play Store flavor: scoped-storage-oriented manifest; policy-specific permission strategy remains release work.

## UI architecture
Kotlin + Compose + Material 3 + ViewModel/StateFlow. Room and WorkManager foundations coexist with the existing foreground BridgeService so long-running byte transfers are not delegated to periodic WorkManager jobs.
