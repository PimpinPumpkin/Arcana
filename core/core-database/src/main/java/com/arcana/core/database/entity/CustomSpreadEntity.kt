package com.arcana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-authored spread. Mirrors the structure of the bundled JSON spreads
 * but lives in Room so we can edit / delete it. Maps to the domain
 * [com.arcana.core.domain.model.Spread] with `layout = CUSTOM`.
 */
@Entity(tableName = "custom_spreads")
data class CustomSpreadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    /** Epoch ms when the user first saved this spread; used as a tiebreaker for default ordering. */
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "custom_spread_positions",
    foreignKeys = [
        ForeignKey(
            entity = CustomSpreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["spreadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["spreadId"])],
)
data class CustomSpreadPositionEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val spreadId: String,
    /** 1-based, matches the existing Position.index convention. */
    val positionIndex: Int,
    val label: String,
    val meaning: String,
    /** Normalized 0..1 board coordinates. */
    val x: Float,
    val y: Float,
    val rotationDegrees: Float,
)
