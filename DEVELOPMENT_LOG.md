# Manjaniq — Development Log

---

## 2026-10-14 12:28 UTC — Core Models and Interfaces Review

### Completed

**Core Kotlin model/interface corrections (DONE)**

The following six Kotlin files were reviewed against the Go engine source
(`engine/rstaspoof/rstaspoof.go`) and corrected where evidence required it:

- `core/models/EngineState.kt` — no changes needed; enum states confirmed.
- `core/models/RSTAConfig.kt` — corrected.
- `core/models/OperationResult.kt` — no changes needed; sealed result confirmed.
- `core/interfaces/EngineController.kt` — no changes needed.
- `core/interfaces/EngineStatusProvider.kt` — no changes needed.
- `core/interfaces/Logger.kt` — no changes needed.

Corrections to `RSTAConfig.kt` (per OpenCode audit report):

- Removed `noMonitor` (no direct engine equivalent; monitor behavior is
  governed by the `MONITOR` config value and the `-no-monitor`/`-quiet`
  flags in the engine).
- Removed `verbose` and `noRaw` — the engine parses these CLI flags but
  explicitly ignores them (`rstaspoof.go` lines 1843–1844:
  `_ = fNoRaw; _ = fVerbose`), so they have no runtime behavior to model.
- Removed `configJson` — an unused placeholder with no documented need in
  the current architecture.
- Added `fakeSNIMethod` with the source-verified default `"prefix_fake"`
  (`rstaspoof.go` lines 1385 and 1448).
- Retained `monitorEnabled` and `quietMode`.

Configuration defaults, bypass methods (`fragment`, `fake_sni`,
`combined`), and fragmentation strategies (`sni_split`, `half`, `multi`,
`tls_record_frag`) were cross-referenced against the Go engine source and
found consistent.

**Reference check (DONE)**

Per the final OpenCode reference-check report:

- Searched the project (excluding the nested `engine/rstaspoof/` repository)
  for references to the removed fields `noMonitor`, `verbose`, `noRaw`, and
  `configJson`.
- No broken references to these fields exist in the core Kotlin source files.
- Remaining mentions in `report.md` are historical documentation of the Go
  CLI, not broken Kotlin code references.
- References to `RSTAConfig` and `fakeSNIMethod` remain consistent
  (`core/models/RSTAConfig.kt`, `core/interfaces/EngineController.kt`).

### Validation

- Reference consistency between the six core files was verified by search
  (file paths, package declarations, and cross-references inspected).
- Configuration field values were verified against the Go engine source.
- **Kotlin/Android compilation has NOT been verified (NOT VERIFIED).**
- **Unit tests have NOT been run (NOT VERIFIED).**
- At the time of these checks, the project did not include an Android/Gradle
  build configuration, so no compilation or test run was possible.

### Known Limitations

- No Android/Gradle build configuration existed at the time of the core
  review and reference check, so the six Kotlin files remain uncompiled.
- The `fakeSNIMethod` values accepted by the engine's `FakeSNIBypass`
  implementation were not exhaustively enumerated; only the default
  (`"prefix_fake"`) is source-verified so far.

---

## 2026-10-14 12:45 UTC — Root .gitignore Preparation

### Completed

**Root .gitignore configuration (DONE)**

The root `.gitignore` was prepared for the initial GitHub setup. The following inspections were performed to determine the necessary ignore rules:

- **README.md**: Confirmed that `engine/rstaspoof/` is a separate repository and the nested `.git` directory is not required for the build.
- **app/build.gradle.kts**: Verified that Android source sets reference `core/` directories but do not depend on files within `engine/`.
- **settings.gradle.kts**: Verified the project structure includes only the `:app` module.
- **.github/workflows/build-debug.yml**: Confirmed that the CI build runs `./gradlew assembleDebug`, which does not require files from the `engine/` directory.

**Decisions and Changes:**

