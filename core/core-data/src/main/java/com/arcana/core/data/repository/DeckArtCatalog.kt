package com.arcana.core.data.repository

import com.arcana.core.domain.model.DeckArt

object DeckArtCatalog {
    const val DEFAULT_ID = "rider_waite_smith_1909"

    val RIDER_WAITE_SMITH = DeckArt(
        id = "rider_waite_smith_1909",
        name = "Rider-Waite-Smith (1909)",
        artist = "Pamela Colman Smith",
        year = 1909,
        license = "Public Domain",
        description = "The classic deck illustrated by Pamela Colman Smith for A.E. Waite. The most widely-recognized tarot imagery in the West.",
        assetFolder = "decks/rider-waite",
        cardBackAsset = "decks/rider-waite/back.png",
        isBundled = true,
    )

    val MINIMAL_LINE = DeckArt(
        id = "minimal_line",
        name = "Minimal Line (placeholder)",
        artist = "Arcana built-in",
        year = null,
        license = "App-bundled",
        description = "A clean, geometric placeholder deck — used when card art is missing.",
        assetFolder = "decks/minimal",
        cardBackAsset = "decks/minimal/back.png",
        isBundled = true,
    )

    val ALL = listOf(RIDER_WAITE_SMITH, MINIMAL_LINE)
}
