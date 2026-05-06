package com.arcana.service.ai.local

/**
 * Kotlin-side bridge to the native llama.cpp wrapper (`libarcana-llama.so`).
 *
 * Phase 1 only exposes [nativeGreeting] — a smoke test that proves the JNI
 * library is loadable and llama.cpp's symbols are reachable from this process.
 *
 * Real model load + inference + token streaming functions land in Phase 3.
 */
internal object LlamaBridge {
    init {
        System.loadLibrary("arcana-llama")
    }

    @JvmStatic
    external fun nativeGreeting(): String
}
