// Records which classes and methods the app touches at startup and on its busiest screens, and
// writes the list to app/src/release/generated/baselineProfiles/. That file is committed, so
// every release build carries it and the phone compiles those paths at install time.
//
// To refresh it after the screens change shape:
//   ./gradlew :app:generateBaselineProfile
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.arcana.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices.create("pixel6Api34") {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}

// Generation runs on an emulator Gradle manages, never on a connected phone: the test harness
// uninstalls the app when it finishes, and on a real phone that would take the journal with it.
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
