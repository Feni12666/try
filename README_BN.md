# SHAHADAT PRO — Milestone 0 + A

এটি approved SHAHADAT PRO blueprint-এর প্রথম foundation build। বর্তমান V3 safety/transfer engine রেখে Kotlin + Jetpack Compose + Material 3 + MVVM + Room + WorkManager foundation যোগ করা হয়েছে।

**Data-safety rule:** package id, `bridge` preferences এবং `bridge_journal.db` অপরিবর্তিত রাখা হয়েছে।

**Build:** GitHub Actions → `SHAHADAT-PRO-M0-A-Test-APK` (Direct flavor).

দেখুন: `MILESTONE_0_A_STATUS.md` এবং `MILESTONE_0_MIGRATION_BN.md`।

---

# V3.0.3 Fast Queue

বর্তমান build: **3.0.3-fast-queue**। 1 active transfer-এর পেছনে সর্বোচ্চ 10টি file prepared queue-তে রাখা হয়, cleanup delay 3 মিনিট, এবং permanent GitHub debug signing secret ব্যবহার করা হয়।

# Nagram USB Bridge V3 Premium — SHAHADAT

এটি V2/Winner safety core-এর উপর Premium V3 UI build। Core transfer logic নতুন করে rewrite করা হয়নি।

## V2/Winner থেকে রাখা হয়েছে
- Shizuku দিয়ে Nagram restricted storage read
- SAF দিয়ে user-selected USB folder write
- Download Completion Guard
- Source Mutation Guard
- Persistent transfer journal
- Sequential single-file transfer
- temporary `.part` destination
- verification before cleanup
- 5-minute safety delay
- cleanup-time destination revalidation
- USB disconnect / crash recovery
- reboot recovery
- 1 GB free-space reserve default
- FAT32 large-file best-effort protection
- Auto Safety Escalation
- Same Video Duplicate Guard

## Duplicate rule
Filename বা rounded MB দিয়ে duplicate decide করা হয় না। Exact bytes candidate filter, content fingerprint, এবং duplicate candidate হলে SHA-256 confirmation ব্যবহার করা হয়। সন্দেহ হলে file skip/delete করা হয় না।

## V3 Premium UI
- AMOLED black Home dashboard
- SHAHADAT profile photo
- animated neon shield
- animated circular transfer progress
- live percent / MB/s / ETA
- Phone + Main USB storage cards
- Nagram / Shizuku / USB status
- Recent Activity
- Home / Activity / Files / Settings bottom navigation
- Android 15 / targetSdk 35 system-bar safe insets: bottom navigation ফোনের system navigation bar-এর উপরে থাকে
- compact-width tuning for ~360dp Android phones
- premium dark cards + subtle animations
- idle UI refresh throttling to reduce unnecessary battery work

## Build
GitHub Actions workflow push-এর পর debug APK build করবে।

**Golden rule:** Bridge নিশ্চিত না হলে original file রেখে দেবে।

## V3.0.1 Mobile Safety Polish
- Bottom navigation আর Android system navigation/gesture area-এর নিচে যাবে না।
- 360dp-class ফোনে hero/progress/storage/connection cards compact হয় যাতে text squeeze/overlap কমে।
- dead bell action বাদ: bell এখন Activity খুলে।
- Same Video Duplicate Guard UI থেকে disable করা যায় না; এটি V3-তে mandatory।
- cleanup-এর ঠিক আগে destination size/readability-এর সাথে content fingerprint আবার match করা হয়।
- repeated transfer errors-এ bounded retry backoff আছে; প্রতি 5 সেকেন্ডে endless retry করা হয় না।
- profile animation finite entrance animation; detached screen-এ infinite avatar animator leak নেই।
