# DK App Blocker — audit and Codex handoff
Date: 2026-10-14

## Review scope
Static review of MainActivity.kt, BlockActivity.kt, BlockAccessibilityService.kt, Prefs.kt, Models.kt, StrictDeviceAdminReceiver.kt, AndroidManifest.xml, app/build.gradle.kts, and Android build workflow. Build, instrumentation, and APK validity have not been verified.

## Priority 0: Android blocking reliability and safety
- Accessibility service processes all accessibility event types. Limit handling to appropriate window/foreground transitions, prevent duplicate block screen launches, and test rapid switching.
- Verify that Home, Back, Recents, split screen, notification shade, reboot, and permission changes cannot cause trapping or unintended bypass.
- Avoid blanket blocking Android Settings and permission controllers. Provide a safe, recoverable path to disable administrator access. Ordinary apps cannot guarantee uninstall prevention.
- Run the Gradle debug build and tests, inspect errors, and verify a real installable APK.

## Priority 1: Authentication and persistence
- Prefs.hashPin uses unsalted SHA-256. Migrate to salted PBKDF2-HMAC-SHA256 (or another vetted password KDF), constant-time verification, and backwards-compatible legacy hash migration.
- Require verification of existing credentials before replacing a configured password.
- Test timer expiry, process restart, reboot, wall-clock changes, and authentication failures.
- Audit AndroidManifest allowBackup=true because SharedPreferences include hashes and activity history.

## Priority 1: Rules and UX
- Test overnight schedules, weekdays, daily usage limits, geofence transitions, overlapping blocking rules and break exemptions.
- Verify swipe navigation on gesture navigation and 3-button navigation; do not hijack sliders or horizontal lists.
- Verify custom blocking colours/message persist and render correctly.

## Priority 2: Release
- Version metadata currently says 0.3.0. Add unit/instrumentation tests, run lint, validate permissions, generate release notes.
- Confirm GitHub Actions uploads DK-App-Blocker-debug-apk with app-debug.apk; provide actual artifact URL.

## Acceptance criteria
A clean build, installable APK, verified blocked/allowed apps, correct strict authentication, predictable navigation, recovery route, passing tests and explicit remaining Android limitations.

## Codex task
Read this report first. Audit the full Android project. Fix findings in priority order while preserving the green-and-black UI and local-only privacy. Add tests, run Gradle assembleDebug and lint, inspect any CI failures, verify the APK and publish a usable artifact link. Do not overstate device administrator powers. Commit changes and report tests, limitations, and remaining risks.
