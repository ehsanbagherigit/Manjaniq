# Manjaniq

Modular network utility application for Android. Its feature is a native
UI controlling the genuine RSTA SNI Spoof Go engine, integrated as an
embeddable JNI library at `engine/rstaspoof/` (the pristine upstream file
is kept, for reference/diffing, at `rsta-source/rstaspoof.go`). The app is a
local proxy controller: point other apps at the configured listen address;
it does not redirect all device traffic and needs no root.

- Kotlin + Jetpack Compose, Go/cgo JNI bridge (`native/`), foreground service
- Application ID / namespace: `com.manjaniq`
- minSdk 26, targetSdk/compileSdk 35, ABI: arm64-v8a
- App models live under `app/src/main/java/com/manjaniq/core/`
  (the project-root `core/` directory holds reference copies that are
  deliberately not compiled — see `app/build.gradle.kts`).

See `ARCHITECTURE.md`, `ROADMAP.md`, `UI_DESIGN_GUIDE.md`, and `report.md`
for the design and progress documentation.

## Build (GitHub Actions — recommended)

Debug APKs are built automatically by the workflow at
`.github/workflows/build-debug.yml` on every push and pull request:

1. Push this repository to GitHub. The workflow builds the Go engine
   (`engine/rstaspoof`) into `libmanjaniq_native.so` (arm64-v8a) with the
   NDK and packages it into the APK automatically.
2. Open the repository's **Actions** tab and wait for the **Build Debug APK**
   workflow.
3. Download the `manjaniq-debug-apk` artifact, which contains
   `app-debug.apk`.

No signing, publishing, or secrets are configured; the workflow only produces
an unsigned debug APK.

## Build (local)

Requires JDK 17. The Android SDK is not required to be installed manually when
building through GitHub Actions; for local builds the Android Gradle Plugin
will download SDK components only if an SDK is installed (for example via
Android Studio, or by setting `ANDROID_HOME` and installing
`platforms;android-35` and `build-tools;35.0.0`).

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Useful commands:

```bash
./gradlew --version     # Verify wrapper and Gradle version (8.9)
./gradlew help          # Sanity-check project configuration
./gradlew lint          # Run Android lint
```

## Toolchain versions

| Component | Version |
|---|---|
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.0.21 |
| Compose Compiler | Kotlin Compose plugin 2.0.21 |
| Compose BOM | 2024.09.03 |
| JDK | 17 |

## Project layout

```
Manjaniq/
├── app/                          # Android application module
│   └── src/main/
│       ├── java/com/manjaniq/    # MainActivity + Compose UI
│       ├── res/                  # Strings, theme
│       └── AndroidManifest.xml
├── core/
│   ├── models/                   # EngineState, RSTAConfig, OperationResult
│   ├── interfaces/               # EngineController, EngineStatusProvider, Logger
│   └── common/                   # Reserved (empty)
├── engine/rstaspoof/             # Genuine RSTA engine (embeddable Go package)
├── native/                       # JNI bridge (bridge.go + bridge.c) and verifier
├── rsta-source/                  # Pristine upstream RSTA source (reference only)
├── .github/workflows/            # CI build
└── *.md                          # Architecture, roadmap, UI guide, report
```
