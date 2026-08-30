# Nagram USB Bridge V3.0.3 — Fast Queue

এই build-এর লক্ষ্য: transfer safety না কমিয়ে file-to-file waiting gap কমানো।

## Core changes
- Cleanup delay: 5 min → 3 min (one-time migration when old value is exactly 5 min)
- Scanner: 5 sec → 2 sec
- Download Completion Guard: 3 unchanged observations ≈ 6 sec
- 1 active USB transfer + up to 10 prepared files
- Scanner transfer চলার সময়ও future files prepare করে
- Queue full থাকলে active USB write-এর সময় full Nagram rescan skip করে bandwidth/battery বাঁচায়
- Prepared file-এর quick fingerprint background-এ করা হয়
- Full SHA-256 only when duplicate candidate exists
- Active transfer শেষ হলে next prepared file immediately starts
- USB write buffer: 4 MiB buffered PFD output
- UI progress update throttled to ~1.5 sec
- Cleanup revalidation scan throttled to 10 sec so it does not hammer USB during large writes
- Home screen shows “N ready next”

## Safety unchanged
- .part destination
- Source Mutation Guard
- exact size/readability verification
- finalization before cleanup eligibility
- cleanup-time destination identity revalidation
- Same Video Duplicate Guard always on
- uncertain state => original source stays

## Permanent update signing
GitHub workflow now requires secret `DEBUG_KEYSTORE_B64` and restores the same debug keystore on every build. This prevents future APK update signature mismatch as long as the secret/keystore is preserved.