- Added `/engine/` to the ignore list based on the evidence that the current Phase 2 Android foundation does not depend on the engine source for compilation or CI.
- Preserved existing rules for build outputs (`/build`, `/app/build`, `/*/build`), Gradle cache (`.gradle`), local properties (`local.properties`), and IDE settings (`.idea/`, `*.iml`, `.vscode/`).
- Preserved OS-specific rules (`.DS_Store`).
- Preserved native build artifact rules (`*.apk`, `*.aab`, `*.so`) and the exception for `jniLibs`.

### Validation

- Verified that the final `.gitignore` contains all required rules and no redundant entries.
- **Confirmed** that no Git initialization, staging, commit, or push was performed.
- **Confirmed** that no files under `engine/rstaspoof/` were modified.
- **Confirmed** that only `.gitignore` was modified during the preparation task.

### 2026-10-17 — Phase 3 Verification & Fix

### Critical Issues Fixed

**Issue 1: Staged engine/rstaspoof files (SOLVED)**
- **Problem**: 12 `engine/rstaspoof/*.go` files were incorrectly staged for commit, despite being a separate repository that should be completely ignored.
- **Risk**: Would have committed the entire Go engine into the Manjaniq project history, violating the project architecture.
- **Action**: Restored `engine/` directory to `.gitignore` and unstaged all engine/rstaspoof files.
- **Status**: FIXED - engine/rstaspoof files are now unstaged and will not be committed.

**Issue 2: Missing /engine/ in .gitignore (SOLVED)**
- **Problem**: The `/engine/` line was removed from .gitignore during Phase 3 work, making the engine directory eligible for staging.
- **Risk**: Would have allowed accidental commit of engine/rstaspoof files.
- **Action**: Restored `/engine/` line to .gitignore (consistent with HEAD and development log).
- **Status**: FIXED - .gitignore now correctly includes `/engine/`.

### Current Staging Status

**Staged (ready for commit):**
- `ENGINEERING_README.md` - Phase 3 JNI integration documentation
- `app/build.gradle.kts` - Updates for JNI support
- `app/src/main/java/com/manjaniq/core/interfaces/EngineController.kt` - Core models updates
- `app/src/main/java/com/manjaniq/core/models/RSTAConfig.kt` - Core models updates
- `app/src/main/java/com/manjaniq/core/models/Result.kt` - Core models updates
- `app/src/main/java/com/manjaniq/ui/components/ManjaniqApp.kt` - UI updates
- `app/src/main/java/com/manjaniq/ui/components/StatusCard.kt` - UI updates
- `app/src/main/java/com/manjaniq/ui/theme/ManjaniqTheme.kt` - UI updates

**Unstaged (modified in working directory):**
- `app/build.gradle.kts` - JNI source set configuration
- `app/src/main/java/com/manjaniq/MainActivity.kt` - MainActivity JNI integration
- `app/src/main/java/com/manjaniq/NativeBridge.kt` - JNI bridge implementation
- `native/bridge.c` - JNI C wrapper
- `native/bridge.go` - JNI Go wrapper
- `native/verify_native_lib.sh` - JNI verification script
- `promt.md` - Corrections and improvements

### Phase 3 Verification

**Verified:**
- ✅ Project architecture is preserved (engine/rstaspoof remains separate)
- ✅ .gitignore correctly excludes the engine directory
- ✅ No engine/rstaspoof files are staged for commit
- ✅ Native integration work (Phase 3) is preserved in working directory

**Not Yet Verified:**
- **❌ Kotlin/Android compilation (NOT VERIFIED)** - Core files remain uncompiled
- **❌ Unit tests (NOT VERIFIED)** - Tests have not been run
- **❌ JNI verification (NOT VERIFIED)** - Native library has not been built and loaded

### Next Steps (PLANNED)

1. **Verify JNI Native Library**: Run `./gradlew assembleDebug` to build APK with native library
2. **Test Native Integration**: Install APK and verify JNI calls work on device
3. **Run Unit Tests**: Execute `./gradlew :app:testDebugUnitTest` for Android tests
4. **Run Lint/TypeCheck**: Execute `./gradlew lint` to verify code quality

### Known Limitations

