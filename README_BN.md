# SHAHADAT PRO — Blue/White Premium Test Build

এটি approved Final Architecture-এর Phase 0 + Phase 1 এবং existing safe-transfer core-এর updated test source।

## Final design lock
- Design #8: নীল + সাদা premium mobile UI।
- Startup splash-এ শুধু ব্যবহারকারীর ছবি + `SHAHADAT`।
- Splash শেষ হলে app UI-র কোথাও personal name দেখানো হয় না।
- Bottom navigation: Home / Files / Videos / Duplicates / Sync।

## Transfer behavior
- Normal mode: একসাথে সর্বোচ্চ 2 active transfer।
- Background look-ahead: পরের সর্বোচ্চ 10টি completed file prepare থাকে।
- repeated safety failure হলে automatic 1-transfer Safe Mode।
- Same displayed MB দিয়ে duplicate সিদ্ধান্ত হয় না।
- duplicate confirmation: bytes + quick fingerprint + candidate-only SHA-256।
- Copy → Verify → Finalize → 1/3/5 min delay → Destination Revalidate → Source Delete।
- Default cleanup delay 3 min।

## গুরুত্বপূর্ণ
এই package-এ final product-এর architecture/UI এবং proven Smart Sync safety core আছে। Full file-manager, production Media3 player, exact-duplicate management engine এবং similar-video deep scan milestone অনুযায়ী পরের builds-এ সম্পূর্ণ হবে।
