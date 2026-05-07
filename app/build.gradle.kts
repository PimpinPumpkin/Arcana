import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.arcana.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arcana.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Real release signingConfig, populated from env vars set by CI:
    //   ARCANA_KEYSTORE_PATH       — absolute path to a decoded .jks file
    //   ARCANA_KEYSTORE_PASSWORD   — store password (also used as key password)
    //   ARCANA_KEY_ALIAS           — alias inside the keystore (defaults to "arcana")
    // When those aren't set (local dev), we fall back to the debug keystore
    // below — local builds still work, they're just signed with a different,
    // per-machine cert (which is fine for `adb install` during development).
    signingConfigs {
        create("releaseFromEnv") {
            val path = System.getenv("ARCANA_KEYSTORE_PATH")
            if (!path.isNullOrBlank() && File(path).exists()) {
                storeFile = File(path)
                storePassword = System.getenv("ARCANA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARCANA_KEY_ALIAS") ?: "arcana"
                keyPassword = System.getenv("ARCANA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val envSigning = signingConfigs.getByName("releaseFromEnv")
            signingConfig = if (envSigning.storeFile?.exists() == true) {
                envSigning
            } else {
                // Local dev path: per-machine debug keystore. The Obtainium
                // upgrade conflict only matters between releases that *both*
                // come from CI, so this fallback is fine for development.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Force the AGP-default `extractNativeLibs=false` off. We need
            // the .so files extracted to a real filesystem path at install
            // time, because llama.cpp's ggml_backend_load_all_from_path
            // uses opendir() to enumerate the CPU-variant backends. With
            // extraction off, nativeLibraryDir resolves to an APK-internal
            // virtual path that opendir can't walk, no backends load, and
            // model load fails with "no compatible backend".
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-ui"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-spreads"))
    implementation(project(":feature:feature-journal"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":service:service-ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