- No Android/Gradle build configuration existed at the time of Phase 2 review and core reference check, so the six Kotlin files remain uncompiled.
- The `fakeSNIMethod` values accepted by the engine's `FakeSNIBypass` implementation were not exhaustively enumerated; only the default (`"prefix_fake"`) is source-verified so far.
- Android SDK/NDK toolchain must be properly configured for JNI development.

### Next Steps (PLANNED — not completed)

1. **PLANNED** — Initialize the Git repository and set up the remote origin.
2. **PLANNED** — Perform the initial commit and push to GitHub.

---

## 2026-10-14 — Resume Project and Phase 2 Verification

### Completed

**Project State Reconstruction (DONE)**
- Confirmed project root at `/mnt/sdcard/TermuxProjects/manjaniq`.
- Verified Git state: Branch `master`, up to date with `origin/master`.
- Confirmed latest commit `ce7498c` ("Phase 2: Android Project Foundation").
- Verified remote configured to `git@github.com:ehsanbagherigit/Manjaniq.git`.

**Phase 2 Verification (User-Reported & Verified)**
- **Verified via Git**: Initial foundation commit `ce7498c` exists.
- **User-Reported**: GitHub Actions "Build Debug APK" workflow succeeded for commit `ce7498c`.
- **User-Reported**: The `manjaniq-debug-apk` artifact was downloaded, installed, and launched successfully on an Android device.
- **User-Reported**: Initial app screen displayed Manjaniq title, "STOPPED" status, and Start/Stop controls, noting RSTA engine integration is not available yet.

### Environment Note
- Work resumed after a Termux session closure. No temporary state or running processes were preserved.

### Current Status
- **Phase**: Phase 2 (Project Foundation) is considered complete based on successful build and launch milestones.
- **Next Milestone**: Phase 3 — RSTA Integration Proof of Concept.
- **Blockers**: None currently identified for starting Phase 3 analysis.

---

## 2026-10-14 — Phase 3 Feasibility Review

### Confirmed Facts
- **Engine Entry Point**: The Go engine is a `package main` with a `main()` function. It uses a `ProxyServer` struct with a `Start()` method that runs an infinite loop (`ln.Accept()`).
- **Lifecycle**: Startup is handled by `server.Start()`. Shutdown is currently handled by `os.Exit(0)` upon receiving `SIGINT/SIGTERM`.
- **Configuration**: Config is handled via a `map[string]interface{}` merged from defaults, JSON files, and CLI flags.
- **Tooling (Current Environment)**: 
  - Go: `go1.27.1 android/arm64` is installed.
  - java: `javac` is available.
  - Gradle: Wrapper is present (`gradle-8.9-bin.zip`).
  - Android SDK/NDK: `android-sdk-manager` and `ndk-build` are **NOT** available in the current shell path.
- **Git/Build Safety**: `/engine/` is ignored in `.gitignore`. Building from this source for the APK will la-not add the engine repo to the main git history as long as the resulting `.so` is placed in `app/src/main/jniLibs`.

### Integration Analysis
- **Option 1: Executable Child Process**: 
  - *Pros*: Zero changes to Go code.
  - *Cons*: Extremely difficult on modern Android due to `W^X` (Write XOR Execute) restrictions and the need to package a binary in the app's data directory. High risk of failure.
- **Option 2: Shared Library (c-shared/JNI)**: 
  - *Pros*: Native Android integration, consistent with `ARCHITECTURE.md` ("integrate it through an Android-compatible native library").
  - *Cons*: Requires wrapping `main()` logic into exported functions (e.g., `StartEngine`, `StopEngine`).
  - **Decision**: This is the preferred approach.
- **Option 3: gomobile**:
  - *Pros*: Simplifies Go-to-Java binding.
  - *Cons*: Often introduces significant overhead and can be restrictive regarding the existing `net` package usage in some edge cases.

### Trade-offs & Recommendation
- **Fewest Changes**: and Option 2 is the most professional and architecturally sound. Option 2 requires minimal changes: just adding a few exported functions in a new Go file within the engine directory.
- **Recommendation**: **Option 2 (Shared Library via JNI)**. It aligns with the project's architectural goals and avoids the unstable nature of executing bundled binaries on Android.

