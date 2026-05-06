package com.arcana.core.domain.model

data class Spread(
    val id: String,
    val name: String,
    val description: String,
    val positions: List<Position>,
    val layout: SpreadLayout,
    val difficulty: SpreadDifficulty,
) {
    val cardCount: Int get() = positions.size
}

data class Position(
    val index: Int,
    val label: String,
    val meaning: String,
    val coords: PositionCoords,
)

/**
 * Normalized [0..1] coordinates of where this position sits within the spread board.
 * Renderer maps these to actual pixel space.
 */
data class PositionCoords(
    val x: Float,
    val y: Float,
    val rotationDegrees: Float = 0f,
)

enum class SpreadLayout {
    LINEAR,
    GRID,
    CELTIC_CROSS,
    HORSESHOE,
    YEAR_WHEEL,
    CUSTOM,
}

enum class SpreadDifficulty(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}
