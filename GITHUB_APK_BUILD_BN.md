# GitHub Actions দিয়ে APK বানানো

এই source ZIP-এর ভেতরেই `.github/workflows/android.yml` আছে। তাই GitHub-এ
`main` branch-এ push হলেই Android CI debug APK build করবে।

Termux-এ ZIP Download folder-এ থাকার পর:

```bash
cd ~/nagram-usb
unzip -o /sdcard/Download/USB-Video-Manager-Phase3-Source.zip -d .
git add -A
git commit -m "USB Video Manager alpha 03"
git push
```

তারপর GitHub repository খুলে:

1. **Actions** খুলুন
2. নতুন **Android CI** run শেষ হওয়া পর্যন্ত অপেক্ষা করুন
3. run খুলে নিচের **Artifacts** থেকে `usb-video-manager-debug` download করুন
4. ZIP extract করে `app-debug.apk` install করুন

GitHub Actions প্রথমবার run হতে প্রায় 5–15 মিনিট লাগতে পারে। APK install করার
সময় Android-এর install permission দিতে হবে।

## বর্তমান functional scope

- Phone/USB browsing permission flow
- Optional read-only Shizuku protected-folder browsing
- User-selected source and target folder
- Smart Sync: SHA-256 duplicate skip, temporary copy, destination read-back
  verification, safe cancellation; source is never deleted

Manual copy/move, rename/delete, duplicate scanner and Media3 player এই source
checkpoint-এ এখনো শেষ নয়।
