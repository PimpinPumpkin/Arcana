package com.arcana.service.ai.local

import android.content.Context
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var downloadJob: Job? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Active manifest, derived from the user's selection in Settings. Changes
     * here trigger a state recomputation (cancel any in-flight download for
     * the previous manifest, look at the filesystem for the new manifest's
     * file).
     */
    private val _manifest: MutableStateFlow<ModelManifest> = MutableStateFlow(ModelManifest.DEFAULT)
    val manifest: StateFlow<ModelManifest> = _manifest.asStateFlow()

    private val _state: MutableStateFlow<State> = MutableStateFlow(stateForFile(targetFileFor(ModelManifest.DEFAULT)))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            settingsRepository.ai
                .map { ModelManifest.byId(it.localModelId) }
                .distinctUntilChangedBy { it.id }
                .collect { newManifest ->
                    if (newManifest.id != _manifest.value.id) {
                        // Switching models: cancel any download for the
                        // outgoing manifest and recompute state for the
                        // incoming one based on what's already on disk.
                        downloadJob?.cancel()
                        _manifest.value = newManifest
                        _state.value = stateForFile(targetFileFor(newManifest))
                    }
                }
        }
    }

    /** Path to the active manifest's installed weight file, or null if not installed. */
    val modelFile: File?
        get() {
            val f = targetFileFor(_manifest.value)
            return f.takeIf { it.exists() && it.length() > 0L }
        }

    private fun targetFileFor(m: ModelManifest) = File(modelsDir, m.fileName)
    private fun partFileFor(m: ModelManifest) = File(modelsDir, "${m.fileName}.part")

    private fun stateForFile(target: File): State {
        // Trust the filesystem: if a finalized file is present, treat it as
        // installed without re-hashing on every app start. Re-hashing
        // hundreds of MB on cold start is too expensive. Users can manually
        // re-install if they suspect corruption.
        return if (target.exists() && target.length() > 0L) {
            State.Installed(target.length())
        } else {
            State.NotInstalled
        }
    }

    /**
     * Switch the active manifest to [manifest] and immediately install it.
     * Lets the first-tap dialog (and any future "tap install on this card"
     * UI) request a specific size atomically — without this, callers that
     * `setLocalModelId` then `install()` race the manifest collector and
     * can end up installing the previous selection.
     */
    fun installModel(manifest: ModelManifest) {
        scope.launch {
            settingsRepository.setLocalModelId(manifest.id)
            // Force the in-memory manifest update synchronously so the
            // immediate install() call below targets the right file.
            _manifest.value = manifest
            _state.value = stateForFile(targetFileFor(manifest))
            install()
        }
    }

    /** Begin (or restart) a download for the currently-selected manifest. */
    fun install() {
        if (_state.value is State.Downloading) return
        downloadJob?.cancel()
        // Snapshot the manifest at start so a mid-download switch can't
        // confuse which file we're writing to.
        val active = _manifest.value
        downloadJob = scope.launch {
            try {
                runDownload(active)
            } catch (e: CancellationException) {
                partFileFor(active).delete()
                // Only reset state if the user is still on the same manifest.
                // If they switched mid-download, the manifest collector has
                // already pointed _state at the new manifest's actual file
                // state — overwriting that with NotInstalled would clobber
                // it (e.g. claiming the new manifest isn't installed when
                // its file is right there on disk).
                if (_manifest.value.id == active.id) {
                    _state.value = State.NotInstalled
                }
                throw e
            } catch (e: Throwable) {
                partFileFor(active).delete()
                if (_manifest.value.id == active.id) {
                    val (kind, msg) = classify(e)
                    _state.value = State.Failed(kind, msg)
                }
            }
        }
    }

    /** Cancel an in-progress download. */
    fun cancel() {
        downloadJob?.cancel()
    }

    /** Delete the active model's file and any partial download. */
    fun uninstall() {
        downloadJob?.cancel()
        val active = _manifest.value
        partFileFor(active).delete()
        targetFileFor(active).delete()
        scope.launch { settingsRepository.setLocalModelInstalled(false) }
        _state.value = State.NotInstalled
    }

    private suspend fun runDownload(activeManifest: ModelManifest) {
        val targetFile = targetFileFor(activeManifest)
        val partFile = partFileFor(activeManifest)

        // Pre-flight: bail before downloading a single byte if there isn't
        // room. Without this, ENOSPC surfaces as a generic IOException
        // *partway* through the download — confusing UX (progress hits
        // 60%, then "failed").
        val needed = activeManifest.expectedBytes + FREE_SPACE_HEADROOM_BYTES
        val available = context.filesDir.usableSpace
        if (available < needed) {
            throw DiskFullException(
                "Need ${formatBytes(needed)} free in app storage; have ${formatBytes(available)}.",
            )
        }

        // Always start fresh — see the class header re: skipping resume in v1.
        partFile.delete()

        val request = Request.Builder()
            .url(activeManifest.downloadUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: activeManifest.expectedBytes
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

                        // Throttle state emissions to every ~256 KB so we
                        // don't spam recompositions during a fast download.
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
            if (activeManifest.sha256.isNotBlank()) {
                val computed = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computed.equals(activeManifest.sha256, ignoreCase = true)) {
                    partFile.delete()
                    throw IOException(
                        "Checksum mismatch: expected ${activeManifest.sha256.take(12)}…, " +
                            "got ${computed.take(12)}…",
                    )
                }
            }

            if (!partFile.renameTo(targetFile)) {
                partFile.delete()
                throw IOException("Could not move downloaded file into place")
            }
        }

        settingsRepository.setLocalModelInstalled(true)
        // If the user just installed a model while on the rule-based fallback,
        // they almost certainly want the AI now. Flip the backend so the next
        // Interpret tap actually uses the model they just downloaded — no
        // round-trip through Settings required.
        val current = settingsRepository.ai.first()
        if (current.backendType == AiBackendType.RULE_BASED) {
            settingsRepository.setAiBackend(AiBackendType.LOCAL_LLM)
        }
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
