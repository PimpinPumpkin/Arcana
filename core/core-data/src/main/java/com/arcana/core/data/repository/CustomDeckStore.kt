package com.arcana.core.data.repository

import android.content.Context
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.model.DeckArt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The decks a user has imported. Each is a folder under `filesDir/decks/<deckId>/` holding a
 * `manifest.json` and one image per card, named like the card's `imageRef` with any image
 * extension. A card with no file shows its name instead. A file named `back` is the card back.
 *
 * The folders are the only record. The list is read once and kept in memory; everything that
 * changes a deck goes through here or calls [changed].
 */
@Singleton
class CustomDeckStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val rootDir: File = File(context.filesDir, "decks")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _decks = MutableStateFlow<List<DeckArt>?>(null)

    /** Rises whenever a deck's files or details change, so images already on screen are reloaded. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun deckDir(deckId: String): File = File(rootDir, deckId)

    /** A fresh id: part of the name so the folder is recognizable, part random so it is unique. */
    fun newDeckId(displayName: String): String {
        val slug = displayName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "deck" }.take(20)
        return "$slug-${UUID.randomUUID().toString().take(8)}"
    }

    suspend fun list(): List<DeckArt> = _decks.value ?: withContext(dispatchers.io) { scan().also { _decks.value = it } }

    /** Call after writing to a deck's folder by hand. */
    suspend fun changed() {
        val fresh = withContext(dispatchers.io) { scan() }
        _decks.value = fresh
        _version.update { it + 1 }
    }

    suspend fun writeManifest(deck: DeckArt) {
        withContext(dispatchers.io) {
            val dir = deckDir(deck.id).apply { mkdirs() }
            File(dir, MANIFEST).writeText(json.encodeToString(ManifestDto.serializer(), ManifestDto.of(deck)))
        }
        changed()
    }

    suspend fun delete(deckId: String) {
        withContext(dispatchers.io) { deckDir(deckId).deleteRecursively() }
        changed()
    }

    /** The file holding this card's image, whatever its extension, or null. */
    fun imageFile(deckId: String, imageRef: String): File? {
        val name = imageRef.substringBeforeLast('.').lowercase()
        return deckDir(deckId).listFiles()?.firstOrNull { it.isFile && it.name.isImage() && it.nameWithoutExtension.lowercase() == name }
    }

    /** The details of a deck as stored in an exported archive. */
    fun readManifest(text: String): DeckDetails? = runCatching {
        val m = json.decodeFromString(ManifestDto.serializer(), text)
        DeckDetails(m.name, m.artist, m.description)
    }.getOrNull()

    private fun scan(): List<DeckArt> =
        rootDir.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { dir ->
                val manifest = File(dir, MANIFEST).takeIf { it.exists() } ?: return@mapNotNull null
                runCatching { json.decodeFromString(ManifestDto.serializer(), manifest.readText()).toDeckArt(dir.absolutePath) }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    data class DeckDetails(val name: String, val artist: String, val description: String)

    companion object {
        const val MANIFEST = "manifest.json"

        fun String.isImage(): Boolean =
            endsWith(".jpg", true) || endsWith(".jpeg", true) || endsWith(".png", true) || endsWith(".webp", true)
    }
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
) {
    fun toDeckArt(folder: String) = DeckArt(
        id = id,
        name = name,
        artist = artist,
        year = year,
        license = license,
        description = description,
        assetFolder = folder,
        isBundled = false,
    )

    companion object {
        fun of(deck: DeckArt) = ManifestDto(
            id = deck.id,
            name = deck.name,
            artist = deck.artist,
            year = deck.year,
            license = deck.license,
            description = deck.description,
        )
    }
}
