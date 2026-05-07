package com.arcana.core.data.repository

import kotlinx.serialization.Serializable

/**
 * On-disk shape of an Arcana backup bundle. Holds custom spreads + saved
 * readings — the user-generated data that's worth surviving an uninstall
 * or moving between devices.
 *
 * Bundled spreads aren't included (they ship with the app), and bundled
 * deck art isn't included (the same APK has it). Custom DECK files aren't
 * yet covered — those are images that bloat a JSON badly; deferred until
 * we decide whether to do a separate ZIP-format export for them.
 *
 * schemaVersion will get bumped if the shape changes; importers should
 * refuse a higher schemaVersion than they understand.
 */
@Serializable
data class BackupBundle(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val customSpreads: List<CustomSpreadDto> = emptyList(),
    val savedReadings: List<ReadingDto> = emptyList(),
)

@Serializable
data class CustomSpreadDto(
    val id: String,
    val name: String,
    val description: String,
    val createdAtEpochMs: Long,
    val positions: List<CustomSpreadPositionDto>,
)

@Serializable
data class CustomSpreadPositionDto(
    val positionIndex: Int,
    val label: String,
    val meaning: String,
    val x: Float,
    val y: Float,
    val rotationDegrees: Float,
)

@Serializable
data class ReadingDto(
    val id: String,
    val timestampEpochMs: Long,
    val spreadId: String,
    val spreadName: String,
    val question: String?,
    val interpretation: String?,
    val notes: String?,
    val deckArtId: String,
    val kind: String,
    /**
     * The per-reading spread snapshot from v0.5.0+. Old rows have it null
     * and resolve via SpreadRepository at view time.
     */
    val spreadPositionsJson: String? = null,
    val drawnCards: List<DrawnCardDto>,
)

@Serializable
data class DrawnCardDto(
    val cardId: String,
    val orientation: String,
    val positionIndex: Int,
)
