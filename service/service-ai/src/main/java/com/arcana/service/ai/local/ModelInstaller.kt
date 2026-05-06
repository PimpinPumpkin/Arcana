package com.arcana.service.ai.local

import android.content.Context
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-device LLM weight file: downloads it, verifies it, exposes
 * progress to the UI, and lets users remove it.
 *
 * Lifecycle: process-scoped singleton. Holds an internal [CoroutineScope] so
 * downloads survive ViewModel teardown but die with the process. (This is a
 * deliberate v1 simplification — no WorkManager, no foreground service. If a
 * user backgrounds the app and Android kills the process during download,
 * they'll need to retry. The .part file IS preserved across attempts but
 * resume requires re-hashing it, which we skip for now: each retry restarts
 * from byte 0.)
 *
 * Companion: [LlamaBridge] in Phase 3 will mmap [modelFile] for inference.
 */
@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    sealed interface State {
        object NotInstalled : State
        data class Downloading(
            val bytesDone: Long,
            val totalBytes: Long,
            val progress: Float,
        ) : State
        data class Installed(val sizeBytes: Long) : State
        data class Failed(val kind: FailureKind, val message: String) : State
    }

    /**
     * What went wrong with a download. Lets the UI show the right copy
     * (e.g. "free up some space" vs "check your network") and decide
     * whether retry is likely to help.
     */
    enum class FailureKind {
        NETWORK,    // offline, DNS failure, connection dropped, read timeout
        SERVER,     // HTTP 4xx / 5xx
        DISK_FULL,  // ENOSPC during write, or pre-flight check rejected install
        CHECKSUM,   // SHA-256 didn't match the manifest
        UNKNOWN,    // catch-all
    }

    val manifest: ModelManifest = ModelManifest.DEFAULT

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }
    private val targetFile: File = File(modelsDir, manifest.fileName)
    private val partFile: File = File(modelsDir, "${manifest.fileName}.part")

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var downloadJob: Job? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _state: MutableStateFlow<State> = MutableStateFlow(initialState())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Path to the installed weight file, or null if not installed. */
    val modelFile: File? get() = targetFile.takeIf { it.exists() && it.length() > 0L }

    private fun initialState(): State {
        // Trust the filesystem: if a finalized file is present at the right
        // size, treat it as installed without re-hashing on every app start.
        // (Re-hashing 400 MB on cold start is too expensive.) The user can
        // manually re-install if they suspect corruption.
        return if (targetFile.exists() && targetFile.length() > 0L) {
            State.Installed(targetFile.length())
        } else {
            State.NotInstalled
        }
    }

    /** Begin (or restart) a download. Idempotent if already downloading. */
    fun install() {
        if (_state.value is State.Downloading) return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                runDownload()
            } catch (e: CancellationException) {
                // Don't strand the .part eating disk if the user cancels.
                partFile.delete()
                _state.value = State.NotInstalled
                throw e
            } catch (e: Throwable) {
                // Free the partial download regardless of failure mode —
                // a half-written file is just disk-pressure with no value.
                partFile.delete()
                val (kind, msg) = classify(e)
                _state.value = State.Failed(kind, msg)
            }
        }
    }

    /** Cancel an in-progress download. The .part file is preserved on disk. */
    fun cancel() {
        downloadJob?.cancel()
        // _state will fall back to NotInstalled in the catch block above.
    }

    /** Delete the model file and any partial download. */
    fun uninstall() {
        downloadJob?.cancel()
        partFile.delete()
        targetFile.delete()
        scope.launch { settingsRepository.setLocalModelInstalled(false) }
        _state.value = State.NotInstalled
    }

    private suspend fun runDownload() {
        // Pre-flight: bail before downloading a single byte if there isn't
        // room. Without this, ENOSPC surfaces as a generic IOException
        // *partway* through the download — confusing UX (progress hits
        // 60%, then "failed").
        val needed = manifest.expectedBytes + FREE_SPACE_HEADROOM_BYTES
        val available = context.filesDir.usableSpace
        if (available < needed) {
            throw DiskFullException(
                "Need ${formatBytes(needed)} free in app storage; have ${formatBytes(available)}.",
            )
        }

        // Always start fresh — see the class header re: skipping resume in v1.
        partFile.delete()

        val request = Request.Builder()
            .url(manifest.downloadUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: manifest.expectedBytes
            val digest = MessageDigest.getInstance("SHA-256")

            FileOutputStream(partFile).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var bytesDone = 0L
                    var lastEmittedBytes = 0L

                    _state.value = State.Downloading(0L, totalBytes, 0f)

                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        digest.update(buf, 0, read)
                        bytesDone += read

                        // Throttle state emissions: at most every ~256 KB
                        // so we don't spam recompositions during a fast
                        // download. UI feels smooth without firing 6000
                        // updates for a 400 MB file.
                        if (bytesDone - lastEmittedBytes >= EMIT_INTERVAL_BYTES ||
                            bytesDone == totalBytes
                        ) {
                            _state.value = State.Downloading(
                                bytesDone = bytesDone,
                                totalBytes = totalBytes,
                                progress = if (totalBytes > 0) bytesDone.toFloat() / totalBytes else 0f,
                            )
                            lastEmittedBytes = bytesDone
                        }
                    }
                    out.flush()
                }
            }

            // Verify checksum if the manifest specifies one.
            if (manifest.sha256.isNotBlank()) {
                val computed = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computed.equals(manifest.sha256, ignoreCase = true)) {
                    partFile.delete()
                    throw IOException(
                        "Checksum mismatch: expected ${manifest.sha256.take(12)}…, " +
                            "got ${computed.take(12)}…",
                    )
                }
            }

            // Atomic-ish: rename .part → final.
            if (!partFile.renameTo(targetFile)) {
                partFile.delete()
                throw IOException("Could not move downloaded file into place")
            }
        }

        settingsRepository.setLocalModelInstalled(true)
        _state.value = State.Installed(targetFile.length())
    }

    private fun classify(e: Throwable): Pair<FailureKind, String> {
        val msg = e.message.orEmpty()
        return when {
            e is DiskFullException ->
                FailureKind.DISK_FULL to msg
            // OS-surfaced "no space left" errors during write.
            msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space left", ignoreCase = true) ->
                FailureKind.DISK_FULL to "Ran out of free space mid-download. Free some space and retry."
            e is UnknownHostException ->
                FailureKind.NETWORK to "Couldn't resolve the download host. Check your internet connection."
            e is ConnectException ->
                FailureKind.NETWORK to "Couldn't connect to the download server. Check your internet connection."
            e is SocketTimeoutException ->
                FailureKind.NETWORK to "Connection timed out during download. Try again on a more stable network."
            msg.startsWith("Download server returned HTTP ") ->
                FailureKind.SERVER to "$msg The model URL may be wrong or the asset isn't published yet."
            msg.startsWith("Checksum mismatch") ->
                FailureKind.CHECKSUM to "Downloaded file didn't match the expected checksum. Network glitch — retry."
            else ->
                FailureKind.UNKNOWN to msg.ifBlank { e::class.java.simpleName }
        }
    }

    private class DiskFullException(message: String) : IOException(message)

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024.0
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.size - 1) {
            value /= 1024.0
            unitIdx++
        }
        return "%.1f %s".format(value, units[unitIdx])
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val EMIT_INTERVAL_BYTES = 256L * 1024L
        // Leave room for filesystem metadata + the .part → final rename
        // and a bit of breathing room beyond the model size itself.
        private const val FREE_SPACE_HEADROOM_BYTES = 64L * 1024L * 1024L
    }
}
