package com.arcana.core.ui.util

import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt

/**
 * Resolves a card to its image URI within a given deck. The shape depends
 * on whether the deck is bundled (assets/) or user-imported (filesDir/).
 *
 * Bundled decks: `assetFolder` is a relative path inside the APK's assets,
 * and we produce a `file:///android_asset/...` URI that Coil and the system
 * dynamic linker both understand.
 *
 * Custom decks: `assetFolder` is an absolute filesystem path (e.g.
 * `/data/data/com.arcana.app/files/decks/<deckId>`), so we produce a plain
 * `file://...` URI.
 */
object DeckAssetResolver {
    fun assetPath(deck: DeckArt, card: Card): String =
        "${deck.assetFolder}/${card.imageRef}"

    fun fileUri(deck: DeckArt, card: Card): String =
        if (deck.isBundled) {
            "file:///android_asset/${assetPath(deck, card)}"
        } else {
            "file://${assetPath(deck, card)}"
        }

    fun cardBackUri(deck: DeckArt): String =
        if (deck.isBundled) {
            "file:///android_asset/${deck.cardBackAsset}"
        } else {
            "file://${deck.cardBackAsset}"
        }
}
