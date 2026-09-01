# Milestone 0 + A — Blue/White Final UI Foundation

## Milestone 0 — Data & Signing Safety
- package id remains `com.nagram.usbbridge` for legacy data compatibility.
- SharedPreferences remain `bridge`; legacy journal remains `bridge_journal.db`.
- permanent GitHub test signing secret `DEBUG_KEYSTORE_B64` is supported.
- Direct and Play Store-oriented Gradle flavors remain separated.
- default verified cleanup delay is 3 minutes; user may select 1/3/5 minutes.
- duplicate guard remains mandatory for automatic sync.

## Milestone A — Approved UI Foundation
- Kotlin + Jetpack Compose + Material 3.
- final Design #8 direction: blue + white premium mobile UI.
- personal image + `SHAHADAT` only on animated startup splash.
- after splash, personal name is not displayed in app UI.
- Home / Files / Videos / Duplicates / Sync bottom navigation.
- Phone vs USB/SSD is clearly separated in Files.
- duplicate comparison layout includes side-by-side preview/specification cards and Keep Newest / Keep Oldest / Delete Selected positions.
- Shizuku and Smart Sync are dedicated on Sync screen.
- cleanup delay has a designed 1 / 3 / 5 minute selector.

## Transfer core carried forward
- up to 2 active transfers in normal mode.
- up to 10 prepared files in look-ahead queue.
- automatic 1-transfer Safe Mode after repeated safety failures.
- content lock prevents concurrent matching-content candidates from knowingly being written twice.
- source mutation, temporary destination, verification and cleanup revalidation safeguards remain.
