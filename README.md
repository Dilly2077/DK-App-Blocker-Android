# DK App Blocker — Android

A local-first Android app blocker with schedule, daily usage limit, location/geofence and manual blocking rules. It also includes focus sessions, local insights and a Strict Mode PIN.

## Version 0.3.1

The 0.3.1 update fixes the Accessibility setup banner and adds custom Quick Focus hours/minutes, a live countdown with days, actual session apps and accurate elapsed-time progress. See [0.3.1 implementation and verification](RELEASE_NOTES_0.3.1.md). Builds and tests run locally; physical-device qualification remains pending.

### Previous audits

See [release notes](RELEASE_NOTES_0.3.0.md) and [audit disposition and verification](AUDIT_RESULTS_0.3.0.md) for changes, test coverage, recovery and remaining device limitations.

The [second-audit testing build](SECOND_AUDIT_RESULTS_0.3.0.md) keeps version 0.3.0 and raises the Android version code to 4. It adds blocked-pane overlays that preserve recovery access, independent password checks for strict-rule breaks, durable saves and shared daily-usage samples. Physical-device qualification is still pending; this remains a testing release.

- Select installed launchable apps to block.
- Scheduled rules, including overnight schedules and selectable weekdays.
- Daily combined-use limits using Android Usage Access.
- Location rules using Android/Google Play Services geofences.
- Manual always-on rules.
- Custom Quick Focus sessions, including short sessions and durations longer than 24 hours.
- Accessibility-based foreground app interception and a dedicated block screen.
- Strict rules with optional 5-minute breaks and a PIN challenge.
- Local screen-time list and 7-day blocked-attempt chart.
- Local-only configuration export to JSON clipboard.
- No account, ads, analytics SDK, telemetry API or subscription code.

## Android limitations

Quick Focus persists through navigation, screen locking and process restarts using a durable elapsed-time deadline. Changing the wall clock does not extend it. A reboot safely ends Quick Focus, temporary breaks and timer Strict Mode; independent persistent rules continue. The next-session app selection is retained. End an active session before changing its duration or apps; starting another cannot replace or extend it. Strict Mode restrictions still apply to manual start/end, while natural expiry always releases the temporary focus block.

An existing 0.3.0 focus session is migrated without extending its deadline. Since 0.3.0 did not store its original duration, the progress baseline resets once to the remaining duration at upgrade. Accessibility permission and Usage Access are displayed separately; enabled permission alone is not proof of successful enforcement on every Android/OEM configuration.

A normal third-party Android app cannot make itself literally impossible to uninstall or fully control system UI. This project does not claim device-owner powers it does not have. Recents/system-level lockout is not implemented because it is not reliably enforceable by an ordinary app across Android versions.

Location rules require Location permission; for reliable background geofence transitions on modern Android, set Location to **Allow all the time** in the app's system permissions.

## Install / APK

Builds run locally on the PC hosting this workspace. Download [0.3.1's locally built APK](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/download/v0.3.1-local/DK-App-Blocker-0.3.1-local.apk) from the [0.3.1 testing release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.1-local). Use `DK-App-Blocker-0.3.1-local.apk` for normal installation. That release also includes the unsigned release validation build, test/audit companion APKs, checksums and verification records. Device qualification remains pending. The [second-audit 0.3.0 build](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.0-local-audit2) and [original local build](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.0-local) remain available for history. Unsigned release APKs require signing; instrumentation APKs are not the normal app.

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
