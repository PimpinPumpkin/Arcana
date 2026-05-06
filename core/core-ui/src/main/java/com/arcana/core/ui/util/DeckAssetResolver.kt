package com.arcana.core.ui.util

import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt

/**
 * Resolves a card to its asset path within a given deck. Falls back to a stable
 * filename derived from the card id if the deck doesn't ship the file.
 *
 * Asset paths are relative to `app/src/main/assets/` (or any module's `assets/`).
 */
object DeckAssetResolver {
    fun assetPath(deck: DeckArt, card: Card): String =
        "${deck.assetFolder}/${card.imageRef}"

    fun fileUri(deck: DeckArt, card: Card): String =
        "file:///android_asset/${assetPath(deck, card)}"

    fun cardBackUri(deck: DeckArt): String =
        "file:///android_asset/${deck.cardBackAsset}"
}
