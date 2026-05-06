package com.arcana.service.ai.local

/**
 * Kotlin-side bridge to the native llama.cpp wrapper (`libarcana-llama.so`).
 *
 * Each function maps 1:1 to a `Java_com_arcana_service_ai_local_LlamaBridge_*`
 * symbol in `arcana-llama.cpp`. See the header in that file for the contract
 * around session handles and threading.
 *
 * Most callers should not touch this directly — go through [LlamaEngine],
 * which serializes access and returns proper Kotlin [kotlinx.coroutines.flow.Flow]s.
 */
internal object LlamaBridge {
    init {
        System.loadLibrary("arcana-llama")
    }

    @JvmStatic
    external fun nativeGreeting(): String

    @JvmStatic
    external fun nativeInit(nativeLibDir: String)

    /** Returns a non-zero session handle on success, or 0 on failure. */
    @JvmStatic
    external fun nativeLoadModel(modelPath: String, nCtx: Int): Long

    @JvmStatic
    external fun nativeFreeModel(handle: Long)

    /** Returns 0 on success, negative error code otherwise. */
    @JvmStatic
    external fun nativeStartGeneration(handle: Long, prompt: String, maxTokens: Int): Int

    /** Returns the next token's UTF-8 piece, or null on EOS / cap / error. */
    @JvmStatic
    external fun nativeNextToken(handle: Long): String?

    /** Sets a stop flag the next [nativeNextToken] call observes. Thread-safe. */
    @JvmStatic
    external fun nativeStopGeneration(handle: Long)
}
