package com.arcana.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.repository.CardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bulk-imports a deck of card images from a user-picked folder.
 *
 * The matching convention: each card has an `imageRef` in cards.json
 * (e.g. `major_00_fool.jpg`, `wands_01_ace.jpg`). We match files in the
 * picked folder by **basename, case-insensitive**, against those imageRefs.
 * Whatever extension the user's file uses (`.jpg`, `.png`, `.webp`, etc.) we
 * accept — Coil decodes by content, not extension — and copy under the
 * canonical imageRef filename. So `Major_00_Fool.PNG` gets imported as
 * `major_00_fool.jpg` in our deck folder. That keeps DeckAssetResolver's
 * lookup simple (always look for the exact imageRef path).
 *
 * Missing files are fine — those cards just render the text fallback in the
 * UI. The user can fix specific cards later via the per-card override.
 */
@Singleton
class DeckImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customDeckStore: CustomDeckStore,
    private val cardRepository: CardRepository,
    private val dispatchers: DispatcherProvider,
) {
    sealed interface Result {
        data class Success(
            val deckId: String,
            val matched: Int,
            val total: Int,
            /** ImageRefs that the folder didn't have a file for. */
            val missingRefs: List<String>,
        ) : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Walk [folderTreeUri] (from a SAF OpenDocumentTree picker), copy any
     * matching card images into a fresh deck folder, and write the manifest.
     */
    suspend fun importFromFolder(
        folderTreeUri: Uri,
        deckName: String,
        artist: String,
        description: String,
    ): Result = withContext(dispatchers.io) {
        try {
            val cards = cardRepository.getAllCards()
            // Map basename (lowercase, no extension) → imageRef literal.
            val refsByBasename: Map<String, String> = cards
                .map { it.imageRef }
                .associateBy { it.substringBeforeLast('.').lowercase() }

            val tree = DocumentFile.fromTreeUri(context, folderTreeUri)
                ?: return@withContext Result.Failed("Couldn't open the selected folder.")
            val files = tree.listFiles().toList()

            val deckId = customDeckStore.newDeckId(deckName.takeIf { it.isNotBlank() } ?: "deck")
            val deckDir = customDeckStore.deckDir(deckId).apply { mkdirs() }

            var matched = 0
            val matchedRefs = mutableSetOf<String>()
            for (doc in files) {
                if (!doc.isFile) continue
                val name = doc.name ?: continue
                if (!name.isImageFile()) continue

                val baseLower = name.substringBeforeLast('.').lowercase()
                val ref = refsByBasename[baseLower] ?: continue
                if (ref in matchedRefs) continue // first wins; ignore duplicates

                val dest = File(deckDir, ref)
                context.contentResolver.openInputStream(doc.uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: continue
                matchedRefs += ref
                matched++
            }

            // Optional: a card-back override the user named "back.png" / "back.jpg".
            val back = files
                .firstOrNull {
                    it.isFile && it.name?.substringBeforeLast('.')?.lowercase() == "back"
                        && it.name?.isImageFile() == true
                }
            val backFileName = if (back != null) {
                val dest = File(deckDir, "back.png")
                context.contentResolver.openInputStream(back.uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                "${deckDir.absolutePath}/back.png"
            } else {
                "" // empty → core-ui's CardBackView is used as the fallback
            }

            customDeckStore.writeManifest(
                DeckArt(
                    id = deckId,
                    name = deckName.ifBlank { "Imported deck" },
                    artist = artist.ifBlank { "Unknown" },
                    year = null,
                    license = "User-imported",
                    description = description.ifBlank { "Imported by user." },
                    assetFolder = deckDir.absolutePath,
                    cardBackAsset = backFileName,
                    isBundled = false,
                ),
            )

            val missing = cards.map { it.imageRef }.filter { it !in matchedRefs }
            Result.Success(deckId = deckId, matched = matched, total = cards.size, missingRefs = missing)
        } catch (e: Exception) {
            Result.Failed("Import failed: ${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * Copy a single image (selected from the gallery) into an existing
     * deck's folder under the given imageRef. Used by the per-card override.
     */
    suspend fun replaceCardImage(
        deckId: String,
        imageRef: String,
        sourceUri: Uri,
    ): Boolean = withContext(dispatchers.io) {
        try {
            val dest = customDeckStore.cardImageFile(deckId, imageRef)
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteCardImage(deckId: String, imageRef: String): Boolean =
        withContext(dispatchers.io) {
            customDeckStore.cardImageFile(deckId, imageRef).delete()
        }

    /**
     * Bundle a custom deck's manifest + images into a single ZIP.
     * Layout inside the archive is flat — `manifest.json` and the per-card
     * image files at the root, no nested directory. The importer (see
     * [importFromZip]) reads that exact shape, so a deck round-trips
     * cleanly.
     */
    suspend fun exportToZip(deckId: String, outputUri: Uri): Result =
        withContext(dispatchers.io) {
            try {
                val deckDir = customDeckStore.deckDir(deckId)
                if (!deckDir.isDirectory) {
                    return@withContext Result.Failed("Deck folder not found.")
                }
                val files = deckDir.listFiles().orEmpty().filter { it.isFile }
                if (files.isEmpty()) {
                    return@withContext Result.Failed("Deck has no files to export.")
                }
                context.contentResolver.openOutputStream(outputUri, "wt")?.use { out ->
                    ZipOutputStream(out.buffered()).use { zip ->
                        for (f in files) {
                            zip.putNextEntry(ZipEntry(f.name))
                            f.inputStream().use { input -> input.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: return@withContext Result.Failed("Couldn't open output file.")
                Result.Success(
                    deckId = deckId,
                    matched = files.count { it.name.isImageFile() },
                    total = files.size,
                    missingRefs = emptyList(),
                )
            } catch (e: Exception) {
                Result.Failed("Export failed: ${e.message ?: e::class.java.simpleName}")
            }
        }

    /**
     * Pull a deck back out of a previously-exported ZIP. Reads the
     * manifest entry to recover display name / artist / description, but
     * deliberately mints a **fresh** deck ID so importing a ZIP twice (or
     * importing someone else's that happens to share an ID) makes a new
     * deck rather than colliding with an existing one.
     */
    suspend fun importFromZip(zipUri: Uri): Result =
        withContext(dispatchers.io) {
            try {
                // Two passes: first to read the manifest, second to copy
                // images. ZipInputStream is single-pass so we buffer
                // everything into memory in one walk and dispatch from
                // there.
                val entries = mutableMapOf<String, ByteArray>()
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                // Strip any directory prefix some zip tools add (e.g. macOS Archive Utility's
                                // wrapper folder). We only want the basename.
                                val name = entry.name.substringAfterLast('/')
                                if (name.isNotEmpty()) entries[name] = zip.readBytes()
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: return@withContext Result.Failed("Couldn't open ZIP file.")

                val manifestBytes = entries["manifest.json"]
                    ?: return@withContext Result.Failed("ZIP doesn't contain a manifest.json — not an Arcana deck export.")
                val manifestJson = String(manifestBytes, Charsets.UTF_8)
                val name = readManifestField(manifestJson, "name") ?: "Imported deck"
                val artist = readManifestField(manifestJson, "artist") ?: "Unknown"
                val description = readManifestField(manifestJson, "description") ?: ""

                val deckId = customDeckStore.newDeckId(name)
                val deckDir = customDeckStore.deckDir(deckId).apply { mkdirs() }

                val cards = cardRepository.getAllCards()
                val refsByBasename: Map<String, String> = cards
                    .map { it.imageRef }
                    .associateBy { it.substringBeforeLast('.').lowercase() }

                val matchedRefs = mutableSetOf<String>()
                var hadBack = false
                for ((entryName, bytes) in entries) {
                    if (entryName == "manifest.json") continue
                    val baseLower = entryName.substringBeforeLast('.').lowercase()
                    when {
                        baseLower == "back" && entryName.isImageFile() -> {
                            File(deckDir, "back.png").writeBytes(bytes)
                            hadBack = true
                        }
                        else -> {
                            val ref = refsByBasename[baseLower] ?: continue
                            File(deckDir, ref).writeBytes(bytes)
                            matchedRefs += ref
                        }
                    }
                }

                customDeckStore.writeManifest(
                    com.arcana.core.domain.model.DeckArt(
                        id = deckId,
                        name = name,
                        artist = artist,
                        year = null,
                        license = "Imported (ZIP)",
                        description = description,
                        assetFolder = deckDir.absolutePath,
                        cardBackAsset = if (hadBack) "${deckDir.absolutePath}/back.png" else "",
                        isBundled = false,
                    ),
                )

                val missing = cards.map { it.imageRef }.filter { it !in matchedRefs }
                Result.Success(
                    deckId = deckId,
                    matched = matchedRefs.size,
                    total = cards.size,
                    missingRefs = missing,
                )
            } catch (e: Exception) {
                Result.Failed("Import failed: ${e.message ?: e::class.java.simpleName}")
            }
        }

    /**
     * Pull a top-level string field out of the manifest JSON without
     * pulling kotlinx-serialization deps into core-data's runtime path
     * unnecessarily — the manifest is tiny and tightly-controlled, so a
     * plain regex is fine here.
     */
    private fun readManifestField(json: String, field: String): String? {
        val pattern = Regex("\"$field\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun String.isImageFile(): Boolean =
        endsWith(".jpg", true) || endsWith(".jpeg", true) ||
            endsWith(".png", true) || endsWith(".webp", true)
}
