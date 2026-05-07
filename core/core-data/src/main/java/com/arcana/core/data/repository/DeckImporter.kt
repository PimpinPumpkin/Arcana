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

    private fun String.isImageFile(): Boolean =
        endsWith(".jpg", true) || endsWith(".jpeg", true) ||
            endsWith(".png", true) || endsWith(".webp", true)
}
