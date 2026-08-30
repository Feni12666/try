# Nagram USB Bridge — Root ছাড়া Nagram → Pendrive Auto Move

এই project আপনার case-এর জন্য preconfigured:

- Nagram package: `xyz.nextalone.nagram`
- Source folders:
  - `/storage/emulated/0/Android/data/xyz.nextalone.nagram/files/videos`
  - `documents`
  - `images`
  - `audios`
  - `stories`
- Destination: app-এর মধ্যে Android folder picker দিয়ে Pendrive-এর **`ok`** folder একবার select করবেন।
- Root লাগে না। **Shizuku running** থাকতে হবে।
- Termux/rish app install হওয়ার পরে আর লাগবে না।

## কীভাবে কাজ করে

1. Shizuku UserService UID 2000 (`shell`) হিসেবে Nagram-এর external app data media folders পড়ে।
2. App file size + modified time তিনবার stable না হওয়া পর্যন্ত অপেক্ষা করে (প্রায় 9–12 sec)।
3. Stable হলে source file থেকে **সরাসরি** selected USB SAF folder-এ stream করে। আলাদা staging copy নেই।
4. exact byte count + source unchanged verify করে।
5. সব ঠিক থাকলেই Nagram-এর original file delete করে।
6. USB খুলে গেলে বা copy fail হলে original file delete হয় না। Partial destination file remove করার চেষ্টা করে।

## Build

সবচেয়ে সহজ: AndroidIDE বা Android Studio-তে এই folder open করে Gradle sync/build করুন। Project-এ `gradlew` আছে; প্রথম run-এ wrapper JAR না থাকলে `curl`/`wget` দিয়ে নিজে download করার চেষ্টা করবে।

Termux-এ Android SDK আগে থেকেই configured থাকলে root folder থেকে `./gradlew assembleDebug` চালানো যাবে। শুধু Termux + Java থাকলেই Android APK build হয় না; Android SDK/Build Tools-ও দরকার।

Project uses:
- compileSdk 35
- Java 17
- Shizuku API/provider 13.1.5
- Android Gradle Plugin 8.7.3

APK সাধারণত `app/build/outputs/apk/debug/app-debug.apk` বা release build folder-এ পাবেন।

## প্রথমবার Setup

1. Shizuku app চালু করুন।
2. Nagram USB Bridge install করে খুলুন।
3. **Grant Shizuku Permission** চাপুন → Allow.
4. **Choose Pendrive 'ok' Folder** চাপুন।
5. Android file picker-এ Pendrive (`2D43-C59B` বা USB storage) খুলে `ok` folder-এ গিয়ে **Use this folder** দিন।
6. আগে থেকে থাকা Nagram files-ও move করতে চাইলে checkbox tick করুন। না করলে শুধু service চালুর পরে নতুন/পরিবর্তিত downloads move হবে।
7. **START AUTO MOVE** চাপুন।
8. প্রথমে ছোট একটি video download করে test করুন।

## Samsung-এর জন্য

- Settings → Apps → Nagram USB Bridge → Battery → **Unrestricted** দিলে background reliability বাড়ে।
- Shizuku restart/phone reboot-এর পরে Shizuku আবার চালু করতে হবে, তারপর Bridge আবার Start করুন।
- Pendrive-এর UUID/path hard-code করা হয়নি; SAF folder permission ব্যবহার করা হয়েছে, তাই `/mnt/media_rw/...` write permission problem এড়ানো যায়।

## Safety behavior

- USB copy fail → source থাকে।
- source download চলাকালে size/mtime বদলালে transfer বাতিল করে পরে retry করে।
- same filename USB-তে থাকলে overwrite করে না; `name (1).ext`, `name (2).ext` করে।
- privileged service arbitrary files read/delete করতে পারে না; code Nagram-এর নির্দিষ্ট media folders-এ whitelist করা।

## Note

Nagram চলমান download-এর bytes USB-তে live-write করে না। Download complete/stable হওয়ার পর USB-তে direct stream করে। Root ছাড়া Android-এর storage restrictions-এর মধ্যে এটা safer এবং practical পদ্ধতি।
