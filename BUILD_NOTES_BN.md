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


## V3 Premium
MainActivity premium dashboard-এ redesign করা হয়েছে। PremiumViews.java animated shield ও circular transfer progress দেয়। Core BridgeService safety logic Winner/V2 থেকে preserved.

## V3.0.1 Mobile-safe correction
- versionCode 13 / versionName 3.0.1-premium-mobile-safe
- Android 15 system bar inset protection
- compact phone layout polish
- mandatory duplicate guard
- cleanup fingerprint revalidation
- bounded retry backoff

GitHub Actions compile success না হওয়া পর্যন্ত APK-কে final verified build ধরা হবে না।
