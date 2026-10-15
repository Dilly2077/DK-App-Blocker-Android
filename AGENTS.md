# Repository instructions

## Local builds only — permanent policy

- All application compilation, testing, packaging and artifact generation must run on the computer hosting the active local Codex workspace. This includes APK, AAB, EXE, MSI, installers and other compiled outputs.
- Do not use GitHub Actions, GitHub-hosted runners, remote CI/CD services or cloud build infrastructure for compilation unless the user explicitly authorises an exception for the current task. Never substitute a remote build after a local failure.
- Use GitHub for source control, synchronisation, collaboration and hosting files already built locally. Upload local binaries as GitHub Release assets rather than committing generated binaries to source history unless the user explicitly requests binary commits.
- Do not create, enable or dispatch a build workflow without explicit user authorisation. The retained Android workflow is manual-only for explicit exceptions; its existence is not permission to run it. Preserve unrelated workflows. Review workflow triggers before pushing or publishing, and verify that publication did not start an Actions run.
- This policy applies to all future Codex sessions working in this repository and every application/artifact type developed here. Subdirectory guidance must preserve it.

## Local Android build procedure

- Verify JDK 17, Android SDK platform 35/build tools and Gradle 8.10.2 before building. Prefer the checksum-pinned Gradle wrapper. Local dependency downloads are permitted.
- For this Windows workspace, `.tools/build.ps1` configures the existing local toolchain and caches. Example: `& .tools/build.ps1 :app:assembleDebug --offline`. The `.tools` directory is local-only and may not exist in another clone.
- On a fresh clone, install the local tools and configure `ANDROID_HOME` or an untracked `local.properties`. Run `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`). Use offline mode only after dependencies have been cached.
- Relevant full checks are `:app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:test :app:lint :app:lintRelease`. Run device tests on a local Android device/emulator. The multi-phase persistence/reboot tests use `scripts/device-tests.sh`; inspect it before running because it reboots the selected test device. Never reboot a user's phone without explicit authorisation.
- For a user's physical phone, build with `-PisolatedDeviceAudit=true` first. This creates `com.dk.appblocker.audit` in the ignored `.tools/device-audit-build` directory, preserving the installed normal app and its data. Use `scripts/device-tests.ps1` with the explicit device serial. This runner refuses to reboot a physical phone and does not use GitHub Actions. Normal instrumentation tests clear the target app's preferences, so never run them against a user's normal installed blocker.
- Inspect exit status and build logs, check that output files exist, inspect APK metadata and verify signatures for signed artifacts. Never claim a build or installation succeeded without evidence.
- Report the exact output path and identify debug/release, signed/unsigned and instrumentation-only files. The default debug APK is `app/build/outputs/apk/debug/app-debug.apk`; the release variant is unsigned unless separately configured with an authorised local signing key.
- Preserve the local signing key for compatible future updates. Never commit or upload signing keys, credentials, passwords or secrets. A different signing key can require uninstalling an existing installation and losing its local data; state that limitation when relevant.
- If local tools are missing, prepare source/build commands and explain what is missing. If the workspace is cloud-hosted and has no access to the user's PC, do not describe a cloud build as a local-PC build or use Actions as a substitute.

## Project conventions and artifact hygiene

- Preserve the green-and-black UI, existing features and local-only architecture.
- Keep Strict Mode recoverable. Home, Android Settings, permission controllers and emergency calling must remain available. Ordinary Android Device Administrator privileges cannot prevent uninstalling.
- Keep build outputs, dependency caches, temporary files and generated binaries out of Git by default. Use ignored `dist/` for user-facing copies; publish checksums with Release assets.
- Keep the README's build/download instructions consistent with local builds. Historical audit and CI reports document past verification; they do not override this policy or authorise future cloud builds.
