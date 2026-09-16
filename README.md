# DK App Blocker — Android

A local-first Android app blocker with schedule, daily usage limit, location/geofence and manual blocking rules. It also includes focus sessions, local insights and a Strict Mode PIN.

## What works in v0.1

- Select installed launchable apps to block.
- Scheduled rules, including overnight schedules and selectable weekdays.
- Daily combined-use limits using Android Usage Access.
- Location rules using Android/Google Play Services geofences.
- Manual always-on rules.
- Quick 25/45/60 minute focus sessions.
- Accessibility-based foreground app interception and a dedicated block screen.
- Strict rules with optional 5-minute breaks and a PIN challenge.
- Local screen-time list and 7-day blocked-attempt chart.
- Local-only configuration export to JSON clipboard.
- No account, ads, analytics SDK, telemetry API or subscription code.

## Android limitations

A normal third-party Android app cannot make itself literally impossible to uninstall or fully control system UI. This project does not claim device-owner powers it does not have. Recents/system-level lockout is not implemented because it is not reliably enforceable by an ordinary app across Android versions.

Location rules require Location permission; for reliable background geofence transitions on modern Android, set Location to **Allow all the time** in the app's system permissions.

## Install / APK

Every push to `main` runs `.github/workflows/android.yml` and builds a debug APK. In GitHub, open **Actions → Build Android APK → latest successful run → Artifacts** and download `DK-App-Blocker-debug-apk`.

The artifact ZIP contains `app-debug.apk`, which can be sideloaded on Android after enabling installation from the browser/file manager you use to open it.

## First-run setup

1. Open **Settings** inside the app.
2. Enable **Accessibility** for DK App Blocker. This is required for enforcement.
3. Grant **Usage Access** if you want daily limits and screen-time insights.
4. Grant **Location** only if you create location-based rules.
5. Create rules under the **Rules** tab.

## Privacy

All app data is stored locally in Android `SharedPreferences`. This v0.1 does not contain network calls, analytics, advertising or account infrastructure.
