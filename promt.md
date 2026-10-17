Task: Audit and Fix Manjaniq Android APK Build Pipeline

You are a senior Android, Go, JNI/CGO, and GitHub Actions engineer. Your task is to inspect, debug, and FIX the existing Manjaniq project so that GitHub Actions can reliably build and verify a working Android debug APK.

Do not stop after providing recommendations. Inspect the actual repository, make the necessary safe code/configuration changes, and run all checks supported by the environment.

1. Critical safety rules

- Preserve all existing user work and unrelated changes.
- NEVER run "git reset", "git clean", "git checkout --", "git restore", "git stash", or destructive cleanup commands.
- Do not commit, push, amend commits, or rewrite Git history.
- Do not overwrite existing files without first inspecting their contents and diffs.
- Do not delete directories, nested repositories, binaries, or files simply because they appear unnecessary.
- Do not print secrets, tokens, private keys, or ".env" contents.
- Do not claim a build or test succeeded unless you actually ran it and verified its exit status.
- Make focused, explainable changes. Avoid unrelated redesigns and changes to the networking engine's intended behavior.
- Inspect both staged and unstaged changes before editing. Preserve both.
- Do not assume the working tree is clean.

2. Project facts

The project is an Android application called Manjaniq. The repository uses branch "master" and contains:

- "go.mod", declaring "module manjaniq" and Go 1.22.
- "engine/rstaspoof/", containing the Go networking engine.
- "native/bridge.go" and "native/bridge.c", implementing the JNI/CGO bridge.
- "native/verify_native_lib.sh", intended to validate the native shared library.
- "app/src/main/java/com/manjaniq/NativeBridge.kt", containing Kotlin native declarations.
- "app/build.gradle.kts", containing Android source-set and JNI library configuration.
- ".github/workflows/build-debug.yml".
- ".github/workflows/android_ci.yml".

The intended Android ABI is "arm64-v8a". The expected native library name is "libmanjaniq_native.so", loaded through "System.loadLibrary("manjaniq_native")".

The existing code includes JNI exports such as:

- "Java_com_manjaniq_NativeBridge_GoHelloWorld"
- "Java_com_manjaniq_NativeBridge_StartEngine"
- "Java_com_manjaniq_NativeBridge_StopEngine"
- "Java_com_manjaniq_NativeBridge_IsEngineRunning"
- "Java_com_manjaniq_NativeBridge_GetEngineState"
- "Java_com_manjaniq_NativeBridge_GetMetrics"

Inspect the actual Kotlin and Go source before assuming these declarations or signatures are complete and correct.

3. Known problems to investigate and fix

A. Go Modules and cross-compilation

The current "build-debug.yml" sets:

"GO111MODULE=off"

However, the project has a "go.mod" file, and "native/bridge.go" imports:

"manjaniq/engine/rstaspoof"

This configuration may break module resolution. Fix the native build to use the repository's actual Go module correctly.

Use Android NDK Clang with appropriate CGO configuration for Android ARM64. Verify the correct compiler target, Android API level, linker settings, and "-buildmode=c-shared" requirements.

Do not assume that setting "GOOS=android" and "GOARCH=arm64" alone produces a valid Android shared library.

Build the entire native package, including required C source files. Do not compile only "native/bridge.go" if doing so omits "bridge.c" or other necessary package files.

B. Native library packaging

The existing workflow builds the native library into:

"app/src/main/jniLibs/arm64-v8a/libmanjaniq_native.so"

However, "app/build.gradle.kts" currently contains:

"jniLibs.srcDirs("native")"

Investigate the real Gradle source-set configuration and select a consistent packaging strategy. Ensure the generated library is included in the final APK at:

"lib/arm64-v8a/libmanjaniq_native.so"

Avoid duplicate library packaging, stale binaries, incorrect source directories, or silently packaging an old library.

Ensure verification checks the exact library that will be packaged, and verify the extracted library from the final APK as well.

C. Fix the native verification script

Inspect and correct "native/verify_native_lib.sh".

Known concerns include:

- The script invokes "fail" before its function definition in some error paths.
- Its undefined-symbol check appears incorrect: "nm" commonly marks undefined symbols with "U", not "T.*Undefined".
- Its ELF check currently distinguishes ELF32/ELF64 but does not adequately validate the expected ARM64 architecture.
- ELF64 alone does not prove that the library targets Android rather than Linux.
- Symbol matching must correctly identify exported JNI symbols without false positives or false negatives.
- The verifier must fail with a nonzero exit code when required checks fail.
- The script must validate the library's existence and nonzero size.
- It must verify the expected JNI exports and the consistency between the actual Kotlin native declarations and native implementation.
- It must account for legitimate dynamically resolved Android/system symbols. Do not reject every undefined dynamic symbol indiscriminately.
- It must not require unnecessary exports or reject valid symbol names without justification.

