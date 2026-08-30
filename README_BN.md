# Nagram USB Bridge — SHAHADAT Winner Base

এই project-টি প্রথমে যে working Nagram USB Bridge দিয়ে real Android phone-এ Nagram → USB transfer সফল হয়েছিল, **সেই original project-কে base করে rebuild করা হয়েছে**। Package ID একই রাখা হয়েছে: `com.nagram.usbbridge`।

## Profile
- Name: **SHAHADAT**
- Profile photo: user-provided original image included as `profile_shahadat.jpg`

## Core working model preserved
- Root লাগে না
- Shizuku দিয়ে Nagram external app storage read করা হয়
- SAF দিয়ে user-selected USB/Pendrive folder write করা হয়
- Foreground auto-move service
- Existing package/source path preserved

## ChatGPT Winner safety plan — implemented in this base
- Download Completion Guard
- Source Mutation Guard
- Persistent SQLite transfer journal
- Single sequential transfer queue
- Temporary `.part` destination
- Verification before cleanup
- Default 5-minute Cleanup Delay
- Cleanup Revalidation before source delete
- USB disconnect / crash-safe behaviour
- Boot recovery support
- 1 GB default USB reserve
- Best-effort FAT32 / large-file compatibility preflight
- Auto Safety Escalation (repeated safety failures suspend cleanup)
- Nagram source layout guard
- Same Video Duplicate Guard

## Same Video Duplicate Guard
Filename দিয়ে duplicate decide করা হয় না।

Flow:
1. Exact file size in bytes
2. Quick content fingerprint (sampled SHA-256)
3. Candidate match হলে full SHA-256 confirmation
4. Full content match হলেই duplicate skip

তাই একই video নাম বদলে download হলেও আবার USB-তে copy হবে না। শুধু একই displayed MB হলে duplicate ধরা হবে না।

## Safe Cleanup flow
`Detected → Stable → Preflight → Duplicate Check → Transfer to .part → Verify → Finalize → 5 min Cleanup Pending → Destination Revalidate → Source Delete`

Golden rule:
> **If uncertain, keep the original.**

## Important build fixes included
- AndroidX enabled
- AIDL enabled
- AndroidX annotation dependency
- Shizuku Provider manifest entry
- Shizuku UserService-compatible AIDL implementation
- GitHub Actions APK workflow

## Version
- versionCode: 10
- versionName: `3.0.0-winner-base`

## Build
Push this project to GitHub. `.github/workflows/build-apk.yml` builds the debug APK automatically.

## Current scope
এটি **winner-plan safety foundation + initial dashboard/profile build**। Full future Home/Activity/Files/Settings multi-screen product, USB profile manager, Migration planner, advanced filters, Bangla/English switch, scheduler, widget ইত্যাদি stability testing-এর পরে ধাপে ধাপে যোগ করা হবে। Core safety logic আগে test করা হবে।
