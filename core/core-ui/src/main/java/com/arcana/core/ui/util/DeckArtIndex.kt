package com.arcana.core.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.arcana.core.domain.model.DeckArt
import java.io.File

/**
 * Knows which image files each deck has, bundled or imported, so a card can show its art or go
 * straight to the text plate without a failed load in between.
 *
 * A card's `imageRef` names it (`major_00_fool.jpg`); the file on disk may carry any image
 * extension. Lookups are by name without the extension.
 */
class DeckArtIndex(context: Context) {
    private val app = context.applicationContext
    private val decks = HashMap<String, Map<String, String>>()

    /** Read by composables so they redraw when a deck's files change. */
    var version by mutableIntStateOf(0)
        private set

    /** Call after a deck's files are added, replaced or removed. */
    fun invalidate() {
        synchronized(decks) { decks.clear() }
        version++
    }

    /** Something Coil can load for this card, or null if the deck has no image for it. */
    fun cardUri(deck: DeckArt, imageRef: String): String? = uri(deck, imageRef.substringBeforeLast('.'))

    /** The deck's own card back, if it came with one. */
    fun backUri(deck: DeckArt): String? = uri(deck, "back")

    private fun uri(deck: DeckArt, name: String): String? {
        val file = files(deck)[name.lowercase()] ?: return null
        return if (deck.isBundled) "file:///android_asset/${deck.assetFolder}/$file" else "file://${deck.assetFolder}/$file"
    }

    private fun files(deck: DeckArt): Map<String, String> = synchronized(decks) {
        decks.getOrPut(deck.id) {
            val names = runCatching {
                if (deck.isBundled) app.assets.list(deck.assetFolder).orEmpty().toList()
                else File(deck.assetFolder).list().orEmpty().toList()
            }.getOrDefault(emptyList())
            names.filter { it.isImage() }.associateBy { it.substringBeforeLast('.').lowercase() }
        }
    }

    private fun String.isImage(): Boolean =
        endsWith(".jpg", true) || endsWith(".jpeg", true) || endsWith(".png", true) || endsWith(".webp", true)
}

val LocalDeckArtIndex = staticCompositionLocalOf<DeckArtIndex?> { null }

/** The index the app provides, or a private one for previews and tests. */
@Composable
fun deckArtIndex(): DeckArtIndex {
    val provided = LocalDeckArtIndex.current
    if (provided != null) return provided
    val context = LocalContext.current
    return remember(context) { DeckArtIndex(context) }
}
