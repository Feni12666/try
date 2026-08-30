# Build Notes

এই source GitHub Actions দিয়ে build করার জন্য ready করা হয়েছে।

Workflow: `.github/workflows/build-apk.yml`

Expected artifact:
`Nagram-USB-Bridge-APK/app-debug.apk`

Project rebuild lineage:
1. Original working Nagram USB Bridge base
2. Known successful build fixes restored (AndroidX, AIDL, Shizuku Provider)
3. ChatGPT winner safety plan layered on top
4. SHAHADAT profile + user-supplied photo added
5. Same Video Duplicate Guard added

GitHub-এ push করার পর build result real compiler দিয়ে verify করতে হবে। এই environment-এ Android SDK নেই, তাই local APK compile করা হয়নি।
