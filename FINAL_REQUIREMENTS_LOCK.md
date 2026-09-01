# SHAHADAT PRO — Frozen Product Requirements

This source package follows the final user-approved direction.

## Branding and UI
- Personal name `SHAHADAT` and the supplied profile image appear only on the animated startup splash.
- After splash, the app UI does not display the personal name.
- Final visual direction: Design #8 — premium blue + white, clean light surfaces, subtle motion, mobile-first.
- Bottom navigation: Home, Files, Videos, Duplicates, Sync.
- Bottom navigation respects Android navigation-bar insets.

## Transfer safety invariants
- Cross-storage move: Copy → Verify → Finalize → Cleanup Delay → Destination Revalidation → Source Delete.
- Cleanup Delay is user-selectable: 1 / 3 / 5 minutes; default 3 minutes.
- If destination identity cannot be proven, the original is preserved.
- USB disconnect, verification failure, source mutation, journal uncertainty, or wrong destination must never trigger source deletion.

## Queue and performance
- Normal mode: up to 2 active transfers.
- Look-ahead queue: up to 10 prepared files while active transfers continue.
- Safety escalation: repeated safety failures reduce active transfer capacity to 1.
- Download completion guard uses stable observations before queue eligibility.

## Anti-duplicate sync
- Filename is not an identity signal.
- Same displayed MB alone never causes skip/delete.
- Candidate pipeline: exact bytes → duration/metadata when available → quick content fingerprint → full SHA-256 confirmation.
- A confirmed identical destination copy may be skipped; source cleanup still follows the selected verified-cleanup policy.
- Concurrent same-content candidates are serialized so two active transfer workers cannot knowingly write the same content twice.

## Planned product modules
- Full Phone + USB file manager: browse, grid/list, copy, move, rename, delete, create folder, search/sort, multi-select.
- All Video Folders index for Phone | USB | All.
- Media3 in-app player with ±10s seek, fullscreen, brightness/volume gestures.
- Exact Duplicate groups with side-by-side preview/specifications and Keep Newest / Keep Oldest / Delete Selected.
- Optional Similar Video deep scan using frame/perceptual fingerprints.
- Optional Shizuku restricted-source integration.
- Smart Auto-Sync with anti-duplicate destination checking.
- Room cache/index, foreground transfer service + WorkManager reconciliation.
- Direct build and Play Store-oriented build flavors.
