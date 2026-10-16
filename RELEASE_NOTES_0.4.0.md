# DK App Blocker 0.4.0 · version code 6

Built from `codex/0.3.1-quick-focus-permissions` at `4c83224bc16488282fe03feada78f42056172716`, on development branch `codex/0.4.0-audit-ui`. The older main branch was not merged over this work. Existing preference keys and saved rules, credentials, appearance and focus sessions are preserved.

## Changes

- Home is a focus-first dashboard with compact protection status, a circular live timer, custom hours/minutes, compact app icons and an expandable app list. “See all” opens Rules. Enabled permission and a connected Accessibility service are checked separately; an unavailable service is not presented as enforcing rules. Quick Focus explicitly reports timer-only behaviour when blocking is unavailable. Usage Access is prominent on Home only when an enabled daily-limit rule needs it.
- Rules have All/Enabled/Active/Disabled filters, type icons, app counts and distinct trigger/enforcement status. Overflow menus provide Edit, Duplicate and confirmed Delete. Duplicates retain configuration but get a fresh UUID. Every mutation rechecks Strict Mode/cooldown at persistence time, including changes attempted while a confirmation dialog is open.
- Existing-rule Back returns to Rules; new-rule Back returns to type selection. Pager navigation, editor drafts, selected Insights period, Strict Mode drafts and active focus survive activity recreation. The main navigation is now Home, Rules, Insights and Settings.
- Rule changes run off the UI thread. Geofence reconciliation is requested only after durable success. Failed commits restore the previous preference values in memory, preventing an unconfirmed policy from appearing saved or being used by enforcement. This also covers focus and appearance writes.
- Strict Mode reads confirmed settings before presenting controls. Sliders and switches edit a visibly unsaved draft; Save commits them once. Activation, reconfiguration and verified disable only update confirmed UI state on durable success. Disable and its cooldown are one transaction. Natural expiry remains recoverable if storage fails, and explicitly shows “cleanup not saved” instead of claiming a persisted disable. A stale draft cannot overwrite a newer recovery password. Password/biometric checks and recovery access remain available.
- Location reconciliation preserves unchanged registrations and occupancy, adds replacements before deleting obsolete regions where capacity permits, and removes only obsolete regions first when Android's 100-region limit requires it. Geometry-specific event IDs reject late entry/exit callbacks. Deletion removes occupancy and queues registration cleanup in the same durable policy write. Registration failures remain retryable, platform event errors invalidate metadata for actual re-registration, permission revocation clears location occupancy, and reboot remains recoverable. Async operations have a timeout.
- Insights shows Today/Last 7 days/Last 30 days correctly, uses unambiguous weekday/date labels, accessible recorded-attempt charts and OS-reported usage bars, and explains empty/unavailable/aggregated data. Recorded attempts remain deduplicated within 30 seconds per app.
- Settings is organised into Protection, Permissions, Appearance, Data and About. The blocking screen has a compact responsive layout and retains custom text/colours, password verification, biometrics, breaks and Home recovery. Shared green/black tokens also style selection chips and controls; large-text navigation and scrolling layouts are exercised locally.
- `MainActivity.kt` is reduced to activity setup. Navigation, Home, Quick Focus, Rules, Rule Editor, Insights, Settings, Strict Mode and shared theme/components live in separate files. Persistence/policy helpers are separated into `RuleRepository`, `RuleStatus`, `GeofenceReconciliation` and `InsightPeriods`. The working Accessibility interception/pane engine was preserved; only service-liveness reporting was added.

## Local verification

All builds and tests run on this PC using JDK 17.0.20.1, Gradle 8.10.2, Android platform/build tools 35 and offline caches. GitHub Actions/cloud compilation is prohibited by the unchanged `AGENTS.md`; no workflow is dispatched. The retained workflow has only a manual trigger.

Full command: `.tools/build.ps1 :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease --offline --stacktrace`.

| Check | Result |
| --- | --- |
| Debug and release compilation, normal instrumentation compilation | Passed |
| Debug JVM/Robolectric/Compose/native-render tests | 123 passed; 0 failures/errors/skips |
| Release JVM/Robolectric/Compose/native-render tests | 123 passed; 0 failures/errors/skips |
| Android debug/release lint | Passed; 0 errors, 2 existing warnings per variant |
| Local native-rendered UI previews | 9 previews produced, inspected; includes small/large-text and landscape layouts |

