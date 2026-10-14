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
