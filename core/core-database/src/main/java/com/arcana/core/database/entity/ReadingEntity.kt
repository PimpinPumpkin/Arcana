package com.arcana.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "readings")
data class ReadingEntity(
    @PrimaryKey val id: String,
    val timestampEpochMs: Long,
    val spreadId: String,
    val spreadName: String,
    val question: String?,
    val interpretation: String?,
    val notes: String?,
    val deckArtId: String,
    val kind: String = "DIGITAL",
)

@Entity(
    tableName = "drawn_cards",
    foreignKeys = [
        ForeignKey(
            entity = ReadingEntity::class,
            parentColumns = ["id"],
            childColumns = ["readingId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["readingId"])],
)
data class DrawnCardEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val readingId: String,
    val cardId: String,
    val orientation: String,
    val positionIndex: Int,
)
