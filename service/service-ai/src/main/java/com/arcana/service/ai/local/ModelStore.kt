package com.arcana.service.ai.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The model files on the phone: which are installed, which are on their way, and the downloads
 * themselves. Each model has its own state, so choosing one never disturbs another.
 *
 * A download that is interrupted keeps what arrived and carries on from there, whether it was
 * paused, lost its connection, or the app was closed. [ModelDownloadService] keeps the phone
 * awake while one is running.
 */
@Singleton
class ModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    sealed interface State {
        data object Missing : State

        /** Part of the file is on the phone and the download is not running. */
        data class Paused(val doneBytes: Long, val totalBytes: Long) : State

        data class Downloading(val doneBytes: Long, val totalBytes: Long) : State {
            val fraction: Float get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        }

        /** The whole file has arrived and is being checked against its hash. */
        data object Verifying : State
        data object Installed : State
        data class Failed(val kind: FailureKind, val message: String) : State
    }

    enum class FailureKind { NETWORK, SERVER, DISK_FULL, CHECKSUM, UNKNOWN }

    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val jobs = HashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()

    // Downloads the user started and did not pause. One cut short by the app closing is picked
    // up again at the next launch; one they paused stays paused.
    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private var wanted: Set<String>
        get() = prefs.getStringSet(WANTED, emptySet())!!
        set(value) = prefs.edit().putStringSet(WANTED, value).apply()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // No limit on the whole call: a gigabyte on a slow connection takes as long as it takes.
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private val _imported = MutableStateFlow(readImported())

    /** The model the user supplied themselves, if there is one. */
    val imported: StateFlow<ModelSpec?> = _imported.asStateFlow()

    private val _states = MutableStateFlow(ModelCatalog.all.associate { it.id to onDisk(it) })
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    /**
     * Everything the picker should list: the models on offer, a retired one if any of it is on
     * the phone, then the user's own model.
     */
    val specs: List<ModelSpec>
        get() = ModelCatalog.all.filter { !it.retired || state(it) != State.Missing } + listOfNotNull(_imported.value)

    fun state(spec: ModelSpec): State =
        if (spec.id == ModelCatalog.IMPORTED_ID) _states.value[spec.id] ?: State.Installed else _states.value[spec.id] ?: State.Missing

    fun file(spec: ModelSpec): File? = File(dir, spec.fileName).takeIf { state(spec) == State.Installed && it.exists() }

    /**
     * The model readings use when [chosenId] is the one picked in Settings: that one if it is
     * installed, otherwise the first installed one, with retired models last.
     */
    fun inUse(chosenId: String): ModelSpec? =
        specs.sortedWith(compareByDescending<ModelSpec> { it.id == chosenId }.thenBy { it.retired }).firstOrNull { file(it) != null }

    suspend fun active(): Pair<ModelSpec, File>? {
        val spec = inUse(settings.ai.first().localModelId) ?: return null
        return file(spec)?.let { spec to it }
    }

    /** Continues any download that was interrupted rather than paused. */
    fun resumeInterrupted() {
        val unfinished = wanted
        ModelCatalog.all.filter { it.id in unfinished }.forEach(::install)
    }

    @Synchronized
    fun install(spec: ModelSpec) {
        if (spec.urls.isEmpty() || state(spec) == State.Installed || jobs[spec.id]?.isActive == true) return
        wanted = wanted + spec.id
        set(spec, State.Downloading(partFile(spec).length(), spec.bytes))
        ModelDownloadService.start(context)
        jobs[spec.id] = scope.launch {
            try {
                download(spec)
                set(spec, State.Installed)
                wanted = wanted - spec.id
                installed(spec)
            } catch (e: CancellationException) {
                set(spec, onDisk(spec))
                throw e
            } catch (e: Throwable) {
                // Leaving the app's own cancel aside, OkHttp reports a cancelled call as a plain
                // IOException; a download that was paused must not be shown as failed.
                if (!isActive) set(spec, onDisk(spec)) else set(spec, failure(e))
            } finally {
                calls.remove(spec.id)
            }
        }
    }

    /** Stops a download. What has arrived is kept, so [install] carries on from there. */
    @Synchronized
    fun pause(spec: ModelSpec) {
        wanted = wanted - spec.id
        calls.remove(spec.id)?.cancel()
        jobs.remove(spec.id)?.cancel()
        if (state(spec) !is State.Installed) set(spec, onDisk(spec))
    }

    /** Removes a model and anything partly downloaded for it. */
    @Synchronized
    fun delete(spec: ModelSpec) {
        wanted = wanted - spec.id
        calls.remove(spec.id)?.cancel()
        jobs.remove(spec.id)?.cancel()
        File(dir, spec.fileName).delete()
        partFile(spec).delete()
        if (spec.id == ModelCatalog.IMPORTED_ID) {
            prefs.edit().remove(IMPORTED_NAME).apply()
            _imported.value = null
            _states.update { it - spec.id }
        } else {
            set(spec, State.Missing)
        }
    }

    sealed interface ImportResult {
        data class Done(val spec: ModelSpec) : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    /**
     * Installs a model from a file the user already has. A file that is exactly one of the
     * catalog's models is installed as that model, which is how to avoid the in-app download;
     * any other GGUF chat model is kept as the user's own.
     */
    suspend fun importGguf(uri: Uri): ImportResult = withContext(dispatchers.io) {
        val staging = File(dir, ".import.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { out -> copy(input, out::write, digest) }
            } ?: return@withContext ImportResult.Failed("Could not open that file.")
            val magic = ByteArray(4)
            staging.inputStream().use { it.read(magic) }
            if (!magic.contentEquals("GGUF".toByteArray())) {
                return@withContext ImportResult.Failed("That is not a GGUF model file.")
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val known = ModelCatalog.all.firstOrNull { it.sha256 == hash }
            val spec = known ?: ModelCatalog.imported(displayName(uri)?.removeSuffix(".gguf") ?: "Imported model", copied)
            synchronized(this@ModelStore) {
                jobs.remove(spec.id)?.cancel()
                val target = File(dir, spec.fileName)
                target.delete()
                if (!staging.renameTo(target)) return@withContext ImportResult.Failed("Could not save the model.")
                if (known == null) {
                    prefs.edit().putString(IMPORTED_NAME, spec.title).apply()
                    _imported.value = spec
                }
                wanted = wanted - spec.id
                set(spec, State.Installed)
            }
            installed(spec)
            ImportResult.Done(spec)
        } catch (e: Exception) {
            ImportResult.Failed(if (isDiskFull(e)) "Not enough free space for that file." else "Import failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            staging.delete()
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun partFile(spec: ModelSpec) = File(dir, spec.fileName + ".part")

    private fun set(spec: ModelSpec, state: State) = _states.update { it + (spec.id to state) }

    /** What the files on disk say, with no download running. */
    private fun onDisk(spec: ModelSpec): State {
        val target = File(dir, spec.fileName)
        val part = partFile(spec)
        return when {
            target.exists() && target.length() > 0 -> State.Installed
            part.exists() && part.length() > 0 -> State.Paused(part.length(), spec.bytes)
            else -> State.Missing
        }
    }

    private fun readImported(): ModelSpec? {
        val file = File(dir, ModelCatalog.IMPORTED_FILE)
        if (!file.exists() || file.length() == 0L) return null
        return ModelCatalog.imported(prefs.getString(IMPORTED_NAME, null) ?: "Imported model", file.length())
    }

    /** A first model turns the feature on: nobody downloads one to keep using the built-in text. */
    private suspend fun installed(spec: ModelSpec) {
        val ai = settings.ai.first()
        if (ai.backendType == AiBackendType.RULE_BASED) settings.setAiBackend(AiBackendType.LOCAL_LLM)
        val chosen = specs.firstOrNull { it.id == ai.localModelId }
        if (chosen == null || file(chosen) == null) settings.setLocalModelId(spec.id)
    }

    private suspend fun download(spec: ModelSpec) {
        val part = partFile(spec)
        var attempt = 0
        while (true) {
            try {
                fetch(spec, part)
                break
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                // A dropped connection is worth a few quiet retries before the user is told.
                if (e is ServerException || e is DiskFullException || ++attempt > RETRIES) throw e
                delay(RETRY_WAIT_MS * attempt)
            }
        }
        set(spec, State.Verifying)
        if (sha256(part) != spec.sha256) {
            part.delete()
            throw ChecksumException()
        }
        val target = File(dir, spec.fileName)
        if (!part.renameTo(target)) throw IOException("Could not move the downloaded file into place.")
    }

    /** Appends to [part] from wherever a previous attempt stopped, trying each source in turn. */
    private suspend fun fetch(spec: ModelSpec, part: File) {
        var have = if (part.exists()) part.length() else 0L
        if (have > spec.bytes) {
            part.delete()
            have = 0
        }
        if (have == spec.bytes) return
        val needed = spec.bytes - have + HEADROOM_BYTES
        if (dir.usableSpace < needed) throw DiskFullException("Needs ${megabytes(needed)} MB free; the phone has ${megabytes(dir.usableSpace)} MB.")

        var last: IOException? = null
        for (url in spec.urls) {
            val request = Request.Builder().url(url).header("User-Agent", "Arcana")
                .apply { if (have > 0) header("Range", "bytes=$have-") }
                .build()
            val call = client.newCall(request).also { calls[spec.id] = it }
            try {
                call.execute().use { response ->
                    if (response.code == 416) {
                        // The server says there is nothing past what we hold. Start this file over.
                        part.delete()
                        throw IOException("The download could not be resumed.")
                    }
                    if (!response.isSuccessful) throw ServerException("The download server answered ${response.code}.")
                    if (response.code == 200 && have > 0) {
                        // The server ignored the range and is sending the whole file again.
                        part.delete()
                        have = 0
                    }
                    val body = response.body ?: throw IOException("The download server sent nothing.")
                    RandomAccessFile(part, "rw").use { out ->
                        out.seek(have)
                        body.byteStream().use { input ->
                            var reported = have
                            have += copy(input, { b, off, n ->
                                out.write(b, off, n)
                                val now = out.filePointer
                                if (now - reported >= REPORT_EVERY) {
                                    reported = now
                                    set(spec, State.Downloading(now, spec.bytes))
                                }
                            })
                        }
                    }
                }
                if (have != spec.bytes) throw IOException("The connection dropped.")
                return
            } catch (e: ServerException) {
                last = e // this source does not have it; the next might
            }
        }
        throw last ?: IOException("No download source is set for this model.")
    }

    private suspend fun copy(input: InputStream, write: (ByteArray, Int, Int) -> Unit, digest: MessageDigest? = null): Long {
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            write(buffer, 0, n)
            digest?.update(buffer, 0, n)
            total += n
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun failure(e: Throwable): State.Failed = when {
        e is DiskFullException -> State.Failed(FailureKind.DISK_FULL, e.message!!)
        isDiskFull(e) -> State.Failed(FailureKind.DISK_FULL, "The phone ran out of space. Free some and try again.")
        e is ChecksumException -> State.Failed(FailureKind.CHECKSUM, "The file arrived damaged. Try again.")
        e is ServerException -> State.Failed(FailureKind.SERVER, e.message!!)
        e is UnknownHostException -> State.Failed(FailureKind.NETWORK, "No connection. It will carry on from where it stopped.")
        e is IOException -> State.Failed(FailureKind.NETWORK, "The connection dropped. It will carry on from where it stopped.")
        else -> State.Failed(FailureKind.UNKNOWN, e.message ?: e.javaClass.simpleName)
    }

    private fun isDiskFull(e: Throwable): Boolean =
        e.message.orEmpty().let { it.contains("ENOSPC", true) || it.contains("No space left", true) }

    private fun megabytes(bytes: Long) = bytes / (1024 * 1024)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private class ServerException(message: String) : IOException(message)
    private class DiskFullException(message: String) : IOException(message)
    private class ChecksumException : IOException("Checksum mismatch")

    private companion object {
        const val WANTED = "wanted"
        const val IMPORTED_NAME = "imported_name"
        const val REPORT_EVERY = 512 * 1024L
        const val HEADROOM_BYTES = 64L * 1024 * 1024
        const val RETRIES = 3
        const val RETRY_WAIT_MS = 4_000L
    }
}