### Pending Tests & Blockers
- **Android ABI**: Not yet determined. To check: run `adb shell getprop ro.product.cpu.abi` or check "Device Info" in phone settings.
- **Toolchain**: Android SDK/NDK must be properly configured/located to perform the actual build.
- **Verification**: The PoC remains unverified until a `.so` is built and loaded by the Android app.

---

## 2026-10-14 — Build Environment Analysis

### Confirmed Tooling Requirements for Phase 3
To implement the Shared Library (JNI) approach, the following tools are required:
1. **Go Compiler**: To build the `.so` with `-buildmode=c-shared`.
2. **Android NDK**: Required by the Go compiler to cross-compile for Android ABIs.
3. **JDK & Gradle**: To compile the Kotlin JNI bridge and and package the `.so` into the APK.
4. **Android SDK**: Required for the build process to define the target API level.

### CI vs Local Capabilities
- **GitHub Actions (CI)**: 
  - **Can handle everything**: JDK, Gradle, and Android SDK are readily available via standard actions. 
  - **Go & NDK**: Can be installed on the CI runner using `setup-go` and by downloading the NDK via a script or using a pre-configured Android image.
  - **Result**: The entire build chain (Go $\rightarrow$ `.so` $\rightarrow$ APK) can be automated in CI.
  - **No tools need to be installed in Termux**.
- **Local (LPhone)**:
  - **Necessary for**: **Installation and Runtime Verification**. CI can build the APK, and it cannot verify if the native library actually loads on the real device or if the proxy binds to the port.
- **Not necessary for**: Compilation or packaging.

### Minimal Viable Experiment
The smallest experiment to test native-library integration without modifying the engine's core logic:
1. **Go**: Create a tiny `bridge.go` in the native directory.
2. **Build**: Configure GitHub Actions to build this as a `.so` and include it in the APK.
3. **Kotlin**: Add a single JNI call to `GoHelloWorld()` and and log the result to Logcat.
4. **Verification**: Install the APK and APK and check if the "Hello from Go" message appears in logs.

### Pending Actions
- **User Action**: Confirm the device ABI (e.g., `arm64-v8a`).
- **Verification**: The PoC remains unverified until a `.so` is built and loaded by the Android app.

---

## 2026-10-18 — Fix GitHub Actions APK build ("cannot find package manjaniq/engine/rstaspoof")

### Root cause (three stacked problems, all verified against the repo)
1. `.github/workflows/build-debug.yml` set `GO111MODULE=off`, so the
   module-style import `manjaniq/engine/rstaspoof` in `native/bridge.go`
   could never resolve (no GOPATH checkout exists in CI).
2. There was **no `go.mod`** anywhere in the repository, despite older
   docs claiming one existed — so module mode had nothing to resolve
   against either.
3. The **`engine/rstaspoof` package itself was missing from GitHub**:
   `.gitignore` contained `/engine/`, so the engine was never committed
   (verified: no remote branch/tree contains `engine/` or `go.mod`).
   The previously green CI runs used an older stub `bridge.go` with no
   engine import; commit 5a5f3ae rewrote the bridge against an engine
   API that only existed on a local disk.

### Fixes
- Added root `go.mod` (`module manjaniq`, `go 1.22`).
- Added `engine/rstaspoof` implementing exactly the API `bridge.go`
  uses (`Config`, `NewEngine`, `Start`/`Stop`/`State`/`Metrics`,
  `StateRunning`, ...): synchronous bind (bind failure is a real
  error, never fake success), plain TCP relay to
  `CONNECT_IP:CONNECT_PORT`, live metrics, idempotent `Stop`, restart
  support, host-side unit tests. **Note:** SNI-spoof/fragment bypass
  logic is *not* implemented yet — the config fields are parsed so the
  Kotlin<->Go contract works; porting the upstream bypass into
  `relay()` is the remaining engine work.
