package com.arcana.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.data.repository.CustomDeckStore.Companion.isImage
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.CardFileNames
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.repository.CardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Brings card images into a deck and takes decks back out.
 *
 * Which card a file is for is read from its name by [CardFileNames], which knows the app's own
 * names and the common ways decks are named. A file called `back` is the card back. Anything
 * else in the folder or archive is ignored, and cards with no file show their name instead.
 * Whatever a file was called, it is stored under the card's own name.
 */
@Singleton
class DeckImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: CustomDeckStore,
    private val cardRepository: CardRepository,
    private val dispatchers: DispatcherProvider,
) {
    sealed interface Result {
        data class Success(
            val deckId: String,
            val matched: Int,
            val total: Int,
            /** Cards the source had no image for. */
            val missingRefs: List<String>,
        ) : Result

        data class Failed(val message: String) : Result
    }

    /** Imports every matching image in a folder the user picked. */
    suspend fun importFromFolder(folderTreeUri: Uri, deckName: String, artist: String, description: String): Result =
        withContext(dispatchers.io) {
            val staging = newStaging()
            try {
                val cards = cardRepository.getAllCards()
                val tree = DocumentFile.fromTreeUri(context, folderTreeUri)
                    ?: return@withContext Result.Failed("Could not open that folder.")
                val matched = HashSet<String>()
                for (doc in tree.listFiles()) {
                    val name = doc.name ?: continue
                    if (!doc.isFile || !name.isImage()) continue
                    val key = keyFor(name, cards)?.takeIf { it !in matched } ?: continue
                    val saved = context.contentResolver.openInputStream(doc.uri)?.use { saveImage(it, staging, key) } ?: false
                    if (saved && key != BACK) matched += key
                }
                if (matched.isEmpty()) return@withContext Result.Failed(NOTHING_FOUND)
                finish(staging, deckName.ifBlank { "Imported deck" }, artist.ifBlank { "Unknown" }, description, "User-imported", matched, cards)
            } catch (e: Exception) {
                Result.Failed("Import failed: ${e.message ?: e::class.java.simpleName}")
            } finally {
                staging.deleteRecursively()
            }
        }

    /** Sets one card's image, replacing whatever the deck had for it. */
    suspend fun replaceCardImage(deckId: String, imageRef: String, sourceUri: Uri): Boolean = withContext(dispatchers.io) {
        val ok = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use {
                saveImage(it, store.deckDir(deckId), imageRef.substringBeforeLast('.').lowercase())
            } ?: false
        }.getOrDefault(false)
        if (ok) store.changed()
        ok
    }

    suspend fun deleteCardImage(deckId: String, imageRef: String): Boolean = withContext(dispatchers.io) {
        val ok = store.imageFile(deckId, imageRef)?.delete() ?: false
        if (ok) store.changed()
        ok
    }

    /** Writes the deck's details and images to one ZIP, all at the top level. */
    suspend fun exportToZip(deckId: String, outputUri: Uri): Result = withContext(dispatchers.io) {
        try {
            val files = store.deckDir(deckId).listFiles().orEmpty().filter { it.isFile && (it.name == CustomDeckStore.MANIFEST || it.name.isImage()) }
            if (files.isEmpty()) return@withContext Result.Failed("This deck has nothing to export.")
            context.contentResolver.openOutputStream(outputUri, "wt")?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    for (f in files) {
                        zip.putNextEntry(ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext Result.Failed("Could not open the file to write to.")
            Result.Success(deckId, matched = files.count { it.name.isImage() }, total = files.size, missingRefs = emptyList())
        } catch (e: Exception) {
            Result.Failed("Export failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * Imports a deck from a ZIP: one this app exported, or any archive of images named like the
     * cards. It always arrives as a new deck, so importing twice never overwrites anything.
     */
    suspend fun importFromZip(zipUri: Uri): Result = withContext(dispatchers.io) {
        val staging = newStaging()
        try {
            val cards = cardRepository.getAllCards()
            val matched = HashSet<String>()
            var details: CustomDeckStore.DeckDetails? = null
            var total = 0L
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        // Only the last part of the path is used, so an entry cannot write outside the deck.
                        val name = entry.name.substringAfterLast('/')
                        if (entry.isDirectory || name.isEmpty() || name.startsWith(".")) continue
                        val limited = Limited(zip, MAX_ENTRY_BYTES)
                        if (name == CustomDeckStore.MANIFEST) {
                            details = store.readManifest(limited.readBytes().toString(Charsets.UTF_8))
                        } else if (name.isImage()) {
                            val key = keyFor(name, cards)?.takeIf { it !in matched }
                            if (key != null && saveImage(limited, staging, key) && key != BACK) matched += key
                        }
                        total += limited.count
                        if (total > MAX_TOTAL_BYTES) throw IOException("The archive is too large.")
                    }
                }
            } ?: return@withContext Result.Failed("Could not open that file.")
            if (matched.isEmpty()) return@withContext Result.Failed(NOTHING_FOUND)
            val name = details?.name ?: displayName(zipUri)?.substringBeforeLast('.') ?: "Imported deck"
            finish(staging, name, details?.artist ?: "Unknown", details?.description.orEmpty(), "Imported (ZIP)", matched, cards)
        } catch (e: Exception) {
            Result.Failed("Import failed: ${e.message ?: e::class.java.simpleName}")
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------

    /** The name a file is stored under: its card's own, `back` for the card back, null if it is neither. */
    private fun keyFor(fileName: String, cards: List<Card>): String? =
        if (CardFileNames.isBack(fileName)) BACK else CardFileNames.cardFor(fileName, cards)?.let(::key)

    private fun key(card: Card): String = card.imageRef.substringBeforeLast('.').lowercase()

    /** A folder the deck list ignores (it has no manifest) until the import is complete. */
    private fun newStaging(): File = store.deckDir(".import-${UUID.randomUUID()}").apply { mkdirs() }

    private suspend fun finish(
        staging: File,
        name: String,
        artist: String,
        description: String,
        license: String,
        matched: Set<String>,
        cards: List<Card>,
    ): Result {
        val names = cards.map(::key).toSet()
        val deckId = store.newDeckId(name)
        val dir = store.deckDir(deckId)
        if (!staging.renameTo(dir)) return Result.Failed("Could not save the deck.")
        store.writeManifest(
            DeckArt(
                id = deckId,
                name = name,
                artist = artist,
                year = null,
                license = license,
                description = description,
                assetFolder = dir.absolutePath,
                isBundled = false,
            ),
        )
        return Result.Success(deckId, matched.size, names.size, (names - matched).sorted())
    }

    /**
     * Stores one image as `<key>.<ext>` in [dir]. A camera-sized photo is far more than a card on
     * a phone screen needs, so anything large is scaled down and stored as WebP; the rest is kept
     * exactly as it came.
     *
     * @return false if the data was not an image
     */
    private fun saveImage(input: InputStream, dir: File, key: String): Boolean {
        val incoming = File(dir, ".incoming-$key")
        try {
            incoming.outputStream().use { input.copyTo(it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(incoming.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val edge = max(bounds.outWidth, bounds.outHeight)
            val target: File
            if (edge > SHRINK_ABOVE || incoming.length() > SHRINK_ABOVE_BYTES) {
                val bitmap = decodeScaled(incoming, bounds, MAX_EDGE) ?: return false
                target = File(dir, "$key.webp")
                val tmp = File(dir, ".scaled-$key")
                tmp.outputStream().use { bitmap.compress(webp(), 88, it) }
                bitmap.recycle()
                removeOthers(dir, key)
                if (!tmp.renameTo(target)) return false
            } else {
                val ext = when (bounds.outMimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                target = File(dir, "$key.$ext")
                removeOthers(dir, key)
                if (!incoming.renameTo(target)) return false
            }
            return true
        } finally {
            incoming.delete()
        }
    }

    /** A deck holds one image per card: drop any earlier one, whatever its extension. */
    private fun removeOthers(dir: File, key: String) {
        dir.listFiles()?.forEach { if (it.isFile && it.name.isImage() && it.nameWithoutExtension.lowercase() == key) it.delete() }
    }

    private fun decodeScaled(file: File, bounds: BitmapFactory.Options, maxEdge: Int): Bitmap? {
        val scale = maxEdge.toFloat() / max(bounds.outWidth, bounds.outHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the picture the way its EXIF data says, which matters for photos.
            return runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (scale < 1f) {
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                }
            }.getOrNull()
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Reads at most [limit] bytes of one archive entry and counts them. Never closes the archive. */
    private class Limited(private val source: InputStream, private val limit: Long) : InputStream() {
        var count = 0L
            private set

        override fun read(): Int {
            val b = source.read()
            if (b >= 0) bump(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = source.read(b, off, len)
            if (n > 0) bump(n)
            return n
        }

        private fun bump(n: Int) {
            count += n
            if (count > limit) throw IOException("A file in the archive is too large.")
        }

        override fun close() = Unit
    }

    private companion object {
        const val BACK = "back"
        const val NOTHING_FOUND = "None of the images there could be matched to a card. Name each file for its card, like \"The Fool\", \"Queen of Cups\" or \"wands_05\"."
        const val SHRINK_ABOVE = 2000
        const val SHRINK_ABOVE_BYTES = 2L * 1024 * 1024
        const val MAX_EDGE = 1600
        const val MAX_ENTRY_BYTES = 40L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    }
}
