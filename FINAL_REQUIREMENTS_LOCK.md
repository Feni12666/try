# Frozen Storage Behaviour

1. App is manual-first. No fixed Nagram source and no automatic destination.
2. Source and destination must always be selected by the user for manual Copy/Move.
3. Phone and USB have clear dedicated browsing sections.
4. Shizuku is optional and exists for user-selected Android/data / Android/obb access.
5. Auto-Sync, when implemented, is OFF by default and requires an explicit user-created source + target rule.
6. Manual Copy never deletes source.
7. Manual Move uses Copy -> Verify -> Cleanup Delay -> Destination Revalidation -> Source Delete.
8. Cleanup delay options: 1 / 3 / 5 minutes, default 3.
9. Same content should not be copied twice. Filename is not a duplicate identity.
10. 2 concurrent active transfers are allowed; pending user-selected items form the ready queue.
11. Theme options: System / Light / Dark / AMOLED.
12. SHAHADAT name and photo appear only on splash; not in normal app screens.
