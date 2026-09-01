# Build Notes

Version: `4.0.0-alpha02-bluewhite` (versionCode 17)

GitHub Actions workflow: `.github/workflows/build-apk.yml`

Required repository secret:
- `DEBUG_KEYSTORE_B64` — permanent test signing keystore already used by the previous migration workflow.

Build task used by CI:
`gradle --no-daemon --stacktrace :app:assembleDirectDebug`

Expected artifact:
`app/build/outputs/apk/direct/debug/app-direct-debug.apk`
