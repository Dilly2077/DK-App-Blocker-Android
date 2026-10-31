# DK App Blocker 0.5.0 · least-privilege application blocking

Android version code 7. Based on development commit `228a04ae7392cd01e1a0a9d898ca02602f4f5bbf` (v0.4.0), on `codex/0.5.0-least-privilege`. The v0.4.0 UI, saved preferences, credentials, rules and sessions are preserved. There is no Banking Mode or financial-package exclusion list in production.

## Accessibility audit and exact changes

| Area | Previous implementation | New implementation |
| --- | --- | --- |
| Identity | Read `window.root.packageName` for each application window | Associate selected packages with window IDs from transition events; never request roots, sources, anchors, titles, text, descriptions, child nodes or field values |
| Idle subscriptions | Both window event types, all packages, interactive-window retrieval | `TYPE_WINDOW_STATE_CHANGED`, explicit selected packages plus this app; System UI/launcher identities only for active Strict Mode navigation hints |
| Active-target subscriptions | Always broad | Temporarily include structural window events and all package boundary events only while a selected window ID is tracked, so switches, removals and occlusion can clear protection. Unrelated events contribute identity only, not UI content |
| Interactive-window flag | Always requested | Enabled only while a selected-window candidate exists; cleared when candidates leave or selection changes |
| Periodic work | Every two seconds inspect every window root even without targets | Every two seconds refresh policy/expiry. No window queries without a selected candidate; conditional geometry checks preserve expiry and unchanged-window enforcement |
| Surfaces | Automatically launch a full blocking activity for single-window apps | Automatically draw only inside positively identified selected panes, clipped against unknown/other apps, system regions and system bars. Full DK blocking activity opens only after the user's Review block action |
| Window placement | Included `FLAG_LAYOUT_NO_LIMITS` | Removed; bounded, non-focusable, non-touch-modal surfaces remain |
| Policy races | Package identity obtained during each root scan | Window IDs are transient and invalidated on unrelated reuse, removal, policy change, interruption and service destruction. Boundary events clear surfaces before re-evaluation; stale async results are discarded |
| Additional guard | Service responsible for selecting panes | Overlay renderer also checks the current explicit selection before drawing; only actually shown selected panes generate attempt history |
| Framework node cache | Default | Cache disabling requested using the public API on Android 13/API 33 and later; no node retrieval on older versions either |
| Daily usage enforcement | Retained transitions/classes for all packages in its sample | Only selected daily-limit package transitions/classes are accumulated. Legacy single-resume boundaries use an identity-free marker for another app's resume |
| Device Administrator | Optional, but configuration default suggested it | Off by default for new/unconfigured settings; existing explicit choices/active roles preserved. Still manual opt-in with an empty policy list and visible manual removal. Never needed for rules, authentication or Strict Mode |

No new Android permissions, gestures, key interception, view-ID reporting, content-change events, screenshots, cross-display inspection, device-owner privileges or network access are requested. The initial XML package filter is DK's own package; runtime filtering uses the actual application ID, including the separate audit build. The Accessibility description and Settings/About explain the scope and limits.

### Capability deliberately retained

