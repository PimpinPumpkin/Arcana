plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.arcana.core.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-database"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    // SAF document tree access for the deck importer (folder picker scan).
    implementation(libs.androidx.documentfile)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
