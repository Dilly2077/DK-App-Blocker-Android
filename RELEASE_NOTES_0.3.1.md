# DK App Blocker 0.3.1 (version code 5)

## Changes

- Accessibility service names are canonicalised with `ComponentName.unflattenFromString`, recognising full and shortened names without matching other services. The Android enabled-service API provides a fallback when the secure service list is unavailable. An explicit revocation or disabled master switch wins over potentially stale manager data.
- Home and Profile refresh permission status on resume and Accessibility settings changes, with a visible-screen polling fallback. Initial state is read synchronously, avoiding a temporary false setup banner. Usage Access has a separate notice for daily limits and insights. Profile no longer implies every feature is ready.
- Quick Focus accepts whole hours and minutes, including minutes greater than 59 and durations longer than a day. Zero, negatives, fractional values, invalid characters, multiplication/addition overflow and deadlines outside the numeric range are rejected, never silently clamped.
- Active focus shows a second-by-second `HH:MM:SS` countdown (with days when needed), an accurate progress ring/bar, app count, actual package/label list and an explicit End Focus control. Its progress is independent of ordinary rules.
- A single, synchronously committed JSON session contains the immutable app list, unique ID, original duration, elapsed start, elapsed deadline and boot count. Simultaneous/repeated starts cannot replace or extend an active session. End it first; Strict Mode still gates manual changes. App selection for the next session is stored separately and retained after expiry.
- UI ticks pause when the activity is not resumed. Resume and recreation read the persisted deadline rather than reconstructing time from ticks. Elapsed time includes screen-off sleep. Wall-clock metadata is not used to decide whether focus is active.
- Natural expiry clears active focus independently of Strict Mode and leaves schedule, manual, daily-limit and location decisions intact. Existing block-screen rechecks (one second) and Accessibility window rechecks (two seconds, plus window events) release the app or pane without requiring a window switch.
- A reboot safely expires temporary focus, as in 0.3.0. Persistent rules and saved next-session selections remain. Legacy active sessions migrate without extending their elapsed deadline; their original duration was not available, so progress starts from the remaining duration once at upgrade.
- Generic break/authentication/edit-lock deadline caps and Strict Mode timer limits remain separate from the new long-focus duration path. Recovery apps remain accessible. Green/black styling and the local-only architecture are preserved.

## Main files

| File | Change |
| --- | --- |
| `AndroidUtils.kt`, `VisibleState.kt` | Canonical service matching, permission refresh and lifecycle-aware UI ticks |
| `FocusSession.kt` | Validated duration parsing, countdown formatting and monotonic session/progress model |
| `Prefs.kt`, `RuleEngine.kt` | Atomic durable sessions, migration, duplicate protection, strict gates, automatic expiry and consistent rule evaluation |
| `MainActivity.kt` | Custom Quick Focus controls, real countdown/progress/apps, separate setup notices |
| `app/build.gradle.kts` | 0.3.1/code 5 and Compose/Robolectric UI test dependencies |
| JVM/UI and device tests, local device runners | Permission, focus, expiry, process/reboot and immediate-save regressions |
| `.gitignore`, `README.md` | Local Kotlin cache exclusion and current behaviour/build documentation |

## Local verification

All compilation ran on this PC using JDK 17.0.20.1, SDK/platform and build tools 35, Gradle 8.10.2 and offline local caches. No GitHub Actions/cloud builds were used. `AGENTS.md` already permanently enforces this policy and requires no change. Source is retained on `codex/0.3.1-quick-focus-permissions`; main is unchanged.

Command: `.tools/build.ps1 :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease --offline --stacktrace`

| Check | Result |
| --- | --- |
| Normal debug/release APKs and instrumentation compilation | Passed |
| Debug JVM/Robolectric/Compose tests | 80 passed, 0 failures/errors/skips |
| Release JVM/Robolectric/Compose tests | 80 passed, 0 failures/errors/skips |
| Android debug/release lint | Passed; 0 errors, 2 existing warnings per variant |
| Separate audit app and instrumentation compilation (`-PisolatedDeviceAudit=true`) | Passed |
| Audit runner package validation (`-VerifyPackagesOnly`) | Passed; no device changes |
| APK metadata and ZIP alignment, all five APKs | Passed |
| Signatures, four signed APKs | Valid APK Signature Scheme v2; same local certificate |
| Normal product manifest | `com.dk.appblocker`, version 0.3.1/code 5, min SDK 26/target SDK 35, no Internet permission |
| Device instrumentation / installation | Not run; no device connected |

The two existing lint warnings concern a newer available Play Services Location version and the Accessibility service's pre-API-30 system-bar resource lookup. Kotlin also reports existing deprecated Android/icon APIs; these are warnings, not build failures.

New tests cover component canonicalisation, grants/revocations/restoration, manager fallback, Home banner visibility and separate Usage Access, immediate resume refresh, suspended background ticks, duration validation and overflow, countdown seconds/days, progress percentages, short/long sessions, activity recreation, context restoration, selection durability, concurrent starts, strict recovery, safe reboot, screen-off deep sleep, wall/elapsed divergence, legacy migration and expiry with independent rules. The actual Accessibility service's pane overlay is tested without changing its app windows. Device regressions additionally cover automatic dismissal of a real blocking activity, long-session process/reboot persistence and immediate process termination after a focus save; those require later device execution.

Normal install APK on this PC: `dist/0.3.1/DK-App-Blocker-0.3.1-local.apk`.

SHA-256: `0b9a6981b4e26fa5ba595fc065225d27fd99f5e4600fa45d64dc9bb1dc58ab00`.

Signing certificate SHA-256: `289c31bf44d99f805cb901bc2c1c7f9e99c19291a8c4574fcb605764a62cc512`.

The [GitHub testing release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.3.1-local) hosts the APKs already built locally, checksums and verification records. No workflow is dispatched by publishing it. The verification ZIP contains JUnit results, lint reports, local build logs and APK inspection output. See `SHA256SUMS.txt` for every APK's checksum.

## Remaining qualification

No usable local Android device is connected. Phone testing was explicitly deferred by the user. This PC's firmware virtualization is disabled, so the previous software-emulator attempt could not boot. Device instrumentation is compiled, but fresh real-device installation, process-kill/reboot, permission binding, OEM split-screen and Pixel/Android 17 qualification must not be described as passing.

Robolectric exercises production Compose screens, preferences, rule evaluation and Accessibility pane overlays. Wall-clock divergence is simulated through deadline metadata while elapsed time stays fixed; tests do not modify the PC's clock. These local tests complement device tests and do not establish OEM behaviour.

The distributable local APK is debug-signed. It supports compatible updates from earlier APKs signed by this PC's same key. The unsigned release APK is a build-validation output and cannot be installed until signed. Instrumentation APKs are test companions; the isolated audit app uses `com.dk.appblocker.audit` to preserve the normal installed app's data.
