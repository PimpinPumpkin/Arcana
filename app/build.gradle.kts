import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.arcana.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arcana.app"
        minSdk = 26
        targetSdk = 35
        // CI passes -PappVersionCode / -PappVersionName, derived from the workflow run number so
        // every channel sits on one rising line. Local builds stay at 1 on purpose: lower than any
        // published build, so a phone that had a dev build can always take a real one.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.7.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment (CI secrets, or exported by hand). Without it the
    // release build falls back to the debug key, which still installs for testing but cannot
    // update a properly signed copy.
    //   ARCANA_KEYSTORE_PATH / ARCANA_KEYSTORE_PASSWORD / ARCANA_KEY_ALIAS
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
            val fromEnv = signingConfigs.getByName("releaseFromEnv")
            signingConfig = if (fromEnv.storeFile?.exists() == true) fromEnv else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        // llama.cpp finds its per-CPU math libraries by listing the app's native library folder,
        // which only works when the libraries are extracted at install time.
        jniLibs.useLegacyPackaging = true
    }

    testOptions.unitTests.isReturnDefaultValues = true
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

    // Compiles the committed baseline profile ahead of time when the app is installed. Copies
    // installed from a release page or Obtainium get no cloud profile from a store, and without
    // this the first launches run interpreted and the card grid stutters.
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

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
    implementation(libs.hilt.viewmodel.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
