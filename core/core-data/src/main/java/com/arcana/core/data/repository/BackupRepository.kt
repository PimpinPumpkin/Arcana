package com.arcana.core.data.repository

import com.arcana.core.common.DispatcherProvider
import com.arcana.core.database.dao.CustomSpreadDao
import com.arcana.core.database.dao.ReadingDao
import com.arcana.core.database.entity.CustomSpreadEntity
import com.arcana.core.database.entity.CustomSpreadPositionEntity
import com.arcana.core.database.entity.DrawnCardEntity
import com.arcana.core.database.entity.ReadingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export / import everything the user has authored (custom spreads + saved
 * readings) as a JSON [BackupBundle].
 *
 * Conflict handling on import: custom spreads use saveWithPositions which
 * is REPLACE-on-conflict; saved readings are deleted-then-inserted to keep
 * the cascading FK on drawn_cards from leaving stale rows. So re-importing
 * the same bundle is idempotent, and importing into a populated install
 * upserts on matching IDs.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val customSpreadDao: CustomSpreadDao,
    private val readingDao: ReadingDao,
    private val dispatchers: DispatcherProvider,
) {
    sealed interface ImportResult {
        data class Success(
            val customSpreads: Int,
            val readings: Int,
        ) : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** Returns a JSON string ready to be written to a file. */
    suspend fun exportJson(appVersionName: String): String = withContext(dispatchers.io) {
        val customSpreads = customSpreadDao.observeAll().first().map { row ->
            CustomSpreadDto(
                id = row.spread.id,
                name = row.spread.name,
                description = row.spread.description,
                createdAtEpochMs = row.spread.createdAtEpochMs,
                positions = row.positions
                    .sortedBy { it.positionIndex }
                    .map { p ->
                        CustomSpreadPositionDto(
                            positionIndex = p.positionIndex,
                            label = p.label,
                            meaning = p.meaning,
                            x = p.x,
                            y = p.y,
                            rotationDegrees = p.rotationDegrees,
                        )
                    },
            )
        }

        val readings = readingDao.observeReadings().first().map { entity ->
            val drawn = readingDao.getDrawnCards(entity.id)
            ReadingDto(
                id = entity.id,
                timestampEpochMs = entity.timestampEpochMs,
                spreadId = entity.spreadId,
                spreadName = entity.spreadName,
                question = entity.question,
                interpretation = entity.interpretation,
                notes = entity.notes,
                deckArtId = entity.deckArtId,
                kind = entity.kind,
                spreadPositionsJson = entity.spreadPositionsJson,
                drawnCards = drawn.map { dc ->
                    DrawnCardDto(
                        cardId = dc.cardId,
                        orientation = dc.orientation,
                        positionIndex = dc.positionIndex,
                    )
                },
            )
        }

        val bundle = BackupBundle(
            schemaVersion = 1,
            exportedAtEpochMs = System.currentTimeMillis(),
            appVersionName = appVersionName,
            customSpreads = customSpreads,
            savedReadings = readings,
        )
        json.encodeToString(BackupBundle.serializer(), bundle)
    }

    suspend fun importJson(jsonString: String): ImportResult = withContext(dispatchers.io) {
        val bundle = try {
            json.decodeFromString(BackupBundle.serializer(), jsonString)
        } catch (e: Exception) {
            return@withContext ImportResult.Failed("Couldn't parse backup file: ${e.message ?: e::class.java.simpleName}")
        }
        if (bundle.schemaVersion > 1) {
            return@withContext ImportResult.Failed(
                "Backup file uses schema v${bundle.schemaVersion}, this app only understands v1. Update Arcana and try again.",
            )
        }

        try {
            for (s in bundle.customSpreads) {
                val spreadEntity = CustomSpreadEntity(
                    id = s.id,
                    name = s.name,
                    description = s.description,
                    createdAtEpochMs = s.createdAtEpochMs,
                )
                val positions = s.positions.map { p ->
                    CustomSpreadPositionEntity(
                        spreadId = s.id,
                        positionIndex = p.positionIndex,
                        label = p.label,
                        meaning = p.meaning,
                        x = p.x,
                        y = p.y,
                        rotationDegrees = p.rotationDegrees,
                    )
                }
                customSpreadDao.saveWithPositions(spreadEntity, positions)
            }

            for (r in bundle.savedReadings) {
                // Cascading FK on drawn_cards.readingId means deleting the
                // reading wipes its drawn cards, so the re-insert below
                // doesn't accumulate duplicates on a re-import.
                readingDao.deleteReading(r.id)
                val readingEntity = ReadingEntity(
                    id = r.id,
                    timestampEpochMs = r.timestampEpochMs,
                    spreadId = r.spreadId,
                    spreadName = r.spreadName,
                    question = r.question,
                    interpretation = r.interpretation,
                    notes = r.notes,
                    deckArtId = r.deckArtId,
                    kind = r.kind,
                    spreadPositionsJson = r.spreadPositionsJson,
                )
                val drawnEntities = r.drawnCards.map { dc ->
                    DrawnCardEntity(
                        readingId = r.id,
                        cardId = dc.cardId,
                        orientation = dc.orientation,
                        positionIndex = dc.positionIndex,
                    )
                }
                readingDao.insertReadingWithCards(readingEntity, drawnEntities)
            }

            ImportResult.Success(
                customSpreads = bundle.customSpreads.size,
                readings = bundle.savedReadings.size,
            )
        } catch (e: Exception) {
            ImportResult.Failed("Import failed: ${e.message ?: e::class.java.simpleName}")
        }
    }
}
