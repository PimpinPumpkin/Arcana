package com.arcana.core.ui.util

import android.content.Context
import com.arcana.core.domain.model.DeckArt
import java.io.File

/**
 * Caches "does this deck have any card art?" answers, so the UI can skip the
 * Coil pipeline entirely for art-less decks (huge perf win when the user
 * hasn't yet dropped Rider-Waite scans into assets/).
 *
 * Plain class — instantiated once at the activity / application level. Not a
 * Hilt-managed singleton, since core-ui deliberately doesn't depend on Hilt.
 */
class DeckAssetAvailability(private val context: Context) {

    private val cache = mutableMapOf<String, Boolean>()

    fun hasArt(deck: DeckArt): Boolean = synchronized(cache) {
        cache.getOrPut(deck.id) {
            if (deck.isBundled) hasBundledArt(deck) else hasFilesystemArt(deck)
        }
    }

    /** Drop the cached answer for a deck — call after a deck's images change on disk. */
    fun invalidate(deckId: String) = synchronized(cache) { cache.remove(deckId) }

    private fun hasBundledArt(deck: DeckArt): Boolean = runCatching {
        context.assets.list(deck.assetFolder).orEmpty().any { it.isImageFile() }
    }.getOrDefault(false)

    private fun hasFilesystemArt(deck: DeckArt): Boolean = runCatching {
        File(deck.assetFolder).listFiles { f -> f.isFile && f.name.isImageFile() }
            .orEmpty()
            .isNotEmpty()
    }.getOrDefault(false)

    private fun String.isImageFile(): Boolean =
        endsWith(".jpg", true) || endsWith(".jpeg", true) ||
            endsWith(".png", true) || endsWith(".webp", true)
}
