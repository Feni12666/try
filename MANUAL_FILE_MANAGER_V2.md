# Manual File Manager V2

This build changes the storage behavior from a fixed Nagram watcher to a user-controlled file manager.

## Locked behavior
- No fixed Nagram source.
- No automatic destination.
- No boot-time auto-copy.
- Files screen browses Phone shared storage and the user-selected USB/SSD SAF tree.
- User manually selects source files/folders.
- Copy/Move opens a destination browser; destination storage and folder must be chosen manually.
- Copy never deletes the source.
- Move = copy -> SHA-256 verification -> 1/3/5 minute delay (3 default) -> destination revalidation -> source delete.
- Same-size destination candidates are full-hash checked; confirmed duplicate content is not copied twice.
- Same-name behavior: Keep Both / Replace / Skip.
- Up to 2 top-level transfers run concurrently; UI reports up to 10 ready items.
- Pause / Resume / Cancel are available from the Files screen and foreground notification.
- Light / Dark / AMOLED / System appearance options.
- SHAHADAT name and personal photo are visible only on the splash screen.

## Important current milestone limits
- Generic Shizuku browsing for Android/data and Android/obb is wired into Files when Shizuku is connected. It remains optional and no fixed-folder automation uses it.
- Videos tab, Media3 player, full duplicate-group scanner, and Similar Video deep scan remain later milestones.
