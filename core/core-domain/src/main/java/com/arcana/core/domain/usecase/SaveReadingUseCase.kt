package com.arcana.core.domain.usecase

import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.model.ReadingKind
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.ReadingRepository
import java.util.UUID
import javax.inject.Inject

class SaveReadingUseCase @Inject constructor(
    private val readingRepository: ReadingRepository,
) {
    suspend operator fun invoke(
        spread: Spread,
        drawnCards: List<DrawnCard>,
        question: String?,
        interpretation: String?,
        deckArtId: String,
        kind: ReadingKind = ReadingKind.DIGITAL,
        notes: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val reading = Reading(
            id = id,
            timestampEpochMs = System.currentTimeMillis(),
            spreadId = spread.id,
            spreadName = spread.name,
            question = question?.takeIf { it.isNotBlank() },
            drawnCards = drawnCards,
            interpretation = interpretation,
            notes = notes,
            deckArtId = deckArtId,
            kind = kind,
            // Snapshot the spread's positions so the journal entry stays
            // self-contained: deleting or editing the underlying custom
            // spread later doesn't corrupt this reading.
            spreadSnapshot = spread.positions,
        )
        readingRepository.saveReading(reading)
        return id
    }
}
