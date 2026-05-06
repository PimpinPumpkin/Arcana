package com.arcana.core.domain.model

data class DeckArt(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int?,
    val license: String,
    val description: String,
    val assetFolder: String,
    val cardBackAsset: String,
    val isBundled: Boolean,
)