- Removed `/engine/` from `.gitignore` so the package is tracked.
- `build-debug.yml`: `GO111MODULE=off` -> `on`; native build still
  runs from the repo root as `go build ./native` (whole package,
  bridge.c included) -> `app/src/main/jniLibs/arm64-v8a/libmanjaniq_native.so`,
  which is the default jniLibs dir `app/build.gradle.kts`/AGP packages
  as `lib/arm64-v8a/...`. Added a quick `go test ./engine/...` step.
- Deleted duplicate `.github/workflows/android_ci.yml`: it built only
  `native/bridge.go` (dropping bridge.c) into `native/arm64-v8a/`, a
  path Gradle never packages. `build-debug.yml` is now the single
  authoritative pipeline.
- Kotlin compile fixes unmasked once the native step can pass (this
  code had never compiled in CI): `ManjaniqTheme.kt` declared the
  wrong package and an `object` instead of the `@Composable`
  `ManjaniqTheme` that `MainActivity` imports/calls; `ManjaniqApp.kt`
  and `StatusCard.kt` used `.background`/`RoundedCornerShape` without
  imports; `isStarting` was delegated with `by` but used via `.value`
  and passed as `State<Boolean>`.

### Verification
- Workflow YAML parsed with a real YAML parser; `git diff --check` clean.
- No Go/JDK toolchain in the local sandbox, so Go/Gradle compilation
  is verified by GitHub Actions. Run 39083707635: Go engine tests,
  native build and native verification passed; `testDebugUnitTest`
  failed because `ManjaniqApp.kt` was still missing the Compose
  imports fixed above. After adding them, run **39084006065 passed
  every step** (Go tests, arm64-v8a native build + verification,
  unit tests, `assembleDebug`, APK-content check for
  `lib/arm64-v8a/libmanjaniq_native.so`, artifact upload).
  Artifact `manjaniq-debug-apk` (11,100,830 bytes) is attached to
  that run: https://github.com/ehsanbagherigit/Manjaniq/actions/runs/39084006065

---

## 2026-10-18 (later) — Replacement engine withdrawn; genuine RSTA source required

### Correction to the previous entry
The `engine/rstaspoof` package added earlier today was a newly
written plain TCP relay, created because this sandbox only has the
GitHub clone — where the genuine engine has never existed. The
project owner rejected that substitution (correctly: it silently
dropped the real RSTA/SNI-spoofing behaviour). **The replacement
`rstaspoof.go`/`rstaspoof_test.go` have been removed.** The green
runs 39084006065/39084364166 therefore proved the *pipeline*
(workflow, module resolution, JNI packaging, Kotlin compilation,
APK verification), **not** the genuine engine. No APK from those
runs should be treated as the real RSTA build.

### Verified facts (re-checked this session)
- The genuine engine sources (`engine.go`, `config.go`, `proxy.go`,
  `tls.go`, `bypass.go`, ...) are **not** in this workspace and
  **not** on any GitHub branch (`master` has no `engine/` at all).
  They exist only on the owner's machine, excluded by the old
  `/engine/` ignore rule. The agent cannot fetch or recreate them
  and will not substitute the upstream single-file program either.
- What is ready and kept from the previous fix: root `go.mod`
  (`module manjaniq`, `go 1.22`, stdlib-only until the genuine
  imports say otherwise); `GO111MODULE=on` with
  `go build -buildmode=c-shared ./native` from the repo root
  (includes `bridge.c`); `.so` written to
  `app/src/main/jniLibs/arm64-v8a/` where Gradle packages it;
  duplicate `android_ci.yml` deleted; Kotlin compile fixes;
  `/engine/` removed from `.gitignore`.
- `.gitignore` now also ignores the prebuilt `engine/rstaspoof/rstaspoof`
  binary and `*.test` files, with a note that a nested `engine/rstaspoof/.git`
  must never be committed (it would become a gitlink, not source).
- The workflow now fails fast with an explicit
  "Genuine engine source missing" error if `engine/rstaspoof/*.go`
  is absent, instead of a bare "cannot find package".

