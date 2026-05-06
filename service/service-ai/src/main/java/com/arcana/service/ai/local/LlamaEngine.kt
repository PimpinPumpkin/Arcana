package com.arcana.service.ai.local

import android.content.Context
import com.arcana.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-singleton wrapper around [LlamaBridge].
 *
 * - Initializes the native backend exactly once per process (lazy on first
 *   model load — we only want to pay the cost if the user actually opts in
 *   to local inference).
 * - Caches one loaded model. Switching models or reloading discards the
 *   previous handle.
 * - Serializes load/free/generate via a [Mutex] because llama_context isn't
 *   thread-safe; back-to-back generations must finish their token loop
 *   before another starts.
 * - Exposes generation as a [Flow] of token pieces. Cancelling the flow's
 *   collector calls back into the native side via [LlamaBridge.nativeStopGeneration]
 *   so we don't keep grinding tokens we'll throw away.
 */
@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    private val lock = Mutex()
    private val initialized = AtomicBoolean(false)
    private var sessionHandle: Long = 0L
    private var loadedFile: File? = null

    private fun ensureBackendInitialized() {
        if (initialized.compareAndSet(false, true)) {
            LlamaBridge.nativeInit(context.applicationInfo.nativeLibraryDir)
        }
    }

    /**
     * Load [file] into a fresh native session. If [file] is already loaded,
     * this is a no-op and returns true. Returns false if the model couldn't
     * be loaded (corrupt file, OOM, unsupported architecture).
     */
    suspend fun loadModel(file: File, nCtx: Int = DEFAULT_N_CTX): Boolean = lock.withLock {
        withContext(dispatchers.io) {
            ensureBackendInitialized()
            if (sessionHandle != 0L && loadedFile == file) return@withContext true

            // Tear down any previous session before loading a new one — a
            // 0.5B model is ~500 MB resident, two of them would push budget
            // phones into OOM territory.
            disposeLocked()

            val handle = LlamaBridge.nativeLoadModel(file.absolutePath, nCtx)
            if (handle == 0L) return@withContext false
            sessionHandle = handle
            loadedFile = file
            true
        }
    }

    /** True iff a model is currently loaded in the native session. */
    val isReady: Boolean get() = sessionHandle != 0L

    /**
     * Stream tokens for [prompt] until the model emits EOS or hits [maxTokens].
     * The collecting coroutine is the source of truth for liveness — if the
     * collector is cancelled we tell the native side to stop sampling.
     */
    fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): Flow<String> = flow {
        val handle = sessionHandle
        if (handle == 0L) throw IllegalStateException("LlamaEngine: no model loaded")

        val rc = LlamaBridge.nativeStartGeneration(handle, prompt, maxTokens)
        if (rc != 0) throw IOException("LlamaEngine: nativeStartGeneration failed (rc=$rc)")

        try {
            while (currentCoroutineContext().isActive) {
                val piece = LlamaBridge.nativeNextToken(handle) ?: break
                emit(piece)
            }
        } finally {
            LlamaBridge.nativeStopGeneration(handle)
        }
    }.flowOn(dispatchers.io)

    /** Free the loaded model and the native context. Idempotent. */
    suspend fun unload() = lock.withLock { disposeLocked() }

    private fun disposeLocked() {
        if (sessionHandle != 0L) {
            LlamaBridge.nativeFreeModel(sessionHandle)
            sessionHandle = 0L
            loadedFile = null
        }
    }

    companion object {
        // 2048 is plenty of context for our system + user prompt + a single
        // multi-paragraph reading. Bumping to 4096 doubles RAM use without
        // changing the answer.
        private const val DEFAULT_N_CTX = 2048
        // Tarot interpretations don't run longer than ~600 tokens in
        // practice; cap at 800 for safety so a runaway loop can't burn a
        // user's battery indefinitely.
        private const val DEFAULT_MAX_TOKENS = 800
    }
}