Use tools available in GitHub Actions, such as "llvm-readelf", "readelf", "llvm-nm", "nm", or other suitable NDK utilities.

Verify ELF class, machine architecture, shared-library format, and Android-target indicators as appropriate. Clearly distinguish what can be proven statically from what requires runtime testing.

Preserve useful existing checks and add regression tests for the verifier where practical.

D. JNI and C wrapper consistency

Inspect "native/bridge.go", "native/bridge.c", "native/libmanjaniq_native.h", and "NativeBridge.kt".

Previously, a source search found no references to "libmanjaniq_native.h" under "native" or "app". Determine whether it is a required source header, a generated c-shared header, or an obsolete artifact. Do not delete it without establishing its role.

Verify that C wrapper functions such as "NewStringUTF_wrapper" are defined and linked correctly.

Check that:

- Every required Kotlin native method has a matching exported JNI symbol.
- Argument and return types agree across Kotlin, JNI, C, and Go.
- The shared library can load without missing required symbols.
- No required C source is omitted from the build.
- The build does not depend on stale generated files.

E. Duplicate or conflicting workflows

The repository has two workflows:

- ".github/workflows/build-debug.yml"
- ".github/workflows/android_ci.yml"

They appear to perform overlapping tasks but use different native library output paths and build commands. One version previously attempted to compile "native/bridge.go" directly, potentially omitting the C wrapper.

Inspect both files in full. Validate their YAML syntax with a real parser if available. Determine the safest maintainable solution.

Prefer one authoritative APK build pipeline unless there is a concrete reason to retain two. If a workflow is redundant, inspect its history/content and make a documented, non-destructive decision about how to prevent conflicting or inconsistent builds. Do not leave contradictory instructions or packaging paths.

Ensure triggers work for the actual "master" branch and retain "workflow_dispatch" where appropriate.

F. Android and Gradle integration

Inspect the Android Gradle configuration, manifest, Kotlin source sets, and existing UI.

Verify:

- Java/Kotlin compatibility settings.
- Correct inclusion of core model/interface sources.
- Correct JNI library packaging and ABI.
- "System.loadLibrary("manjaniq_native")" matches the actual output name.
- Internet or other Android permissions are present only when required by the real implementation.
- The Start/Stop UI correctly invokes the native bridge and reports real engine state.
- Errors from native startup and shutdown are not silently treated as success.
- The application does not report "RUNNING" before startup has actually succeeded.

Do not declare VPN or foreground-service functionality unless the implementation genuinely requires and supports it.

G. Native engine lifecycle and security

Inspect the Go engine, JNI bridge, and existing tests.

Review Start/Stop behavior, repeated calls, cleanup after failure, shutdown and restart, error propagation, synchronization, and concurrent calls where applicable.

Add focused tests for concrete defects, without rewriting the engine unnecessarily.

Review for accidentally tracked secrets, private keys, ".env" files, binaries, unsafe commands, incorrect ".gitignore" rules, and important missing tests. Report sensitive-file paths and exposure types only; never print secret contents.

4. Required validation

Run the checks that are supported by the environment, including:

- Go formatting checks for project-owned Go source files.
- "go vet ./..."
- "go test ./..."
- "git diff --check"
- "git diff --cached --check"
- Workflow YAML syntax validation.
- Native Android ARM64 cross-compilation using the configured NDK.
- Native ELF and JNI symbol validation.
- Gradle configuration and Android unit tests.
- "./gradlew assembleDebug"
- APK contents verification, including the expected "arm64-v8a" native library.
- Verification of the extracted native library from the final APK.

Do not blindly reformat unrelated files or overwrite existing user changes.

If the current environment cannot run Android builds, clearly identify the limitation and ensure the GitHub Actions workflow performs the required validation.

5. Definition of done

The task is complete only when:

1. The native library is built for Android ARM64 using the correct Go module and NDK toolchain.
2. Required JNI/C symbols are linked and verified.
3. Gradle packages the correct library into the APK.
4. The final APK is checked for required files and ABI.
5. The workflow fails on actual build, test, packaging, or verification errors.
6. Conflicting workflows or output paths are resolved.
7. Available tests and static checks have been run and their actual results recorded.
8. Existing user changes and unrelated networking-engine behavior remain preserved.

6. Final report

When finished, provide:

- Root causes discovered.
- Files changed and the purpose of each change.
- Validation commands actually executed.
- Which tests passed and which failed.
- Any checks that could not be executed and why.
- Any remaining risks or manual steps needed in GitHub Actions.

Do not merely describe the changes you would make. Make the safe, necessary changes first, validate them, and then report the real results.
