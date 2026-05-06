package com.arcana.core.data.source

import kotlinx.serialization.Serializable

@Serializable
data class CardCatalogDto(
    val schemaVersion: Int,
    val cards: List<CardDto>,
)

@Serializable
data class CardDto(
    val id: String,
    val name: String,
    val arcana: ArcanaDto,
    val keywordsUpright: List<String>,
    val keywordsReversed: List<String>,
    val uprightMeaning: String,
    val reversedMeaning: String,
    val description: String,
    val element: String? = null,
    val astrology: String? = null,
    val numerology: String? = null,
    val imageRef: String,
)

@Serializable
data class ArcanaDto(
    val type: String,
    val number: Int? = null,
    val suit: String? = null,
    val rank: String? = null,
)

@Serializable
data class SpreadCatalogDto(
    val schemaVersion: Int,
    val spreads: List<SpreadDto>,
)

@Serializable
data class SpreadDto(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: String,
    val layout: String,
    val positions: List<PositionDto>,
)

@Serializable
data class PositionDto(
    val index: Int,
    val label: String,
    val meaning: String,
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
)
