# Manjaniq — Initial Audit Checklist (2026-10-18)

Branch: `arena/8b38b744-manjaniq` @ `76f63af` ("Log the Serve resurrection race and its fix")
Working tree at audit start: clean. Base of branch: `arena/b76a9257-manjaniq`.
PR #2 (`feature/manjaniq-ui-redesign`): OPEN, unmerged — must stay that way.
`main`/`master`: untouched; no merges into it.

## Environment facts (evidence)

- Local sandbox: no JDK, no Gradle, no Go, no Android SDK/NDK, no adb/emulator
  (`java`, `go`, `gradle`, `adb`, `emulator`, `sdkmanager` all absent).
- Network egress is restricted to github.com, api.github.com, codeload.github.com,
  registry.npmjs.org, pypi.org, files.pythonhosted.org (verified by curl probes:
  repo.maven.apache.org, dl.google.com, services.gradle.org,
  objects.githubusercontent.com all fail).
  => Gradle/Android/Go execution is BLOCKED locally; the real pipeline runs in
  GitHub Actions on GitHub-hosted runners (full JDK/SDK/NDK/emulator available).
- `gh` CLI is authenticated and can push to the working branch, so CI evidence
  is obtainable from real workflow runs.

## Known-failure investigation (spec section 2)

| # | Issue | Status | Evidence |
|---|-------|--------|----------|
| A | Empty Compose test version (`ui-test-junit4:.`) | Confirmed defect (history) | Run 39168562782 job 117798035307 annotation: "Could not find androidx.compose.ui:ui-test-junit4:." — androidTest deps had no version and no BOM platform. Current branch has NO androidTest deps at all (nothing to resolve), so the fix is to add them correctly with the Compose BOM for the androidTest source set. |
| B | Instrumentation signature mismatch | Confirmed defect (history) | Run 39168136508 job 117796876083 annotation: "Permission Denial: starting instrumentation ComponentInfo{com.manjaniq.test/androidx.test.runner.AndroidJUnitRunner} ... package com.manjaniq.test does not have a signature matching the target com.manjaniq". Root cause: app APK installed from the build job's artifact (signed with that job's auto-generated debug keystore) while the test APK was rebuilt in the emulator job (different debug keystore). Fix: build+install both APKs in one job via `connectedDebugAndroidTest`. |
| C | `/usr/bin/sh: 1: Bad substitution` | Confirmed defect (history) | Jobs 117776046779/117773292680 annotation: "The process '/usr/bin/sh' failed with exit code 2" — `${PIPESTATUS[0]}` used inside the emulator-runner `script:` block, which is executed by dash, not bash. Fix: POSIX-safe exit-code capture (redirect to file, then `$?`), explicit `shell: bash` for bash-specific steps. |
| D | App process disappears after launch | Confirmed defect (history + current-branch risk) | (1) Previous branches crashed at startup via a LocaleHelper that mutated the shared `Configuration` in `attachBaseContext` (documented in their own RobolectricLaunchTest comments). (2) Current branch requests POST_NOTIFICATIONS in `MainActivity.onCreate` before any UI — on Android 13+ the permission-controller activity becomes the resumed activity, so `am start -W` "ok" proves nothing (matches the reported symptom). Fix: request the permission lazily at engine-start (point of need), grant it explicitly in CI before launch, and add a real smoke test asserting process-alive + RESUMED + Home UI marker. |

## Repository audit findings

### Verified working (inspection + CI history)
- Go engine (`engine/rstaspoof`) genuine RSTA integration, lifecycle race fix
  (Serve resurrection) with `TestRapidStartStop` regression test; CI run
  39087908963 passed all engine tests on the base branch.
- JNI bridge: Kotlin `external` declarations match Go `//export` names and the
  C wrappers in `native/bridge.c` are compiled into the `.so` (whole `./native`
  package built); `native/verify_native_lib.sh` cross-checks symbols.
- `EngineService`: foreground specialUse FGS with channel, Stop action,
  engine stopped on destroy; manifest declares permission + FGS type +
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- `EngineRepository`: polls real native state/metrics/logs; no fabricated
  values; native-absent degrades to STOPPED without crashing.
- `ConfigStore`/`RSTAConfig`: genuine-format JSON persistence, validation
  mirroring the Go side.
- Current workflow: Go tests with `-count=3`, native arm64 build, APK
  verification, artifact upload; single authoritative workflow file.

### Confirmed defects (to fix)
1. No Android instrumentation test source set at all — the spec-required
   startup smoke test, navigation and UI tests do not exist on this branch.
2. No `testInstrumentationRunner` declared; no androidTest dependencies
   (Compose BOM missing for androidTest — the historical issue A).
3. `MainActivity` requests POST_NOTIFICATIONS at cold start (startup defect D).
4. No launcher icon / adaptive icon; manifest has no `android:icon`
   (system default icon ships — "branding" acceptance criterion fails).
5. All UI text hardcoded in Kotlin; no `values/strings.xml` beyond app_name,
   no Persian (`values-fa`) resources — English/Persian acceptance criteria fail.
6. No locale/`attachBaseContext` handling (system-locale follow relies on the
   platform default; a tested helper is required by the audit).
7. Workflow: no explicit `shell: bash` on bash-specific steps; no
   dependency-resolution verification step; no lint; no emulator/instrumentation
   job; APK artifact not named with the commit SHA.
8. `abiFilters` is arm64-v8a only — the x86_64 emulator cannot install the APK
   (INSTALL_FAILED_NO_MATCHING_ABIS), blocking on-device validation.

### Potential defects requiring verification (in CI)
- Compose BOM 2024.09.03 + AGP 8.7.2 + Kotlin 2.0.21 combination resolves
  cleanly (to be proven by a real `dependencies` task run in CI).
- FGS `specialUse` notification behaviour on API 35 emulator (service start
  instrumentation test).
- Engine start/stop through the real service on-device (instrumentation test).

### Blocked by environment (local sandbox)
- All Gradle tasks, Go tests, native c-shared build, emulator boot, APK
  installation, instrumentation execution. Evidence: toolchain absence +
  blocked hosts listed above. These run in GitHub Actions instead; results
  are reported from real workflow runs.
