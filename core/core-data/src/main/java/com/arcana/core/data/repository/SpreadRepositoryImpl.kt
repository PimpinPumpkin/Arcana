package com.arcana.core.data.repository

import com.arcana.core.data.source.AssetJsonLoader
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.SpreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpreadRepositoryImpl @Inject constructor(
    private val loader: AssetJsonLoader,
) : SpreadRepository {

    private val cache = MutableStateFlow<List<Spread>>(emptyList())
    private val initMutex = Mutex()
    private var initialized = false

    private suspend fun ensureLoaded() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            cache.value = loader.loadSpreads()
            initialized = true
        }
    }

    override fun observeAllSpreads(): Flow<List<Spread>> = cache.asStateFlow()

    override suspend fun getAllSpreads(): List<Spread> {
        ensureLoaded()
        return cache.value
    }

    override suspend fun getSpreadById(id: String): Spread? {
        ensureLoaded()
        return cache.value.firstOrNull { it.id == id }
    }
}