### API contract the genuine package must satisfy (from native/bridge.go)
- `type Config` — JSON-unmarshalled from Kotlin keys `LISTEN_HOST`,
  `LISTEN_PORT`, `CONNECT_IP`, `CONNECT_PORT`, `FAKE_SNI`,
  `BYPASS_METHOD`, `FRAGMENT_STRATEGY`, `FRAGMENT_DELAY`,
  `USE_TTL_TRICK`, `MONITOR`, `quiet`.
- `func NewEngine() *Engine`, `(*Engine).Start(Config) error`,
  `(*Engine).Stop() error`.
- `(*Engine).State()` comparable to `rstaspoof.StateRunning` and
  convertible with `string(...)` (values must match the Kotlin
  `EngineState` enum: STOPPED/STARTING/RUNNING/STOPPING/ERROR).
- `(*Engine).Metrics()` returning a JSON-marshalable value.

### Owner action required (only place the genuine source exists)
On the machine that has `engine/rstaspoof/`:
1. Fetch this branch, copy the genuine `*.go` files into
   `engine/rstaspoof/` **without** the nested `.git` and without
   binaries (keep your local `.git` backup elsewhere if needed).
2. `go build ./...` locally; fix only genuine import/API mismatches
   against the contract above, and run `go mod tidy` only if the
   genuine imports require external dependencies.
3. `git add engine go.mod go.sum` (go.sum only if created),
   commit, push to this branch (`arena/b76a9257-manjaniq`).
4. The existing workflow then builds and uploads `manjaniq-debug-apk`;
   merge PR #1 into `master` once that run is green.

### Verification this session
- Workflow YAML re-parsed OK; ignore semantics checked with
  `git check-ignore` (engine *.go NOT ignored, prebuilt binary ignored);
  `git diff --check` clean.
- GitHub Actions on this commit is **expected to fail** at
  "Test Go engine package" with the explicit missing-source error:
  the genuine engine is not committed yet, so no APK is claimed.

---

## 2026-10-18 — Genuine RSTA engine integrated; full Android app (APK green)

### What happened
The owner uploaded the genuine upstream source to `rsta-source/` on
`master` (`rstaspoof.go` 1,973 lines, `config.json`, `README.md`).
Merged into this branch (`3955cbf`); the upload had accidentally
renamed away the project `README.md` (restored) and left a 2-byte
placeholder at `rsta-source/rstaspoof` (removed — it was `\r\n`,
not an executable).

### Genuine engine, as read from source (not docs)
- Single-file `package main`, stdlib only. Fixed-destination TCP
  proxy: parses the first TLS ClientHello, applies the chosen
  bypass, then relays; "server replied" ⇒ bypass OK, silence ⇒
  DPI-blocked event. Rich `PacketMonitor` (per-connection state +
  total/OK/blocked/byte counters) printing colourised events.
- Methods: `fragment`, `fake_sni`, `combined`. Strategies:
  `sni_split`, `half`, `multi` (24B chunks), `tls_record_frag`.
- **Honest findings:** in this source the "TTL trick" does NOT set
  IP TTL and there is no raw-socket injection (`-no-raw` is parsed
  and ignored; `FAKE_SNI_INJECT` print code is unreachable).
  `fake_sni` = fake-ClientHello probe on a side connection +
  SNI-split; `FAKE_SNI_METHOD`/`FAKE_SNI_FRAGMENT_REAL` are parsed
  and stored but do not change behaviour. The UI says exactly this.
- Embedding blockers: CLI `main()`, `os.Exit` paths, stdout-only
  output, and `ProxyServer.Start()` `continue`d on Accept errors
  (could never shut down, no connection tracking).

### Integration (smallest adaptation; protocol logic untouched)
- `engine/rstaspoof/rstaspoof.go` = the genuine file with marked
  `[Manjaniq integration]` changes only: `package rstaspoof`,
  `main()`+CLI imports removed, `fmt.Print*` routed to a leveled
  log ring, `Listen()`/`Serve()` split (synchronous bind errors),
  closed-listener ends the accept loop, connection tracking +
  `Stop()`. `rsta-source/` keeps the pristine copy to diff against.
