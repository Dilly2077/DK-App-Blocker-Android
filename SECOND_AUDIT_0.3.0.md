## DK App Blocker v0.3.0 — Second audit

I've located five new commits since the previous audit, with the latest at `fbe2c03` on 15 October 2026.

Codex has made substantial changes. Several problems from the first audit are now addressed in the source code.

| Previous finding                                         | Current assessment                                             |
| -------------------------------------------------------- | -------------------------------------------------------------- |
| Unsalted SHA-256 passwords                               | Fixed in code — salted PBKDF2-HMAC-SHA256, 600,000 iterations  |
| Existing password could be replaced without verification | Fixed in code — old password must be verified                  |
| Excessive Accessibility events                           | Improved — window-event filtering and periodic checks          |
| Overnight schedules                                      | Fixed in logic — tests cover weekday boundaries                |
| Breaks bypassing stricter overlapping rules              | Fixed in logic — exemptions are tied to matching policy        |
| Strict Mode timer vulnerable to system clock changes     | Improved — uses elapsed time and boot count                    |
| Backup of sensitive application data                     | Fixed in configuration — backup and device transfer disabled   |
| Swipe-navigation implementation                          | Reworked — now uses a Compose horizontal pager                 |
| Automated testing                                        | Improved substantially — unit and Android emulator tests added |

The repository also contains an audit-results document reporting 33 unit tests passing on each build variant, alongside successful local compilation and lint checks. Those are Codex's recorded results, not tests I have independently rerun.

These changes resolve most of the original code-level findings. The remaining question is whether the implementation and device tests cover the important failure cases.

## Remaining findings

After reviewing the updated implementation, I found two significant logic issues, several smaller concerns and some outstanding verification work.

### 1. Split-screen blocking can still be bypassed

High priority

In `BlockAccessibilityService.kt`, lines 44–61, enforcement stops immediately when the focused application is a protected recovery app, such as Android Settings.

This creates a potential split-screen loophole:

- Open Android Settings in one pane.
- Open a blocked application in the other.
- Keep Settings focused.

The service can skip checking the blocked application, even though it remains visible.

Required fix: Check all visible application windows while preserving access to Android's essential recovery functions. This needs an additional emulator test.

### 2. The individual rule's “Strict block” setting is misleading

Medium priority

In `BlockActivity.kt`, lines 159–174, a five-minute break is granted without authentication when global Strict Mode is disabled.

That remains true even if the individual rule has `strict = true`.

Consequently, selecting Strict block on a rule doesn't independently require authentication.

Required fix: Define whether rule-level Strict Mode must enforce its own authentication, or whether it requires global Strict Mode. Make the behaviour and interface consistent.

### Other issues and improvements

| Priority     | Finding                                                                              | Recommendation                                                            |
| ------------ | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| Medium       | Accessibility scans visible windows every second                                     | Profile battery and CPU usage, especially with multiple daily-limit rules |
| Medium       | Daily-limit enforcement relies on usage-event history                                | Test long-running apps, missing events and multi-window use               |
| Medium       | Rule and appearance settings use asynchronous persistence                            | Test immediate process termination after saving                           |
| Low          | Hiding the app-name label doesn't remove `{app}` from a custom message               | Clarify or adjust the setting's behaviour                                 |
| Verification | Emulator coverage targets Android 8 and Android 15                                   | Test Android 17 on your Pixel 8 Pro                                       |
| Verification | I couldn't independently confirm the latest successful Actions run and published APK | Inspect the CI run and artifact before distributing                       |

## Overall assessment

Implementation progress

Substantial improvement

Code-level security

Much improved

Production readiness

Not fully verified

My recommendation is to keep v0.3.0 as a testing release, address the two blocking-policy issues, then perform a final reliability pass on your actual phone.

The initial audit raised fundamental issues involving password security, blocking logic and persistence. Codex has addressed most of those. What's left is primarily ensuring that the app behaves consistently under unusual Android conditions.

View the latest repository · View APK build runs