# ChatGPT Winner Plan — Implementation Map

## Implemented now (Trust Foundation)
- [x] Original working Shizuku + SAF core preserved
- [x] Download Completion Guard
- [x] Source Mutation Guard
- [x] Persistent Transfer Journal
- [x] Single active transfer
- [x] Temporary destination `.part`
- [x] Size + readability verification
- [x] Cleanup Delay (5 min default)
- [x] Cleanup Revalidation
- [x] USB disconnect-safe source preservation
- [x] Crash/interruption journal recovery
- [x] Boot receiver / safe recovery start
- [x] USB free-space + reserve check
- [x] Best-effort FAT32 >4GB preflight
- [x] Duplicate safe rename
- [x] Same Video Duplicate Guard using content confirmation
- [x] Auto Safety Escalation
- [x] Nagram source layout guard
- [x] Live progress / speed / ETA
- [x] SHAHADAT profile + user photo

## Next after real-device torture test
- [ ] Full 4-tab Home / Activity / Files / Settings UI
- [ ] Full USB Profiles manager + Preferred USB UI
- [ ] Dedicated Existing File Migration Session UI
- [ ] Full safety presets UI: Archive / Safe Cleanup / Storage Saver
- [ ] Folder mapping and file filters
- [ ] Full Bangla / English switch
- [ ] System / Light / Dark / AMOLED theme selector
- [ ] Privacy mode across activity/export
- [ ] Rich diagnostics and support report
- [ ] Quick Settings tile / widget (later)

## Mandatory torture tests before expanding scope
- USB unplug at 1%, 50%, 99%
- USB unplug during verification
- USB unplug during cleanup delay
- app kill during transfer
- app kill during verification
- phone reboot with cleanup pending
- Shizuku off / permission lost
- USB full
- FAT32 large file
- same filename different file
- same MB different video
- same video different filename
- rename while downloading
- source mutation during transfer
- duplicate already present on USB

Acceptance rule: **Zero incorrect source deletion.**
