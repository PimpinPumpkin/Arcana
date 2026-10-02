package com.arcana.service.ai.local

/**
 * The native side of the on-device model (`libarcana-llama.so`, built from `src/main/cpp`). Every
 * function here maps to a method of the session in `arcana-session.h`, which documents each.
 *
 * Text is passed as UTF-8 bytes. Nothing outside [LlamaEngine] should call this: a session may
 * only be used from one thread, and the engine is what guarantees that.
 */
internal object LlamaBridge {
    init {
        System.loadLibrary("arcana-llama")
    }

    /**
     * @param libraries CPU libraries to try, best first (see [CpuLibraries])
     * @return the one that loaded, or null if this processor could run none of them
     */
    @JvmStatic external fun nativeInit(nativeLibDir: String, libraries: Array<String>): String?

    /** @return a session handle, or 0 if the file is not a model llama.cpp can run */
    @JvmStatic external fun nativeLoad(path: String, contextTokens: Int, threads: Int, batchThreads: Int): Long

    @JvmStatic external fun nativeFree(handle: Long)

    @JvmStatic external fun nativeBegin(handle: Long, system: ByteArray, temperature: Float, topK: Int, topP: Float, repeatPenalty: Float)

    @JvmStatic external fun nativeUser(handle: Long, text: ByteArray, reserve: Int): Int

    @JvmStatic external fun nativeFeed(handle: Long, maxTokens: Int): Int

    @JvmStatic external fun nativeGrammar(handle: Long, gbnf: ByteArray): Boolean

    @JvmStatic external fun nativeNext(handle: Long): ByteArray?

    @JvmStatic external fun nativeReply(handle: Long, text: ByteArray)

    @JvmStatic external fun nativeContextLeft(handle: Long): Int
}
