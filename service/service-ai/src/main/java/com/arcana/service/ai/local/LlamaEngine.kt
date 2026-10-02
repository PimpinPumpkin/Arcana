package com.arcana.service.ai.local

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the on-device model. One model is kept loaded between readings, since loading takes a
 * second or two, and dropped when Android asks for memory back while the app is in the background.
 *
 * Everything goes through [converse], which holds a lock for as long as its block runs. That is
 * what keeps two readings from ever using the model at once, and a model from being unloaded or
 * swapped while one is writing.
 */
@Singleton
class LlamaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Mutex()

    // llama.cpp's context belongs to one thread, so all native calls are made on this one.
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "arcana-llm") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + worker)

    private var started = false
    private var cpuLibrary: String? = null
    private var handle = 0L
    private var loaded: File? = null

    /** False on a phone the native library was not built for (a 32-bit system, an x86 emulator). */
    val supported: Boolean = Process.is64Bit() && Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")

    /**
     * The processor-specific libraries this phone may use, best first (see [CpuLibraries]). The
     * first is the one a reading runs on; About names it, which is what to ask for in a bug report.
     */
    val cpuLibraries: List<String> by lazy { if (supported) CpuLibraries.pick() else emptyList() }

    init {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) unloadIfIdle()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = unloadIfIdle()
        })
    }

    /**
     * Loads [file] if it is not the model already in memory, then runs [block] with it.
     *
     * @throws IOException if this phone cannot run the model or the file will not load
     */
    suspend fun <T> converse(file: File, block: suspend Conversation.() -> T): T = lock.withLock {
        withContext(worker) {
            if (!supported) throw IOException("The on-device model needs a 64-bit ARM phone.")
            if (!started) {
                cpuLibrary = LlamaBridge.nativeInit(context.applicationInfo.nativeLibraryDir, cpuLibraries.toTypedArray())
                Log.i(TAG, "CPU library: $cpuLibrary")
                started = true
            }
            if (cpuLibrary == null) throw IOException("The reading engine could not start on this phone's processor.")
            if (handle == 0L || loaded != file) {
                free()
                val (threads, batchThreads) = Threads.pick()
                handle = LlamaBridge.nativeLoad(file.absolutePath, CONTEXT_TOKENS, threads, batchThreads)
                if (handle == 0L) throw IOException("The model file could not be loaded. It may be damaged or of a kind this version cannot run.")
                loaded = file
            }
            Conversation(handle).block()
        }
    }

    /** Frees the model unless a reading is being written right now. */
    fun unloadIfIdle() {
        scope.launch {
            if (lock.tryLock()) {
                try {
                    free()
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    /** Frees the model if [file] is the one loaded, waiting for any reading to finish first. */
    suspend fun release(file: File) = lock.withLock {
        withContext(worker) { if (loaded == file) free() }
    }

    private fun free() {
        if (handle != 0L) LlamaBridge.nativeFree(handle)
        handle = 0L
        loaded = null
    }

    /** One conversation with the loaded model. Only valid inside [converse]. */
    class Conversation internal constructor(private val handle: Long) {
        fun begin(system: String, temperature: Float, topK: Int, topP: Float, repeatPenalty: Float) =
            LlamaBridge.nativeBegin(handle, system.toByteArray(), temperature, topK, topP, repeatPenalty)

        /**
         * Says [text] to the model and opens its reply.
         *
         * @param reserve tokens to keep free for the reply
         * @return how many tokens are waiting to be read with [feed], or null if the conversation
         * no longer fits in the model's context
         */
        fun user(text: String, reserve: Int): Int? {
            val waiting = LlamaBridge.nativeUser(handle, text.toByteArray(), reserve)
            if (waiting == CONTEXT_FULL) return null
            if (waiting < 0) throw IOException("The model could not read the prompt ($waiting).")
            return waiting
        }

        /** Has the model read up to [maxTokens] more of what is waiting. @return how many are left */
        fun feed(maxTokens: Int): Int {
            val left = LlamaBridge.nativeFeed(handle, maxTokens)
            if (left < 0) throw IOException("The model failed while reading ($left).")
            return left
        }

        /** Rules, in llama.cpp's GBNF, that the next reply must follow. Empty for none. */
        fun grammar(gbnf: String) {
            if (!LlamaBridge.nativeGrammar(handle, gbnf.toByteArray())) throw IOException("The reply rules did not parse.")
        }

        /** The next piece of the reply as UTF-8 bytes, or null when the reply is finished. */
        fun next(): ByteArray? = LlamaBridge.nativeNext(handle)

        /** Tells the model's side of the conversation what it ended up saying. */
        fun reply(text: String) = LlamaBridge.nativeReply(handle, text.toByteArray())
    }

    private companion object {
        const val TAG = "ArcanaLlama"

        // A twelve-card spread fits with room to spare. One much larger than that is carried on
        // in a fresh conversation partway through (see LocalLlmInterpreter).
        const val CONTEXT_TOKENS = 4096

        // Session::CONTEXT_FULL in arcana-session.h.
        const val CONTEXT_FULL = -2
    }
}

/**
 * How many threads to use. Phones mix fast and slow cores. Measured on a Pixel 4a 5G (two fast,
 * six slow): writing is quickest on the fast cores alone, since each token waits on the slowest
 * thread, while reading the prompt is work that divides well and is quickest on all of them.
 */
internal object Threads {
    /** @return threads for writing, threads for reading */
    fun pick(): Pair<Int, Int> {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val speeds = (0 until cores).mapNotNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        return pick(cores, speeds)
    }

    fun pick(cores: Int, speeds: List<Long>): Pair<Int, Int> {
        val slowest = speeds.minOrNull()
        val fast = if (slowest == null || speeds.size < cores) cores / 2 else speeds.count { it > slowest }
        // All cores the same speed (or unknown): half of them, as the rest are needed elsewhere.
        val writing = (if (fast == 0) cores / 2 else fast).coerceIn(2, 4).coerceAtMost(cores)
        val reading = cores.coerceIn(writing, 8)
        return writing to reading
    }
}