`android:canRetrieveWindowContent="true"` remains declared. Android requires it, together with `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, for the window-ID/type/bounds API used to keep a blocked pane off another app in split-screen. Retaining this framework capability does **not** mean this implementation reads nodes: production contains no root/source/title/text retrieval calls, and regression tests make those calls fatal. Removing the declaration would prevent safe pane geometry retrieval and silently weaken existing split-screen protection.

The service remains registered, enabled when the user enables it, and detectable. A bank may reject the enabled service or its declared capability regardless of event filtering or actual data access. The app does not conceal the service, spoof identity/signatures/install sources, hook other apps, interfere with Play Integrity, or automatically disable/re-enable security permissions.

Primary platform references: [Android window-access requirements](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getWindows()), [dynamic package filters](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#packageNames), [structural window events](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent#TYPE_WINDOWS_CHANGED). Android applies one package filter across the event types; the temporary boundary scope is therefore necessary rather than a claim that structural events have a separate filter. See the [Android event-dispatch implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java).

## Generic protection for unrelated applications

Targets are the union of enabled rule selections and a live Quick Focus session, excluding recovery packages. This is a positive selection policy for every package, including future or unknown financial apps; there is no HSBC-specific branch. Outside that set, RuleEngine returns unblocked, no target identity is cached, no blocking activity is automatically launched, and the overlay renderer refuses the package. While a selected pane is visible, unknown window rectangles are treated as protected regions. The service never needs their app's screen content or account details.

State is limited to selected package/window IDs and timestamps in RAM, transient geometry, and existing selected-app attempt history. Events themselves, unrelated package histories and banking screen content are not retained or exported. Insights remains a separate user-facing feature using Android's aggregated usage statistics after Usage Access is granted; those statistics can include unrelated apps' usage totals, but are not Accessibility screen content.

## Retained capabilities and explicit trade-offs

- Quick Focus/custom countdowns, schedules, daily limits, geofences, manual rules, independent overlapping rules, Strict Mode, passwords/biometrics, break verification, appearance customisation, Insights and local data remain.
- Ordinary switches and split-screen protection use selected event IDs and bounded geometry. If a window's identity is missing or ambiguous, the app avoids drawing over it.
- After service startup/restart/interruption, or adding/enabling an already-visible app whose ID was not observed, **reopen that app** to supply a fresh window-state event. Android does not expose a reliable window package name without a content-root read. Window IDs are deliberately not persisted across a service restart or reboot. This privacy trade-off is visible in Settings; newly unknown windows are not blindly covered.
- Known selected windows remain tracked during breaks or inactive triggers, so timer/break expiry and schedule/location/daily-limit changes still enforce without switching windows. Policy/expiry checks retain the two-second service interval and five-second usage sample cache.
- Automatic full-screen activity interception has been replaced by selected-pane surfaces. **Review block** opens the existing full blocking screen, including password/biometric break controls. Home/system recovery remains accessible and overlays do not take keyboard focus.
- Secure apps or OEMs may withhold events/window metadata, hide overlays or refuse enabled Accessibility/Administrator services. This update does not override their policy and cannot guarantee universal banking compatibility.
- Administrator access does not prevent uninstalling. It remains optional and manually removable; no existing active role is silently removed. No real-bank evidence establishes whether Administrator access is the cause of a specific compatibility failure.
- Reboot recovery for temporary focus/break/Strict Mode timers is unchanged; persistent rules and user data remain.

## Local verification and device status

All compilation/testing is on this PC with JDK 17, SDK/build tools 35 and Gradle 8.10.2. `AGENTS.md` remains local-only. No GitHub Actions/cloud build is authorised or dispatched.

Full command: `.tools/build.ps1 :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease :compat-fixture:assembleDebug :compat-fixture:lint --offline --stacktrace`.

| Check | Result |
| --- | --- |
| Normal debug/release compilation and instrumentation compilation | Passed |
| Debug local JVM/Robolectric/Compose tests | 144 passed, zero failures/errors/skips |
| Release local JVM/Robolectric/Compose tests | 144 passed, zero failures/errors/skips |
| Android debug/release lint | Passed; zero errors, one existing pre-API-30 resource lookup warning per variant |
| Three synthetic fixture debug APKs | Built locally |
| All three fixture debug flavor lint tasks | Passed; zero findings |
| Isolated audit app/instrumentation compilation | Passed locally |
| Normal/audit/fixture package validation | Passed without installing or changing a device |
| All eight APKs | Metadata and ZIP alignment verified; seven signed APKs have valid v2 signatures and the preserved local certificate |

Normal install APK: `dist/0.5.0/DK-App-Blocker-0.5.0-local.apk`, signed debug, package `com.dk.appblocker`, version 0.5.0/code 7, min SDK 26/target SDK 35, no Internet permission.

SHA-256: `d7dfa1bb3e64960cdc0def68b48b6ddd3e6e0788d7b36776324c601b29436a80`.

Signing certificate SHA-256: `289c31bf44d99f805cb901bc2c1c7f9e99c19291a8c4574fcb605764a62cc512`, matching earlier local builds. `SHA256SUMS.txt` includes every APK. Release assets are downloaded and compared against the local files before delivery. The verification archive includes successful JUnit XML/build/lint results, compiled service metadata and APK inspection output.

The fixture manifest explicitly disables backup/transfer, and all fixture strings/icons are local resources. No dependency update or stronger product privilege was needed. Existing deprecated Android/icon compiler warnings remain. Tests include unrelated finance samples (including an unknown future package), selected social alongside a focused finance pane, overlap priority, rapid window-ID reuse, fresh selection/subscription changes, connected preference observation, permission lifecycle, Strict Mode/admin independence, service restart/reboot, focus expiry, own-overlay feedback and defensive overlay rejection. Custom Android test shadows throw on root, title, anchor, source, text, description or unrelated class access. Existing v0.4.0 regression suites remain.

Three separate local fixture APKs represent two finance apps and a distraction app. They contain synthetic fields, no bank SDK, no network permission and no real account. Device regression tests and the Windows runner support these packages, using only the isolated audit installation. Passing fixtures is not proof of compatibility with a real bank.

No Android device is connected. Firmware virtualization and the hypervisor are disabled, and the earlier software-emulator attempt did not boot. Physical-device installation, actual package-filter delivery/geometry timing, secure-window handling, Google Play geofences, biometric hardware, real banks and OEM/Android 17 behaviour remain unperformed. No device or PC security setting was changed. Use the signed local APK for normal installation; instrumentation/audit/fixture APKs are testing artifacts, and release-unsigned needs signing.

The [0.5.0 testing release](https://github.com/Dilly2077/DK-App-Blocker-Android/releases/tag/v0.5.0-local) hosts the locally built files and checksums. The signing certificate is preserved from earlier local builds.
