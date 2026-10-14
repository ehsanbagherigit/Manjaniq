Manjaniq — Phase 3 Implementation: Android Native Engine Integration

We are moving from project analysis to implementation. Do not repeat the completed project analysis or spend time producing another proposal. Read the relevant project files, understand the existing architecture, and start implementing Phase 3.

1. Read the existing project documentation first

Read these files from the actual repository root:

- "README.md"
- "ARCHITECTURE.md"
- "ROADMAP.md"
- "UI_DESIGN_GUIDE.md"
- "DEVELOPMENT_LOG.md"
- ".gitignore"
- ".github/workflows/build-debug.yml"

Then inspect the actual Android project structure, including:

- "settings.gradle.kts"
- Root "build.gradle.kts"
- "gradle.properties"
- "gradle/wrapper/gradle-wrapper.properties"
- "app/build.gradle.kts"
- "app/src/main/AndroidManifest.xml"
- "app/src/main/java/" and all relevant Kotlin source files
- Existing resources and any native-library directories

Inspect the existing core abstractions:

- "core/models/EngineState.kt"
- "core/models/RSTAConfig.kt"
- "core/models/OperationResult.kt"
- "core/interfaces/EngineController.kt"
- "core/interfaces/EngineStatusProvider.kt"
- "core/interfaces/Logger.kt"

Verify actual paths and filenames before using them. Do not assume that the repository structure is exactly as described in previous notes.

2. Inspect the real Go engine before choosing the integration approach

Read the Go module definition and source files under:

- "engine/rstaspoof/"

Inspect the actual entry point, package declarations, exported functions, server lifecycle, listener creation, configuration handling, shutdown behavior, and dependencies.

In particular, determine:

- Whether the current engine can be reused as a library or requires a small adapter.
- How it starts and stops its TCP proxy.
- Whether it uses command-line flags, process-level termination, or global state that would complicate embedding.
- Which Go dependencies and build constraints affect Android compilation.
- Whether "CGO_ENABLED=1" and the Android NDK toolchain are required for the chosen approach.

Do not modify "engine/rstaspoof/" during this Phase 3 proof of concept.

Do not assume JNI, "gomobile", a subprocess, or any other integration mechanism is automatically the best option. Make a decision based on the actual source. Prefer the simplest reliable approach that can eventually support real engine startup, configuration, status, logs, and controlled shutdown.

3. Build environment

My phone's confirmed Android ABI is:

"arm64-v8a"

GitHub Actions is the intended build environment. The existing workflow has already built a debug APK successfully.

Provision the required tools in CI as needed:

- JDK compatible with the existing project
- Android SDK
- Android NDK
- Go toolchain
- Existing Gradle wrapper

Do not require me to install Android SDK or NDK in Termux.

Do not add unnecessary local build prerequisites. If a specific runtime test requires my phone, clearly distinguish that from compilation tasks that CI can perform.

4. Implement a minimal but meaningful native integration proof of concept

Implement the smallest working native integration test that fits the existing project and can be extended for the real RSTA engine.

The test must:

1. Build an Android-compatible native library for "arm64-v8a".
2. Package that library into the debug APK using the Android Gradle build.
3. Load the library from Kotlin using the selected native integration mechanism.
4. Call a native function from the app.
5. Return a known, deterministic result, such as "Manjaniq native bridge OK".
6. Display the result or failure clearly in the app UI and write a diagnostic log.
7. Handle library-load or native-call failures without crashing the entire app.
8. Avoid pretending that the actual RSTA proxy is running.

The experiment must validate the real build and runtime chain, not merely create an unused ".so" file.

Keep the PoC separate from the existing Go engine source. Use a clearly named, isolated directory for any test-only bridge or wrapper code.

If a Go-based native library and JNI bridge are appropriate, implement the required bridge correctly. Do not assume that a Go-exported C function is automatically a valid JNI method. If a C/C++ JNI shim is needed, include it in the build.

If the source inspection reveals that this approach is unnecessarily complex or unsuitable, choose a technically justified alternative and document the reason briefly in "DEVELOPMENT_LOG.md".

5. Integrate with the existing Android app

Preserve the current project architecture, package names, theme, and existing screen behavior.

Reuse existing abstractions where appropriate. Avoid creating duplicate engine interfaces or unrelated architecture.

Add only the UI needed to run or display the native bridge test. Make it obvious whether the test has:

- Not run yet
- Succeeded
- Failed

The app must remain launchable even if native library loading fails.

Do not enable or simulate the real RSTA Start/Stop behavior in this PoC. The engine status must not claim that RSTA is running when only the native bridge has been tested.

Respect the existing dark visual design and Persian RTL / English LTR conventions where relevant.

6. Update GitHub Actions

Modify ".github/workflows/build-debug.yml" only as necessary to:

- Install or configure the required toolchains.
- Build the native library for "arm64-v8a".
- Package it into the Android APK.
- Run the available Gradle build and validation tasks.
- Upload the resulting debug APK as an artifact with an obvious, stable name.
- Fail clearly if native compilation or APK packaging fails.

Avoid hardcoded machine-specific paths. Use supported SDK/NDK setup methods and environment variables.

Keep secrets out of the workflow. Do not add release signing, publishing, or unrelated CI complexity.

7. Testing and verification

Run every feasible automated check in the available environment.

Verify at least:

- The native library is produced for the intended Android ABI.
- The APK contains the expected native library at the correct location, typically "lib/arm64-v8a/".
- The APK build succeeds.
- The Kotlin native-loading code and native symbols are consistent.
- Failures are reported clearly.

Use CI to validate compilation and packaging. Actual loading and execution must be verified on my physical phone after I install the artifact.

Do not claim that device runtime testing passed before I perform that test.

Provide a simple on-screen verification path so I do not need Android Studio, ADB, or Logcat access.

8. Repository and change safety

- Do not modify "engine/rstaspoof/".
- Do not rewrite Git history.
- Do not force-push.
- Do not commit or push anything.
- Do not overwrite unrelated user changes.
- Do not change the app's overall architecture or redesign the UI.
- Do not add dependencies unless they are necessary for the chosen implementation.
- Keep the changes focused and reviewable.

9. Documentation

Update "DEVELOPMENT_LOG.md" with:

- The implementation completed.
- The files changed.
- The chosen integration method and why it was selected.
- The CI build result.
- The native ABI and packaging verification result.
- Any remaining limitations.
- The exact device test still required.

Update "ROADMAP.md" only if needed to reflect the verified status of Phase 3. Do not mark the phase complete until its acceptance criteria have actually passed.

10. Final report and next step

When implementation is complete, report:

1. What was implemented.
2. Exact files created or modified.
3. Build and automated test results.
4. GitHub Actions workflow status, if available.
5. Artifact name and where to retrieve it.
6. Simple, step-by-step instructions for installing and testing on my phone.
7. Any actual blocker, with the relevant error and the smallest required action.

Then stop and wait for my device-test result before beginning the next phase.

Do not ask me for another analysis approval. Start by reading the files, then implement the PoC. Only stop early if a concrete technical blocker prevents progress, and explain it precisely.
