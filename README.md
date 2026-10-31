# DK App Blocker — Android

A local-first Android app blocker with schedule, daily usage limit, location/geofence and manual blocking rules. It also includes focus sessions, local insights and a Strict Mode PIN.

## Version 0.5.0

The 0.5.0 update replaces Accessibility root inspection with selected-package/window identities and bounded surfaces. Idle monitoring is restricted to selected packages; unrelated app content is never requested. This improves least-privilege behaviour without a Banking Mode or a bank-package whitelist. Android still requires a content-retrieval capability declaration for split-screen window bounds, so financial apps can still detect/refuse the service. See [exact changes, tests and limits](RELEASE_NOTES_0.5.0.md). The complete v0.4.0 UI, blocking features and local data are preserved. Builds run locally; real-bank and physical-device qualification remain pending.

### Previous audits

The [0.4.0 update](RELEASE_NOTES_0.4.0.md) introduced the focus-first dashboard, rule management, durable Strict Mode drafts, safer geofence reconciliation and modular UI.

The [0.3.1 update](RELEASE_NOTES_0.3.1.md) fixed the Accessibility setup banner and added custom hours/minutes, live countdowns, session apps and elapsed-time progress.

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
- Day/Week/Month insights with OS-reported screen time and accessible recorded-attempt charts.
- Rule filters and confirmed deletion/duplication, with Strict Mode checks on every mutation.
- Settings grouped by protection, permissions, appearance, data and about.
- Local-only configuration export to JSON clipboard.
- No account, ads, analytics SDK, telemetry API or subscription code.

## Android limitations

Accessibility uses selected package/window identities and rectangles, never UI roots or text. After enabling/restarting the service or adding an already-visible app, reopen that app to provide a fresh window event. Unknown identities are not blindly covered. Automatic protection is bounded to selected panes; **Review block** opens authentication and break controls. Banks can still detect/refuse the enabled service and its window-access capability. There is no compatibility switch or security-check bypass.

Quick Focus persists through navigation, screen locking and process restarts using a durable elapsed-time deadline. Changing the wall clock does not extend it. A reboot safely ends Quick Focus, temporary breaks and timer Strict Mode; independent persistent rules continue. The next-session app selection is retained. End an active session before changing its duration or apps; starting another cannot replace or extend it. Strict Mode restrictions still apply to manual start/end, while natural expiry always releases the temporary focus block.

An existing 0.3.0 focus session is migrated without extending its deadline. Since 0.3.0 did not store its original duration, the progress baseline resets once to the remaining duration at upgrade. Accessibility permission and Usage Access are displayed separately; enabled permission alone is not proof of successful enforcement on every Android/OEM configuration.

A normal third-party Android app cannot make itself literally impossible to uninstall or fully control system UI. This project does not claim device-owner powers it does not have. Recents/system-level lockout is not implemented because it is not reliably enforceable by an ordinary app across Android versions.

Location rules require Location permission; for reliable background geofence transitions on modern Android, set Location to **Allow all the time** in the app's system permissions.

## Install / APK

Builds run locally on the PC hosting this workspace. Download [0.5.0's locally built APK](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/download/v0.5.0-local/DK-App-Blocker-0.5.0-local.apk) from the [0.5.0 testing release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.5.0-local). Use `DK-App-Blocker-0.5.0-local.apk` for normal installation. Other assets include the unsigned release validation build, instrumentation/audit APKs, clearly labelled synthetic test fixtures, checksums and verification records. Fixture APKs are not banking applications. Real-bank and device qualification remain pending. [0.4.0](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.4.0-local), [0.3.1](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.1-local) and older builds remain available. Unsigned release APKs require signing; instrumentation APKs are not the normal app.

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

Build the synthetic finance/social helpers locally with `:compat-fixture:assembleDebug`; lint each debug flavor with `:compat-fixture:lintFinanceOneDebug :compat-fixture:lintFinanceTwoDebug :compat-fixture:lintSocialDebug`. Later isolated device tests can use `scripts/device-tests.ps1 -FixtureDirectory compat-fixture/build/outputs/apk` along with its required SDK/APK/serial arguments. The runner validates all three package IDs before installing. These fixtures and unit tests do not establish compatibility with any real bank.
