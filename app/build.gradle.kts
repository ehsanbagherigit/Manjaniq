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

        // The native library is built for arm64-v8a only (see CI);
        // declaring it avoids installing on devices that cannot
        // load libmanjaniq_native.so.
        ndk {
            abiFilters += "arm64-v8a"
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

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