The two existing lint warnings concern a newer available Play Services Location version and the pre-API-30 Accessibility system-bar resource lookup. Existing compiler deprecation warnings remain. The geofence call now includes an explicit runtime permission guard recognised by lint, and handles permission changes during async registration.

Normal APK: `dist/0.4.0/DK-App-Blocker-0.4.0-local.apk`.

SHA-256: `0163ce95f645c14428f5ca1a34fff0a5998b975b5c64348f6cf3298346bfbbf3`.

Signing certificate SHA-256: `289c31bf44d99f805cb901bc2c1c7f9e99c19291a8c4574fcb605764a62cc512` (unchanged from previous local builds).

The uploaded verification archive contains JUnit XML, lint reports, build logs and APK inspection output. `SHA256SUMS.txt` covers every locally built APK; uploaded files are downloaded and compared before delivery. All five APKs are inspected for metadata and ZIP alignment. The four signed APKs use valid APK Signature Scheme v2. The isolated audit app and instrumentation compile locally and their package IDs are checked without installing anything. Device installation is not claimed.

New regressions cover navigation/back, duplicate/delete confirmation, all rule mutations under Strict Mode, memory-changing commit failures, failed Strict Mode activation/disable/reconfiguration, credential preservation, atomic cooldowns, geofence updates/failures/deletion/stale callbacks/capacity, trigger versus enforcement status, permission relevance, focus timer-only status, period labels/charts, activity recreation, 320dp screens, 1.6× font scale and landscape scrolling. Existing expiry, authentication, usage, split-pane and recovery tests remain in the suite.

## Previews and device qualification

The `ui-previews` archive contains **Robolectric native-rendered previews of production Compose components using Android API 35**, with sample fixture data. They were visually inspected. They are not screenshots from an installed APK, an emulator, or the user's phone.

No Android device is connected. Firmware virtualization and the hypervisor are disabled on this PC, and the earlier software-emulator attempt did not boot. No PC setting was changed. Fresh installation, real gesture navigation, Google Play Services geofence transitions, biometric hardware, process-kill/reboot instrumentation, OEM split-screen and Pixel/Android 17 qualification remain unperformed. The device suite is compiled; a separate audit app avoids clearing the user's normal app data when testing later. Never run preference-clearing instrumentation against the personal installation.

## Downloads and remaining limitations

Use `DK-App-Blocker-0.4.0-local.apk` for normal sideload installation. It is debug-signed using the same certificate as earlier local builds, allowing compatible updates without clearing their data. Its package is `com.dk.appblocker`, min SDK 26, target SDK 35. `release-unsigned` is a build-validation artifact and requires signing before installation. `local-tests` is instrumentation-only. `isolated-audit` uses `com.dk.appblocker.audit`; its test companion is not the normal application.

- Focus, temporary breaks and timer Strict Mode still safely expire after a reboot. Persistent independent rules and the next-session app selection remain.
- Accessibility connection status is more accurate but cannot guarantee successful interception on every Android/OEM configuration. Periodic enforcement checks retain the existing timing; daily usage samples are cached for up to five seconds.
- Geofences depend on Android permissions and Google Play Services. Region updates can involve a short registration transition; unchanged occupancy is retained, changed geometry is not trusted, errors are visible, and obsolete IDs are ignored. Actual location transitions still need device qualification.
- Android's usage totals are OS aggregates; the charts do not present them as a precise event-by-event reconstruction. Recorded attempts reflect this app's observations and deduplication, not every interaction.
- Ordinary Device Administrator cannot make uninstalling impossible. Android Settings, permission controllers, Home and emergency recovery remain reachable. Recents/split-screen hints remain device dependent.
- Existing dependency/deprecated-API lint/compiler warnings are documented with the final results; they are not silently treated as errors or proof of device correctness.

The [GitHub testing release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.4.0-local) hosts APKs already built locally, SHA-256 sums, verification records and previews.
