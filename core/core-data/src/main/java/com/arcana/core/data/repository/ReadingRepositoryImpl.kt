package com.arcana.core.data.repository

import com.arcana.core.database.dao.ReadingDao
import com.arcana.core.database.entity.DrawnCardEntity
import com.arcana.core.database.entity.ReadingEntity
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.model.ReadingKind
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingRepositoryImpl @Inject constructor(
    private val dao: ReadingDao,
    private val cardRepository: CardRepository,
) : ReadingRepository {

    override fun observeReadings(): Flow<List<Reading>> =
        dao.observeReadings().map { readings ->
            if (readings.isEmpty()) return@map emptyList()
            val ids = readings.map { it.id }
            val drawnByReading = dao.getDrawnCardsForReadings(ids).groupBy { it.readingId }
            val cards = cardRepository.getAllCards().associateBy { it.id }
            readings.map { entity ->
                entity.toDomain(drawnByReading[entity.id].orEmpty(), cards)
            }
        }

    override suspend fun getReading(id: String): Reading? {
        val entity = dao.getReading(id) ?: return null
        val drawn = dao.getDrawnCards(id)
        val cards = cardRepository.getAllCards().associateBy { it.id }
        return entity.toDomain(drawn, cards)
    }

    override suspend fun saveReading(reading: Reading) {
        val entity = ReadingEntity(
            id = reading.id,
            timestampEpochMs = reading.timestampEpochMs,
            spreadId = reading.spreadId,
            spreadName = reading.spreadName,
            question = reading.question,
            interpretation = reading.interpretation,
            notes = reading.notes,
            deckArtId = reading.deckArtId,
            kind = reading.kind.name,
        )
        val cards = reading.drawnCards.map { dc ->
            DrawnCardEntity(
                readingId = reading.id,
                cardId = dc.card.id,
                orientation = dc.orientation.name,
                positionIndex = dc.positionIndex,
            )
        }
        dao.insertReadingWithCards(entity, cards)
    }

    override suspend fun updateInterpretation(id: String, interpretation: String) {
        dao.updateInterpretation(id, interpretation)
    }

    override suspend fun updateNotes(id: String, notes: String) {
        dao.updateNotes(id, notes)
    }

    override suspend fun deleteReading(id: String) {
        dao.deleteReading(id)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }

    private fun ReadingEntity.toDomain(
        drawn: List<DrawnCardEntity>,
        cardsById: Map<String, com.arcana.core.domain.model.Card>,
    ): Reading = Reading(
        id = id,
        timestampEpochMs = timestampEpochMs,
        spreadId = spreadId,
        spreadName = spreadName,
        question = question,
        drawnCards = drawn.mapNotNull { dc ->
            val card = cardsById[dc.cardId] ?: return@mapNotNull null
            DrawnCard(
                card = card,
                orientation = runCatching { Orientation.valueOf(dc.orientation) }.getOrDefault(Orientation.UPRIGHT),
                positionIndex = dc.positionIndex,
            )
        },
        interpretation = interpretation,
        notes = notes,
        deckArtId = deckArtId,
        kind = runCatching { ReadingKind.valueOf(kind) }.getOrDefault(ReadingKind.DIGITAL),
    )
}
