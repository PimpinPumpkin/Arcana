package com.arcana.core.domain.model

enum class ReadingKind(val displayName: String) {
    /** Cards were drawn by the app's RNG. */
    DIGITAL("Digital pull"),
    /** User logged a reading they did with a physical deck. */
    PHYSICAL("Physical reading"),
}

data class Reading(
    val id: String,
    val timestampEpochMs: Long,
    val spreadId: String,
    val spreadName: String,
    val question: String?,
    val drawnCards: List<DrawnCard>,
    val interpretation: String?,
    val notes: String?,
    val deckArtId: String,
    val kind: ReadingKind = ReadingKind.DIGITAL,
    /**
     * Snapshot of the spread's positions captured at save time. Lets the
     * journal render correctly even if the user later edits or deletes the
     * underlying custom spread. Null only for readings created before
     * v0.5.0 introduced the snapshot column.
     */
    val spreadSnapshot: List<Position>? = null,
)
