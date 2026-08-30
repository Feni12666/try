# GitHub Actions দিয়ে APK বানানোর সহজ নিয়ম

এই project-এ `.github/workflows/build-apk.yml` আগে থেকেই দেওয়া আছে। GitHub repository-তে project-এর files upload/push করলেই APK cloud-এ build করা যাবে। AndroidIDE দরকার নেই।

## সবচেয়ে সহজ flow

1. GitHub-এ একটি নতুন repository বানান।
2. এই project-এর **ভেতরের সব file/folder** repository-র root-এ upload/push করুন।
   - `app/`
   - `.github/`
   - `build.gradle`
   - `settings.gradle`
   - `gradle.properties`
   - অন্যান্য files
3. GitHub repository → **Actions** tab খুলুন।
4. **Build Android APK** workflow নির্বাচন করুন।
5. **Run workflow** → আবার **Run workflow** চাপুন।
6. Build শেষ হলে workflow run খুলুন।
7. নিচে **Artifacts** section থেকে **Nagram-USB-Bridge-APK** download করুন।
8. ZIP খুললে `app-debug.apk` পাবেন। এটিই install করবেন।

## গুরুত্বপূর্ণ

- GitHub-এ শুধু `Nagram_USB_Bridge_GitHub_Ready.zip` file upload করলে build হবে না। ZIP extract করে **ভেতরের project files** repository-তে থাকতে হবে।
- Workflow Java 17 + Gradle 8.9 ব্যবহার করে এবং `:app:assembleDebug` চালায়।
- APK path: `app/build/outputs/apk/debug/app-debug.apk`
- প্রথম build-এ dependencies download হয়, তাই কয়েক মিনিট লাগতে পারে।

## Build fail করলে

GitHub Actions-এর লাল ❌ run খুলে `Build debug APK` step-এর শেষ error lines copy করে ChatGPT-তে দিন।
