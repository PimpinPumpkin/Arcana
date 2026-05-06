package com.arcana.core.data.repository

import com.arcana.core.data.source.AssetJsonLoader
import com.arcana.core.database.dao.CustomSpreadDao
import com.arcana.core.database.dao.CustomSpreadWithPositions
import com.arcana.core.database.entity.CustomSpreadEntity
import com.arcana.core.database.entity.CustomSpreadPositionEntity
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.PositionCoords
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadDifficulty
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadRepositoryImpl @Inject constructor(
    private val loader: AssetJsonLoader,
    private val customSpreadDao: CustomSpreadDao,
    private val settingsRepository: SettingsRepository,
) : SpreadRepository {

    private val bundledCache = MutableStateFlow<List<Spread>>(emptyList())
    private val initMutex = Mutex()
    private var initialized = false

    private suspend fun ensureLoaded() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            bundledCache.value = loader.loadSpreads()
            initialized = true
        }
    }

    /**
     * Combined Flow: bundled JSON spreads + Room-backed custom spreads,
     * sorted by the user's spread-order list (with anything missing appended
     * in default order).
     *
     * The `flow { ensureLoaded(); emitAll(...) }` wrapper is load-bearing:
     * `bundledCache` starts as `emptyList()` and is only populated inside
     * `ensureLoaded()`. Without this priming step the Flow would emit an
     * empty bundled set on first collection — which v0.4.0 shipped with,
     * causing the picker to show only the "Create custom" entry plus any
     * user-authored spreads.
     */
    override fun observeAllSpreads(): Flow<List<Spread>> = flow {
        ensureLoaded()
        emitAll(
            combine(
                bundledCache,
                customSpreadDao.observeAll(),
                settingsRepository.spreadOrder,
            ) { bundled, customRows, order ->
                val customSpreads = customRows.map { it.toDomain() }
                applyUserOrder(bundled + customSpreads, order)
            }
        )
    }

    override suspend fun getAllSpreads(): List<Spread> {
        ensureLoaded()
        val customSpreads = customSpreadDao.observeAll().first().map { it.toDomain() }
        val order = settingsRepository.spreadOrder.first()
        return applyUserOrder(bundledCache.value + customSpreads, order)
    }

    override suspend fun getSpreadById(id: String): Spread? {
        ensureLoaded()
        bundledCache.value.firstOrNull { it.id == id }?.let { return it }
        return customSpreadDao.getById(id)?.toDomain()
    }

    override suspend fun isCustomSpread(id: String): Boolean {
        return customSpreadDao.getById(id) != null
    }

    override suspend fun saveCustomSpread(spread: Spread) {
        val entity = CustomSpreadEntity(
            id = spread.id,
            name = spread.name,
            description = spread.description,
            createdAtEpochMs = customSpreadDao.getById(spread.id)?.spread?.createdAtEpochMs
                ?: System.currentTimeMillis(),
        )
        val positions = spread.positions.map { p ->
            CustomSpreadPositionEntity(
                spreadId = spread.id,
                positionIndex = p.index,
                label = p.label,
                meaning = p.meaning,
                x = p.coords.x,
                y = p.coords.y,
                rotationDegrees = p.coords.rotationDegrees,
            )
        }
        customSpreadDao.saveWithPositions(entity, positions)
    }

    override suspend fun deleteCustomSpread(id: String) {
        customSpreadDao.deleteSpread(id)
        // Also drop it from user-order if present so we don't keep ghost IDs.
        val current = settingsRepository.spreadOrder.first()
        if (id in current) {
            settingsRepository.setSpreadOrder(current - id)
        }
    }

    /**
     * Apply the user's reorder preference. IDs in [order] come first, in that
     * order. Spreads not yet in the order list are appended in their default
     * order (bundled order from JSON, then custom by createdAt asc).
     */
    private fun applyUserOrder(all: List<Spread>, order: List<String>): List<Spread> {
        if (order.isEmpty()) return all
        val byId = all.associateBy { it.id }
        val ordered = mutableListOf<Spread>()
        val seen = mutableSetOf<String>()
        for (id in order) {
            byId[id]?.let {
                ordered += it
                seen += id
            }
        }
        for (spread in all) {
            if (spread.id !in seen) ordered += spread
        }
        return ordered
    }
}

private fun CustomSpreadWithPositions.toDomain(): Spread = Spread(
    id = spread.id,
    name = spread.name,
    description = spread.description,
    positions = positions
        .sortedBy { it.positionIndex }
        .map { p ->
            Position(
                index = p.positionIndex,
                label = p.label,
                meaning = p.meaning,
                coords = PositionCoords(
                    x = p.x,
                    y = p.y,
                    rotationDegrees = p.rotationDegrees,
                ),
            )
        },
    layout = SpreadLayout.CUSTOM,
    difficulty = SpreadDifficulty.INTERMEDIATE,
)
