# Nagram USB Bridge V3 Premium

## Base
V3 is built directly on the SHAHADAT Winner/V2 safety core. The transfer engine, Shizuku UserService, SAF destination flow, journal, duplicate guard, verification, cleanup delay, cleanup revalidation, crash recovery, reboot recovery, space reserve, and source mutation protection are preserved.

## Premium UI target
The Home screen follows `DESIGN_REFERENCE_V3.png`:
- AMOLED black dashboard
- SHAHADAT avatar/header
- animated neon safety shield
- animated circular transfer progress
- live filename, bytes, percent, speed and ETA
- Phone + Main USB storage meters
- Nagram / Shizuku / USB health row
- recent activity cards
- fixed Home / Activity / Files / Settings bottom navigation, system-bar inset safe

## Functional tabs
- Home: live bridge state, transfer progress, storage, connection state, recent activity
- Activity: journal history and safety events
- Files: USB destination, safe test, existing-file migration, duplicate guard explanation
- Settings: Shizuku, automation, boot recovery, safe cleanup, duplicate guard, diagnostics, SHAHADAT profile

## Animation
- screen fade/slide transition
- profile avatar entrance animation (finite; no detached-view infinite animator)
- pulsing safety shield and glow rings
- animated circular transfer percentage
- animated linear progress/storage meters

## Safety invariant
If the Bridge is unsure, it keeps the original.


## Mobile-safe polish (3.0.1)
- Android 15 edge-to-edge/system navigation overlap protection
- compact 360dp layout sizing
- bottom nav side/bottom spacing
- idle refresh throttling
- mandatory Same Video Duplicate Guard
- cleanup-time destination fingerprint revalidation
- bounded reason-aware retry backoff
