# Video & Storage Pro — Manual File Manager Alpha 03

এটি SHAHADAT PRO project-এর manual-first test build source।

## এই build-এর সবচেয়ে বড় পরিবর্তন
পুরোনো fixed Nagram watcher / automatic destination পুরোপুরি বাদ। App নিজে কোনো folder থেকে auto-copy শুরু করবে না।

ব্যবহারকারী নিজে:
1. Files থেকে source file/folder select করবে।
2. Copy বা Move চাপবে।
3. Phone / USB / Shizuku restricted destination নিজে বেছে folder browse করবে।
4. Current folder-কে destination হিসেবে confirm করবে।

## Working features
- Phone shared storage browser
- USB / SSD SAF browser
- Android/data + Android/obb optional Shizuku browser
- List / Grid
- Multi-select
- Create folder / Rename / Delete
- Manual Copy / Move
- Keep Both / Replace / Skip
- 2 active transfers + up to 10 reported ready
- Foreground notification progress + Pause / Resume / Cancel
- Move safety: Copy -> SHA-256 Verify -> 1/3/5 min delay -> Revalidate -> Source Delete
- Default cleanup delay: 3 min
- Same-size destination candidate full SHA-256 duplicate check
- Light / Dark / AMOLED / System theme
- SHAHADAT name/photo visible only in splash screen

## Not finished yet
- All Video Folders index
- Media3 in-app player
- Full duplicate grouping/comparison scanner
- Similar Video deep scan
- User-created optional Auto-Sync rules

## Build
GitHub Actions workflow builds `:app:assembleDirectDebug` with the repository permanent test signing key secret `DEBUG_KEYSTORE_B64`.
