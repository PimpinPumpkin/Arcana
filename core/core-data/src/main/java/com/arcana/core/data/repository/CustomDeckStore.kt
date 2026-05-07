package com.arcana.core.data.repository

import android.content.Context
import com.arcana.core.domain.model.DeckArt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Filesystem-backed registry for user-imported tarot decks.
 *
 * Storage layout:
 *   filesDir/decks/<deckId>/manifest.json   ← display name, artist, year, etc.
 *   filesDir/decks/<deckId>/major_00_fool.jpg
 *   filesDir/decks/<deckId>/wands_01_ace.jpg
 *   filesDir/decks/<deckId>/<imageRef>      ← one file per card, named to
 *                                              match cards.json's imageRef.
 *                                              Missing files fall back to
 *                                              the per-card text plate.
 *
 * The filesystem is the source of truth — no DB rows, no DataStore. A scan
 * of the decks/ subdirs produces the runtime list, which the settings layer
 * merges with the bundled catalog. Side benefit: deletion is just rmdir.
 */
@Singleton
class CustomDeckStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val rootDir: File = File(context.filesDir, "decks").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun deckDir(deckId: String): File = File(rootDir, deckId)
    fun manifestFile(deckId: String): File = File(deckDir(deckId), "manifest.json")
    fun cardImageFile(deckId: String, imageRef: String): File =
        File(deckDir(deckId), imageRef)

    /** Allocate a fresh deck ID. Slugified prefix for readability + UUID for uniqueness. */
    fun newDeckId(displayName: String): String {
        val slug = displayName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "deck" }
            .take(20)
        return "${slug}-${UUID.randomUUID().toString().take(8)}"
    }

    /** Returns all custom decks currently on disk, in createdAt order. */
    fun listAll(): List<DeckArt> {
        val children = rootDir.listFiles { f -> f.isDirectory }.orEmpty()
        return children
            .mapNotNull { dir ->
                val mf = File(dir, "manifest.json")
                if (!mf.exists()) return@mapNotNull null
                runCatching {
                    val m: ManifestDto = json.decodeFromString(
                        ManifestDto.serializer(),
                        mf.readText(),
                    )
                    m.toDeckArt(dir.absolutePath)
                }.getOrNull()
            }
            .sortedBy { it.year ?: Int.MAX_VALUE } // stable-ish order; manifest createdAt could be added
    }

    /**
     * Save (or overwrite) a deck's manifest. The folder must already exist,
     * which install / per-card-override flows arrange before calling this.
     */
    fun writeManifest(deck: DeckArt) {
        val dir = deckDir(deck.id).apply { mkdirs() }
        val dto = ManifestDto.fromDeckArt(deck)
        File(dir, "manifest.json").writeText(json.encodeToString(ManifestDto.serializer(), dto))
    }

    fun delete(deckId: String) {
        deckDir(deckId).deleteRecursively()
    }

    /** True if any image file at least exists for this custom deck. */
    fun hasAnyImages(deckId: String): Boolean {
        val dir = deckDir(deckId)
        return dir.listFiles { f -> f.isFile && f.name.endsWithImageExt() }.orEmpty().isNotEmpty()
    }

    private fun String.endsWithImageExt(): Boolean =
        endsWith(".jpg", true) || endsWith(".jpeg", true) ||
            endsWith(".png", true) || endsWith(".webp", true)
}

@Serializable
private data class ManifestDto(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val artist: String,
    val year: Int? = null,
    val license: String,
    val description: String,
    val cardBack: String? = null,
) {
    fun toDeckArt(absoluteDirPath: String): DeckArt = DeckArt(
        id = id,
        name = name,
        artist = artist,
        year = year,
        license = license,
        description = description,
        // For custom decks, assetFolder is an ABSOLUTE filesystem path
        // (DeckAssetResolver branches on isBundled). Bundled decks store
        // a relative asset path here; both shapes are intentional.
        assetFolder = absoluteDirPath,
        cardBackAsset = cardBack ?: "$absoluteDirPath/back.png",
        isBundled = false,
    )

    companion object {
        fun fromDeckArt(deck: DeckArt) = ManifestDto(
            id = deck.id,
            name = deck.name,
            artist = deck.artist,
            year = deck.year,
            license = deck.license,
            description = deck.description,
            cardBack = deck.cardBackAsset.takeIf { it.isNotBlank() },
        )
    }
}
