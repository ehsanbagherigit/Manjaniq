Manjaniq — Complete Pre-Push Audit, Repair, and Validation

Mission

Perform a thorough, evidence-based audit of the Manjaniq Android project before any changes are pushed to GitHub.

Primary goal: Make the existing GitHub Actions workflow pass reliably, eliminate the current build failure, detect and fix related problems, and verify the Android native JNI bridge as thoroughly as the available environment allows.

Strict rule: DO NOT push, commit, amend, reset, rebase, or force-push anything. I will approve Git operations separately. Do not discard my existing changes.

Repository: "Manjaniq"
Branch: "master"

Recent relevant changes:

- "app/src/main/java/com/manjaniq/MainActivity.kt"
- "promt.md"
- "native/bridge.go"
- "native/bridge.c"
- ".github/workflows/build-debug.yml"

The most recent GitHub Actions failure was:

"go: cannot find main module, but found .git/config ..."

A subsequent unpushed workflow edit added "GO111MODULE=off" and changed the Go build command. Verify whether that solution is actually valid. Do not assume it works.

Phase 1 — Establish the current state

1. Inspect "git status", the current branch, the latest commits, and the complete diff against "HEAD".
2. Identify tracked, modified, staged, and untracked files.
3. Preserve all existing user changes.
4. Read the complete relevant files, including:
   - ".github/workflows/build-debug.yml"
   - "native/bridge.go"
   - "native/bridge.c"
   - "app/src/main/java/com/manjaniq/NativeBridge.kt"
   - "app/src/main/java/com/manjaniq/MainActivity.kt"
   - "settings.gradle" or "settings.gradle.kts"
   - root and app Gradle build files
   - "AndroidManifest.xml"
   - any Go module or native build configuration, if present
5. Inspect the complete GitHub Actions workflow, not just the failed step.

Do not modify Git history or delete files.

Phase 2 — Fix the Go/cgo native build correctly

Review the current Go build command against the actual Go toolchain behavior.

Verify all of the following:

1. Whether the project has a "go.mod" file and whether module mode is appropriate.
2. Whether "GO111MODULE=off" is compatible with the selected Go version and the intended build command.
3. Whether explicitly passing "native/bridge.go" and "native/bridge.c" to "go build" is valid. Remember that Go's build command does not necessarily accept C source files as ordinary Go source arguments.
4. Whether the correct solution is to build the Go package from the "native" directory, initialize a module, or use another valid approach.
5. Whether "native/bridge.c" is actually compiled and linked into the resulting shared library.
6. Whether the cgo preamble contains declarations only when required by "//export" rules.
7. Whether all required C headers and JNI types are available to the NDK compiler.
8. Whether "CGO_ENABLED", "GOOS", "GOARCH", "CC", and the Android API level are set correctly.
9. Whether the selected Go version supports the chosen Android/arm64/cgo build configuration.
10. Whether the generated library exports the expected JNI symbol:
    "Java_com_manjaniq_NativeBridge_GoHelloWorld"

Do not blindly add "go.mod", disable module mode, or rewrite the native code without explaining why the chosen solution is correct.

Prefer the smallest maintainable fix that works with the existing project.

Phase 3 — Verify JNI end to end as far as possible

Compare the Kotlin declaration with the exported native function:

- Package: "com.manjaniq"
- Class: "NativeBridge"
- Method: "GoHelloWorld"
- Static versus instance method conventions
- JNI parameter and return types
- Library name passed to "System.loadLibrary"
- Shared library filename and location

Verify that:

- The expected native symbol is exported in the dynamic symbol table.
- "NewStringUTF_wrapper" is linked into the shared library.
- The native library is built for Android "arm64-v8a", not Linux or desktop.
- The library is packaged into the APK at "lib/arm64-v8a/libmanjaniq_native.so".
- Kotlin calls the correct library and method.
- Native memory is managed safely.

If the local environment lacks Android SDK/NDK tools, clearly identify the limitation. Do not pretend that a native build, symbol inspection, or device test was performed when it was not.

Phase 4 — Audit the entire CI workflow

Inspect and validate every relevant workflow step, including:

- Trigger configuration
- Checkout
- Java and JDK setup
- Android SDK setup
- Android platform and build-tools installation
- NDK installation and environment variables
- Go installation and version
- Native library compilation
- Native library output path
- Gradle wrapper permissions
- Gradle dependency resolution
- Kotlin compilation
- Debug APK assembly
- Artifact upload

Check working directories, relative paths, environment-variable scope, shell syntax, YAML indentation, and step ordering.

Ensure the native library is built before Gradle packages the APK.

Ensure failures stop the workflow and produce useful error messages.

Do not disable tests, suppress errors, add "continue-on-error", or weaken validation just to make GitHub Actions appear green.

Phase 5 — Run all available local validations

Run the checks that are genuinely available in the current environment. At minimum:

1. "git diff --check"
2. YAML syntax validation using an available validator
3. Go formatting and static checks where applicable
4. Native build checks where the necessary compiler and headers exist
5. Gradle wrapper and project configuration checks
6. Kotlin compilation or Gradle build if the required Android SDK is available
7. Inspect all changes, including untracked files

If a check cannot run because a tool is missing, record it as NOT RUN, not PASS.

Do not install random tools or alter the system unnecessarily. If an appropriate tool is available through the project, use it.

Phase 6 — Test the actual CI build without pushing

Investigate whether the workflow can be tested locally with the tools already installed, such as an available YAML/workflow validator or compatible local runner.

Do not assume a local environment can reproduce GitHub-hosted Android builds perfectly.

If a full hosted CI run is impossible without pushing, say so explicitly. Do not claim that GitHub Actions is guaranteed to pass based solely on static inspection.

Do not push, create a remote branch, or trigger any remote workflow.

Phase 7 — Repair and repeat

Fix any concrete problems found during the audit, then rerun the relevant checks.

Repeat until:

- All locally runnable checks pass.
- The workflow configuration is internally consistent.
- The native build command is valid for the actual Go/cgo toolchain.
- The JNI signatures and packaging paths agree.
- No unexplained errors remain.

If a check still fails, investigate the real error and fix it where possible. Do not hide or bypass the failure.

Phase 8 — Final report and approval gate

Return a concise but complete report with these sections:

A. Problems found

List each issue, its root cause, and whether it was fixed.

B. Files changed

List every modified, added, or deleted file and explain why.

C. Validation results

For every check, report exactly one of:

- PASS — actually executed and passed
- FAIL — executed and failed
- NOT RUN — unavailable or not executed

Include the actual command or evidence for important checks.

D. Remaining risks

Explain any validation that still requires GitHub Actions, a real Android device, or unavailable SDK/NDK tools.

E. Next step

Tell me exactly what remains before I can safely commit and push.

Never say the project is fully verified or guarantee a green GitHub Actions run unless the corresponding build actually ran successfully.

Stop after presenting the report. Wait for my explicit approval before any commit or push.
