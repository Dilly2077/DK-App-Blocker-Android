# DK App Blocker 0.3.0

- Retains the green-and-black interface, schedules, combined daily limits, manual rules, location rules, quick focus, insights, Strict Mode, custom block screens and local export.
- Rechecks visible applications every second so schedule boundaries, daily limits and expired breaks enforce without another app switch. Uses foreground window transitions, duplicate-launch throttling and bounded attempt history.
- Combines all matching rules. A no-break rule takes precedence; breaks are tied to the exact rule/focus/Strict Mode configuration rather than exempting all future rules for a package.
- Home, Android Settings, permission controllers, the notification shade, installers and emergency dialling remain available for recovery. The app picker excludes recovery packages. Strict Mode offers an explicit Accessibility recovery shortcut and administrator removal even while active. The old blanket Settings interception becomes a confirmation on in-app Settings shortcuts.
- Replaces unsalted hashes with randomly salted PBKDF2-HMAC-SHA256 (600,000 iterations), constant-time verification, successful-login migration, verified password replacement and a persistent 30-second delay after five failures. Credential work runs off the UI thread. Biometrics require enrolment and a recovery password.
- Temporary Strict Mode, focus, cooldown and break deadlines use elapsed time, persist across process death, ignore wall-clock changes and expire safely on reboot. Existing legacy Strict Mode timers migrate with a bounded remaining duration; legacy focus/break timestamps expire on upgrade.
- Corrects overnight weekday boundaries and usage accounting across midnight, repeated resume events, overlapping activities and screen-off/shutdown events. Schedule rules follow the current local time and timezone by design.
- Reconciles geofences after reboot and permission changes; serialises updates, preserves registrations across process restarts, clears stale occupancy and displays permission/registration errors. Both precise and background location are required on Android 10+.
- Uses a Compose pager for tab swipes so child scroll/slider gestures participate normally. Makes settings and blocking content scrollable on small screens. Replaces the placeholder Insights chart with real local blocked-attempt counts.
- Disables cloud backup/device transfer of app data. Configuration export omits password hashes and history. The product manifest has no Internet permission.
- Adds a checksum-pinned Gradle wrapper, unit/Robolectric tests, instrumented navigation and external-app blocking tests. CI verifies signatures and installs the exact candidate on API 26 and 35 before publishing `DK-App-Blocker-debug-apk`.

## Android limitations and recovery

Ordinary Device Administrator access cannot prevent uninstalling. Remove administrator access in Android Settings → Security → Device admin apps, disable DK App Blocker's Accessibility service, then uninstall normally. Settings and Home are always exempt. Biometric users can use their recovery password; timer users can wait or reboot. Safe mode remains an Android recovery route.

Blocking is an Accessibility-based intervention, not device-owner/kiosk enforcement. An app can be briefly visible before interception. Android/OEM restrictions, revoked permissions, force-stop, safe mode, ADB and system UI can bypass it. Recents/split-control detection is best effort; ordinary apps cannot guarantee control of those surfaces. Visible application windows are also checked to reduce split-screen bypass, while recovery windows remain exempt.

Google Play services geofences can arrive minutes late and need background location, enabled location services and working Google Play services. Real-world location accuracy, OEM battery policies, hardware biometric prompts and the full range of OEM split-screen behaviour require physical-device testing. These are not implied by passing emulator/policy tests.

The published APK is debug-signed and intended for sideloading, not Play Store release. CI debug keys can change between builds; Android may require uninstalling a previously installed debug build signed with another key, which deletes local data. The unsigned release variant is compiled for validation but is not the distributed APK.
