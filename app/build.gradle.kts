plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The ABIs this build packages, from `-Pmanjaniq.abis=arm64-v8a,x86_64`.
 *
 * A physical device is arm64-v8a, and every native file in the APK is ~2-3x the
 * size it needs to be once a second architecture is carried for an emulator:
 * libaether.so (Rust + BoringSSL), libmanjaniq_native.so (Go), libc++_shared.so
 * and the Psiphon helper each exist once per ABI. CI therefore assembles the
 * deliverable with `-Pmanjaniq.abis=arm64-v8a` and a second, x86_64-only APK for
 * the emulator gate (.github/workflows/build-debug.yml), which is why the
 * default here is still both: a plain `./gradlew assembleDebug` must go on
 * producing an APK an emulator can install.
 */
val manjaniqAbis: List<String> =
    (project.findProperty("manjaniq.abis") as String?)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf("arm64-v8a", "x86_64")

android {
    namespace = "com.manjaniq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manjaniq"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only the architectures this build actually ships (see [manjaniqAbis]).
        ndk {
            abiFilters.clear()
            abiFilters += manjaniqAbis
        }
    }

    // Aether native library: built via aether-wrapper (cargo ndk) in CI.
    // Local builds without Rust will have no libaether.so; app degrades gracefully (isNativeAvailable=false).
    // CI must run: cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release  in aether-wrapper
    // Packaging keeps both RSTA (libmanjaniq_native.so) and Aether (libaether.so) in lib/arm64-v8a/
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    packaging {
        jniLibs {
            /**
             * True so the APK is installed with `extractNativeLibs=true`, i.e. the
             * native libraries are written into the app's read-only library
             * directory instead of being mapped from inside the APK.
             *
             * Android 10 (API 29) enforces W^X: an app that targets 29 or newer may
             * not `execve` a file in its own writable data directory. Aether runs
             * two helper processes - a Tor pluggable transport
             * (`bridges.rs` -> `--tor-pt`) and `psiphon-tunnel-core`
             * (`psiphon.rs:749` -> `--psiphon-bin`) - and those arrive as
             * `lib<name>.so` entries in jniLibs (scripts/ci/build_helpers.sh).
             * Without extraction they are not files on disk at all, so they cannot
             * be started; with it, they live in the one directory the platform both
             * populates and allows exec from.
             *
             * `AetherHelpers` only ever resolves a helper inside that directory and
             * refuses an executable that sits in `filesDir`, so nothing here weakens
             * the W^X boundary: the data directory still holds no executable code.
             *
             * Cost: the libraries occupy disk twice (once in the APK, once
             * extracted) and installation is a little slower. libaether.so itself
             * loads either way.
             */
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The dependency metadata blob AGP can write into the APK (an app-store
    // reporting feature) is dead weight for a local/CI build.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Only five icons are drawn (Home, Settings, Info, List, Menu), all of which
    // live in material-icons-core. `material-icons-extended` carries ~10k vector
    // icons - several megabytes of dex - and one icon used to need it.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    // ui-tooling (the preview/debug inspector) is not packaged: this project has
    // no @Preview composables, and it only ever shipped into the debug APK.

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    // Host-side launch of the real UI under Robolectric (RobolectricLaunchTest)
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    // Compose BOM must be declared for androidTest as well to resolve ui-test-junit4 version
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
