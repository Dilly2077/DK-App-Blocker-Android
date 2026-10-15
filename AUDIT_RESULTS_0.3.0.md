# Version 0.3.0 audit disposition

The supplied `AUDIT_AND_CODEX_HANDOFF.md` was missing from the repository and has been copied into its root unchanged. It is supporting audit material; the user's development request defines the scope.

All Kotlin sources, resources, manifests, Gradle configuration, workflow and README were reviewed. No accounts, telemetry, server or product Internet permission were introduced.

| Report area | Disposition and evidence |
| --- | --- |
| Foreground transitions / duplicate launches / rapid switches | Event-type filtering plus visible-window checks, monotonic launch throttling and 30-second per-package event deduplication. Device tests use a separate APK as a real blocked app. |
| Home / Back / Recents / notification shade / recovery | Home and Back exit the blocking screen. Recovery packages are unconditionally exempt, even if configured in stored rules. Instrumentation exercises repeated launches, exits, Settings, shade and Recents. Recents/split-control hints are explicitly best effort. |
| Split screen | Inspect all visible application windows, not only the focused app; recovery windows remain usable. OEM split controls and hardware multi-window interaction remain a manual qualification item. |
| Strict authentication | PBKDF2, constant-time comparisons, successful legacy migration, verified password replacement, failure throttling, strong-biometric enrolment checks and password fallback. Unit/Robolectric credential tests. |
| Timer / process death / reboot / clock changes | Persisted elapsed deadlines plus boot count. Temporary locks expire on reboot for recovery. Pure tests simulate clock independence. Separate instrumentation processes verify persisted state and an actual emulator reboot verifies recovery deadlines and surviving manual rules/appearance. OEM physical power-cycle behaviour still requires hardware qualification. |
| Backup / export | Backup disabled; cloud and device-transfer exclusion rules; export excludes credentials and history; product APK permissions checked by CI. |
| Overnight / weekdays / limits | Boundary tests include Sunday→Monday, overnight ownership, equal-time full days and exact daily limit thresholds. Usage tests cover midnight, repeated resumes, multiple activities, combined packages and screen-off. Usage Access is checked before enforcement. |
| Location / permissions | Geofence enter/exit occupancy and revoked-permission policy tests; registration state persists across process restart, resets at boot, serialises updates, reports async failures, caps registration at Android's 100-rule maximum. GPS/GMS delivery timing needs physical testing. |
| Overlap / breaks | Most restrictive matching rule wins; policy-scoped break tokens invalidate when rules or Strict Mode change. Policy/persistence tests and on-device break expiry check. |
| Swipe navigation / nested controls | Compose HorizontalPager replaces ancestor drag consumption. Navigation test runs in both CI emulator configurations (API 26 three-button, API 35 gestural). Sliders and horizontal rule-day lists are outside the pager in the editor. |
| Custom appearance | Persistence test and real rendered customised block-screen device test; screenshot artifacts. Scrollable editors and valid hex colour checks. |
| Build / release | Debug and unsigned release compilation, both unit-test variants, debug/release lint and test APK compilation. Workflow validates APK signature/manifest, installs the exact candidate APK and publishes only after both device-test jobs pass. |

Run `./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease` with JDK 17 and Android SDK 35. GitHub Actions retains unit/lint reports and device logs/screenshots alongside the verified APK. The final handoff identifies the actual commit and run/artifact URLs; workflow configuration alone is not evidence of a successful publication.

Local verification on 2026-10-15: debug and unsigned release compiled; 33 unit/Robolectric tests passed on each variant (66 executions, zero failures/skips); debug and release lint passed with zero errors and 16 dependency-update warnings each. `apksigner verify` accepted the debug APK's v2 signature; `aapt dump badging` confirmed package `com.dk.appblocker`, version `0.3.0`/code `3`, minimum SDK 26 and target SDK 35. The packaged permission list contains no Internet permission. Device installation and instrumentation are required CI gates rather than inferred from those static checks.

Dependency-update warnings are reviewed separately from correctness errors: the existing compatible Android/Compose stack is pinned for this release. The narrow `ProtectedPermissions` lint exception is limited to Usage Access, which Android grants through its app-op settings screen. Synchronous preference commits are intentional for durable credentials and deadlines; password KDF work runs on a background dispatcher.
