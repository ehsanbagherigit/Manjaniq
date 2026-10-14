# Manjaniq

Modular network utility application for Android. The initial feature is a native
UI for controlling the existing RSTA SNI Spoof Go engine
(`engine/rstaspoof/`, a separate repository kept unmodified).

- Kotlin + Jetpack Compose
- Application ID / namespace: `com.manjaniq`
- minSdk 26, targetSdk/compileSdk 35
- Core models and interfaces live in `core/models/` and `core/interfaces/`
  and are compiled into the `:app` module via Gradle source-set configuration
  (no files were moved).

See `ARCHITECTURE.md`, `ROADMAP.md`, `UI_DESIGN_GUIDE.md`, and `report.md`
for the design and progress documentation.

## Build (GitHub Actions — recommended)

Debug APKs are built automatically by the workflow at
`.github/workflows/build-debug.yml` on every push and pull request:

1. Push this repository to GitHub (the nested `engine/rstaspoof/.git` directory
   is not required by the build).
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
├── feature-rsta/                 # Reserved for the RSTA feature module (empty)
├── engine/rstaspoof/             # Existing Go engine (separate git repo, unmodified)
├── .github/workflows/            # CI build
└── *.md                          # Architecture, roadmap, UI guide, report
```
