plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.manjaniq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manjaniq"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Instrumentation tests run with the standard AndroidJUnitRunner;
        // the test applicationId defaults to <applicationId>.test
        // (com.manjaniq.test) and is signed with the same debug key as
        // the app when both APKs are produced by one Gradle invocation
        // (connectedDebugAndroidTest) — never mix adb installs of APKs
        // built by different jobs/keystores.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The native library is built for arm64-v8a (real devices) and
        // x86_64 (CI emulator); declaring the filters avoids installing
        // on devices that cannot load libmanjaniq_native.so.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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

    testOptions {
        unitTests {
            // Robolectric-based startup tests need real resource access.
            isIncludeAndroidResources = true
        }
    }

    // Core sources are compiled from the copies under app/src/main/java.
    // Do not add srcDirs pointing at the project-root "core/" copies: they
    // declare the same classes (RSTAConfig, EngineState, OperationResult,
    // EngineController) and would cause "Duplicate class" build failures.
    //
    // jniLibs: no override. AGP's default source dir app/src/main/jniLibs is
    // used, which is exactly where the CI workflow writes the built
    // libmanjaniq_native.so. Pointing jniLibs.srcDirs elsewhere replaced the
    // default and silently dropped the library from the APK.
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

    // One Compose BOM platform object, applied to every source set
    // that declares Compose dependencies. Declaring the BOM only
    // for the implementation configuration leaves the androidTest
    // configuration without a version source, so a version-less
    // "androidx.compose.ui:ui-test-junit4" fails resolution with
    // "Could not find androidx.compose.ui:ui-test-junit4:." (CI
    // regression, run 39168562782). The BOM must be added to each
    // configuration separately — platform() dependencies do not
    // cross configurations.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for local unit tests (android.jar stubs throw).
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
