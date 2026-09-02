# Approved UI contract

## Primary navigation

The Material 3 bottom navigation is always rendered above Android's system
navigation inset. Primary destinations are Home, Files, Videos, Duplicates and
Transfer. Player and Shizuku are focused secondary destinations with an
explicit back action.

## Confirmed design requirements

1. **Phone and USB switch:** Files shows two full-width, labeled storage options
   (`Phone` and `USB / Pendrive`) with icon, selected border and check mark.
2. **Duplicate comparison:** Two files are displayed side by side with name,
   location, Size, Duration and Modified time. Actions are `Keep Newest`,
   `Keep Oldest` and `Delete selected copy`, followed by confirmation.
3. **Dedicated advanced options:** Shizuku has a focused access screen; Smart
   Auto-Sync has a dedicated Transfer destination with source, target,
   non-duplicate rule, verification rule and USB-connect automation.
4. **Manual copy and move:** Every manual copy/move action opens a folder
   chooser for both the source and destination context. The app never assumes
   or silently fixes a destination for a manual operation.

## Visual system

- Premium Material 3 blue-and-white palette in light mode.
- Deep navy surfaces with vibrant blue actions in dark mode.
- Icy-gray cards, high-contrast type, mint success states and violet Shizuku
  accents keep each feature legible without weakening the blue hierarchy.

## Motion baseline

- Bottom destination transition: 180–220 ms fade/shared-axis slide.
- Storage switch: 160–220 ms crossfade.
- Permission surfaces and future sheets: 280 ms emphasized easing.
- Transfer progress: continuous progress with verified completion state.
- Player gestures: 10-second feedback ripple and short control fade.

All animations must respect the system Reduce Motion preference.
