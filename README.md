# MedRoute Tracker Android

Android Studio project that turns the supplied HTML tracker into a real Android application.

## What is included

- WebView-based Android app with the original scheduling and shift logic preserved
- Real Android geolocation permission flow and live location capture
- Native PDF export from the Android app
- Native HTML report export from the Android app
- Phone dial intent and map intent integration
- Local persistence through WebView DOM storage / localStorage
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact

## Main project structure

- `app/src/main/assets/index.html` — adapted version of the original tracker HTML
- `app/src/main/java/com/artem/medtracker/MainActivity.kt` — WebView host, permissions, dialogs
- `app/src/main/java/com/artem/medtracker/AndroidBridge.kt` — JavaScript bridge for reports, maps, phone, and location refresh
- `app/src/main/java/com/artem/medtracker/ReportPdfGenerator.kt` — native PDF generator
- `.github/workflows/android-debug.yml` — cloud APK build on GitHub Actions

## How to use with GitHub

1. Create an empty GitHub repository.
2. Upload the contents of this folder into the repository.
3. Open the repository on GitHub.
4. Go to **Actions** and run **Android Debug APK** manually, or just push to `main` / `master`.
5. Download the APK from the workflow artifacts.

## Notes

- The debug APK can be installed directly for testing.
- Geolocation requires runtime permission on the device.
- Generated reports are saved in the app documents directory and then shared through the Android share sheet.
- The adapted HTML keeps the original patient list, optimization, logs, report editor, PDF/HTML export buttons, maps, call button, theme switcher, and shift reset flow.
