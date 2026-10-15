# Second audit — 0.3.0 testing build, version code 4

The supplied second audit reviewed application code through `fbe2c03`. The later repository policy commit changes the development workflow: all new compilation and testing is local. No GitHub Actions run is authorised or used for these changes.

## Findings and disposition

| Finding | Implementation and verification |
| --- | --- |
| Settings-focused split-screen bypass | The Accessibility service now checks all visible application windows, even when a recovery app is focused. In multi-window layouts it places opaque, touch-consuming Accessibility overlays only on blocked window regions, subtracting recovery windows and system-bar/cutout regions. Recovery retains keyboard focus and touch access. The normal full-screen blocker remains for a single blocked app. Pure geometry tests cover overlapping/fully covering recovery regions and 500 generated layouts. A Robolectric test runs the actual service with Settings focused and a blocked neighbour, asserts overlay bounds and removal when Settings becomes fullscreen. An additional actual split-screen instrumentation test is compiled, with on-device execution pending. |
| Rule-level Strict block allowed unauthenticated breaks | A strict rule independently requires the saved password when global Strict Mode is off. Active global Strict Mode can impose its password, biometric or timer policy. Legacy strict rules without a password do not grant free breaks; the screen explains how to configure one. The editor names the control “Require password for breaks”, explains independence, and prevents saving new strict-break rules without a credential. Unit tests exercise the gate; a compiled device regression checks wrong/correct passwords with global mode off. |
| Repeated usage-history scans / CPU | A synchronised five-second usage cache shares one OS event query across all daily-limit rules/windows, invalidating at day/boot changes and permission loss. Window switches still trigger immediate checks; the periodic window scan is now every two seconds. A unit test confirms 100 same-sample consumers use one query. An instrumented 20-decisions × 20-rules CPU/query profiler is compiled; it has not been run on a device and no battery-life measurement is claimed. |
| Long sessions, missing events and multi-window usage | Adds ACTIVITY_STOPPED closure, package-level background events without a class name, and single-resume inference on Android 8/9 when a pause is absent. Android 10+ retains concurrent resumed activities and merges overlapping activities per package. Tests cover long sessions clipped to the day, concurrent packages, missing pause/background class, orphan events and empty history. Completely missing OS history cannot be reconstructed; limits may undercount if Android discards the needed events. |
| Rule/appearance persistence after saving | Saves use synchronous durable commits; editors perform them on an IO dispatcher and acknowledge success only after commit, reporting storage failure. Focus deadlines/packages and break deadline/policy tokens are committed atomically. A local test verifies the XML file contains each save immediately when the call returns. Instrumentation includes separate save phases that immediately kill only the test app, then verify from a new process; actual execution remains pending. |
| App-name toggle ambiguity | Renamed to “Show app-name heading” and explicitly explains that `{app}` in the custom message is independent. Users can remove that token to omit the name from message text. Pane overlays also honour appearance settings. |
| Android 17 / Pixel 8 Pro | Deferred by the user. The isolated audit build installs as `com.dk.appblocker.audit` with the launcher label “DK App Blocker Audit”, preserving the normal blocker and its data. The PowerShell runner validates package IDs before installation and refuses to reboot physical phones. |
| Earlier Actions/artifact confirmation | Earlier successful run and original locally built Release assets were verified in the previous handoff. Those results are historical and do not verify the changed build. Current delivery is a separately labelled local testing release, with checksum/signature checks and the pending device qualifications below. |

## Local verification

Run the normal local suite with JDK 17, Android SDK 35 and Gradle 8.10.2:

```powershell
& .tools/build.ps1 :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease --offline
```

The expanded local suite passed 47 unit/Robolectric tests per variant (94 executions, zero failures or skips), including actual-service and on-disk persistence regressions. Debug/release compilation, instrumentation APK compilation and both lint variants passed (zero errors, 17 warnings per variant: dependency updates and the documented pre-API-30 system-dimension lookup). The added instrumentation tests are not counted as executed tests. The isolated audit app/test APKs also compile locally. Package preflight succeeds for the isolated APKs and rejects normal app APKs before any device changes.

The normal debug APK's v2 signature is valid and matches the signing certificate of the previous local Release APK, with unchanged package `com.dk.appblocker` and increased version code 4. The product APK has no Internet permission. The isolated APK has the distinct package `com.dk.appblocker.audit`, also version code 4. These signature/metadata checks establish packaging compatibility, not a successful installation of the changed APK on a device.

Daily-limit crossings can lag by roughly seven seconds (a five-second shared usage sample plus a two-second periodic scan); window transitions still request prompt re-evaluation. This is an explicit responsiveness/query-cost trade-off. No physical battery improvement or measured on-device CPU result is claimed.

A workspace-local Android 8 image/emulator was installed and tried with software emulation. The PC reports firmware virtualisation disabled and no installed emulator hypervisor driver; the software emulator remained offline and could not execute tests. Only the two processes created for this attempt were stopped. Windows virtualisation settings were not changed and no cloud fallback was used. No Pixel was connected; the user chose phone testing later.

## Physical-device follow-up

Build the isolated audit APKs locally:

```powershell
& .tools/build.ps1 -PisolatedDeviceAudit=true :app:assembleDebug :app:assembleDebugAndroidTest --offline
.\scripts\device-tests.ps1 -SdkRoot .tools/android-sdk -ApkDirectory .tools/device-audit-build/outputs/apk -Serial YOUR_DEVICE_SERIAL -VerifyPackagesOnly
.\scripts\device-tests.ps1 -SdkRoot .tools/android-sdk -ApkDirectory .tools/device-audit-build/outputs/apk -Serial YOUR_DEVICE_SERIAL
```

Normal instrumentation clears its target app's preferences. The isolated package prevents that from affecting the user's normal installed blocker. The script never reboots a phone; reboot phases are allowed only for an emulator with `-AllowEmulatorReboot`. The automated split-screen entry point targets Android 8–11. On Android 17, manually enter split screen on the Pixel, leave Settings focused beside a blocked app, verify only the blocked pane is covered, rotate/resize, open notification shade and permission prompts, use Home/Back, remove administrator access, and test strict-rule breaks with global Strict Mode off. Also qualify GPS/geofence delivery, biometric prompts and battery/CPU behaviour on the physical device.

This remains a testing release. Local unit/service tests do not establish production readiness or prove the new overlay behaviour on Android 17 hardware.
