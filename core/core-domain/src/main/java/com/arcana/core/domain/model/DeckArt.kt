package com.arcana.core.domain.model

data class DeckArt(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int?,
    val license: String,
    val description: String,
    /** A path inside the APK's assets for a bundled deck, an absolute folder for an imported one. */
    val assetFolder: String,
    val isBundled: Boolean,
)
