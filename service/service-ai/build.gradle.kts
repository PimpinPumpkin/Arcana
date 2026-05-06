plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.arcana.service.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // Most Android phones from the last decade are arm64-v8a. We skip
        // x86_64 (emulator-only) and 32-bit ABIs to keep the APK small —
        // the model is big enough on its own.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"

                // Build the helpers in llama.cpp/common/ that we use for
                // sampling and chat templates in Phase 3. Skip the rest.
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_BUILD_TESTS=OFF"
                arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                arguments += "-DLLAMA_BUILD_SERVER=OFF"
                arguments += "-DLLAMA_BUILD_TOOLS=OFF"
                arguments += "-DLLAMA_CURL=OFF"

                // GGML knobs aligned with llama.cpp's own Android example:
                // GGML_NATIVE off because we're cross-compiling for ARM,
                // CPU_ALL_VARIANTS + BACKEND_DL so the right kernel is
                // chosen at runtime per device, LLAMAFILE off since it's
                // x86-only.
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            // llama.cpp's `common` static lib pulls in some metadata files
            // that are duplicated across deps; drop them.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