- `engine/rstaspoof/engine.go` (new): Config (genuine JSON keys),
  Engine Start/Stop/State, Metrics from the genuine monitor
  counters, log access. Start mirrors the original `main()` wiring
  (resolve target host, monitor mode, StrategyFactory); one
  documented change: no pinned source-interface IP (breaks on
  Android network switching). Bind happens synchronously, so the
  UI can never show RUNNING for a failed start; Stop closes the
  listener and in-flight connections and reports STOPPED only
  after the serve loop exits (session stats go to the log).
- JNI: `GetLogs`/`ClearLogs` added on both sides; Kotlin config
  JSON now also sends `FAKE_SNI_METHOD`/`FAKE_SNI_FRAGMENT_REAL`
  (previously dropped silently).
- Android: foreground `EngineService` (`specialUse`, notification
  with Stop action, engine stopped on service destroy);
  `EngineRepository` polls real state/metrics/logs at 1s (no
  fabricated values); `ConfigStore` persists genuine-format JSON
  in SharedPreferences; Material 3 tabs — Dashboard (state, actual
  listen address, genuine stats, recent log, local-proxy
  explanation), Settings (all genuine keys, validated inputs,
  honest per-method/strategy descriptions, apply/restart/reset
  with confirmations, JSON import = paste-filled form only,
  export = view/copy), Logs (timestamps, levels, pause/resume,
  copy, clear with confirmation, empty state). `arm64-v8a`
  abiFilter; permissions limited to INTERNET /
  ACCESS_NETWORK_STATE / foreground-service / notifications.

### Verification (real, in GitHub Actions)
- `engine_test.go` on the CI host: fragment reassembly for all 4
  strategies, built-ClientHello SNI parse round-trip, **full
  proxy pass** of a built ClientHello through the running genuine
  engine (fragment/sni_split) to an echo upstream, byte-identical;
  double-start rejected; restart works; bad configs rejected.
- Run 39087574786 (engine slice): all steps green.
- Run 39087908963 (full app): **all steps green** — Go tests,
  arm64 native build, JNI/ELF verification, unit tests,
  `assembleDebug`, APK contains `lib/arm64-v8a/libmanjaniq_native.so`,
  artifact `manjaniq-debug-apk` uploaded (11,314,818 bytes).
- NOT verified (impossible in this sandbox): on-device launch,
  Start/Stop against a live network, and whether any bypass method
  defeats a real DPI middlebox. Compilation + localhost proxying
  do not prove censorship evasion — device testing is next.

### Known limitations
- Traffic counters advance in 256KB steps (genuine monitor design).
- English UI only (Persian localization deferred to protect the
  working integration); default system launcher icon;
  unsigned debug build; arm64-v8a only.
- Settings draft edits are discarded if you switch tabs without
  saving. Config changes while running require the offered restart.

### Follow-up (same day): engine "resurrection" race — found by CI, fixed
- Run 39088287350 hung the full 10-minute Go test timeout on a
  docs-only commit. Instrumented CI (named 20s per-call guards,
  one-line goroutine dumps + engine-internal state mirrored into
  check annotations, `-count=3`) reproduced it reliably and
  identified the wedge: `Stop` after a restart.
- Root cause (real product bug, not test noise): `Start` spawns
  the `Serve` goroutine; a fast `Stop` could run before that
  goroutine was first scheduled, closing and clearing the
  listener — `Serve` then saw a nil listener, bound a *fresh*
  socket, reset the closed flag and accepted forever while
  `Stop` waited for it to exit. On a phone (Start, then quickly
  Stop) the engine would have been unstoppable.
- Fixes: `Serve` never creates a listener (serves only what
  `Listen()` made; exits immediately if already closed);
  `Engine.Start` does blocking work (DNS/bind) outside its mutex;
  accept loop also keys off an explicit closed flag.
  `TestRapidStartStop` (10 start→immediate-stop cycles per run)
  guards the regression. Failing runs for the record:
  39088287350, 39089231697, 39089475739, 39089899015;
  fixed and green: 39090141043 (3 consecutive failures pre-fix
  under identical `-count=3` conditions, pass after; the final
  docs commit re-verifies HEAD once more).
