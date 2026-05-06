package com.arcana.core.ui.util

import android.content.Context
import com.arcana.core.domain.model.DeckArt

/**
 * Caches "does this deck have any bundled card art?" answers, so the UI can
 * skip the Coil pipeline entirely for art-less decks (huge perf win when the
 * user hasn't yet dropped Rider-Waite scans into assets/).
 *
 * Plain class — instantiated once at the activity / application level. Not a
 * Hilt-managed singleton, since core-ui deliberately doesn't depend on Hilt.
 */
class DeckAssetAvailability(private val context: Context) {

    private val cache = mutableMapOf<String, Boolean>()

    fun hasArt(deck: DeckArt): Boolean = synchronized(cache) {
        cache.getOrPut(deck.id) {
            runCatching {
                context.assets.list(deck.assetFolder).orEmpty().any { name ->
                    name.endsWith(".png", ignoreCase = true) ||
                        name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".webp", ignoreCase = true)
                }
            }.getOrDefault(false)
        }
    }
}
