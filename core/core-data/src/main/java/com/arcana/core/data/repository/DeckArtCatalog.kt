package com.arcana.core.data.repository

import com.arcana.core.domain.model.DeckArt

/** The decks that ship inside the app. Imported decks live in [CustomDeckStore]. */
object DeckArtCatalog {
    const val DEFAULT_ID = "rider_waite_smith_1909"

    val RIDER_WAITE_SMITH = DeckArt(
        id = DEFAULT_ID,
        name = "Rider-Waite-Smith (1909)",
        artist = "Pamela Colman Smith",
        year = 1909,
        license = "Public Domain",
        description = "The classic deck illustrated by Pamela Colman Smith for A.E. Waite. The most widely recognized tarot imagery in the West.",
        assetFolder = "decks/rider-waite",
        isBundled = true,
    )

    val ALL = listOf(RIDER_WAITE_SMITH)
}
