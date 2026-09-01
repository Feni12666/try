# Migration Safety

- Package ID remains `com.nagram.usbbridge`.
- Version code is increased for every test build.
- GitHub workflow uses the permanent test signing key secret.
- If the currently installed legacy APK has a different signature, Android will reject direct update. Do not uninstall until current app data has been backed up.
- Existing USB files are never deleted by installation/update.
