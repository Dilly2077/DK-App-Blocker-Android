# DK App Blocker — Android

A local-first Android app blocker with schedule, daily usage limit, location/geofence and manual blocking rules. It also includes focus sessions, local insights and a Strict Mode PIN.

## Version 0.3.0

See [release notes](RELEASE_NOTES_0.3.0.md) and [audit disposition and verification](AUDIT_RESULTS_0.3.0.md) for changes, test coverage, recovery and remaining device limitations.

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

Builds run locally on the PC hosting this workspace. Download locally built APKs from [GitHub Releases](https://github.com/Dilly2077/DK-App-Blocker-Android/releases). The [0.3.0 local-build release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.0-local) contains the installable debug app, the unsigned release validation APK, the instrumentation-only test APK and SHA-256 checksums. Use `DK-App-Blocker-0.3.0-local.apk` for normal installation. The unsigned release APK requires signing; the test APK is not the app.

The signed debug APK can be sideloaded after enabling installation from the browser/file manager used to open it. Reuse the same local signing key for future compatible updates. A differently signed previous build can require uninstalling first, deleting its local data.

Automatic push and pull-request builds are disabled. The retained GitHub Actions workflow is manual-only and requires explicit user authorisation for a cloud-build exception. Creating a Release and uploading already-built APKs does not trigger this workflow. The tracked [AGENTS.md](AGENTS.md) makes the local-build policy permanent for future sessions in this repository. Historical Actions reports remain evidence of earlier verification, not the current build procedure.

## First-run setup

1. Open **Settings** inside the app.
2. Enable **Accessibility** for DK App Blocker. This is required for enforcement.
3. Grant **Usage Access** if you want daily limits and screen-time insights.
4. Grant **Location** only if you create location-based rules.
5. Create rules under the **Rules** tab.

## Privacy

All app data is stored locally in Android `SharedPreferences`; cloud backup and device transfer are disabled. Passwords use salted PBKDF2. Configuration export excludes credentials and history. The product has no Internet permission, analytics, advertising or account infrastructure. Location rules use the device's Google Play services location APIs.

Build with JDK 17 and Android SDK 35: `./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease`. The release variant is unsigned; distribute the verified debug artifact for sideloading.
