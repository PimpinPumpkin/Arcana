package com.arcana.core.data.repository

import com.arcana.core.data.source.AssetJsonLoader
import com.arcana.core.domain.model.Arcana
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.Suit
import com.arcana.core.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val loader: AssetJsonLoader,
) : CardRepository {

    private val cache = MutableStateFlow<List<Card>>(emptyList())
    private val initMutex = Mutex()
    private var initialized = false

    private suspend fun ensureLoaded() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            cache.value = loader.loadCards()
            initialized = true
        }
    }

    override fun observeAllCards(): Flow<List<Card>> = cache.asStateFlow()

    override suspend fun getAllCards(): List<Card> {
        ensureLoaded()
        return cache.value
    }

    override suspend fun getCardById(id: String): Card? {
        ensureLoaded()
        return cache.value.firstOrNull { it.id == id }
    }

    override suspend fun searchCards(query: String): List<Card> {
        ensureLoaded()
        return cache.value.filter { it.matchesQuery(query) }
    }

    override suspend fun getMajorArcana(): List<Card> {
        ensureLoaded()
        return cache.value.filter { it.arcana is Arcana.Major }
    }

    override suspend fun getCardsBySuit(suit: Suit): List<Card> {
        ensureLoaded()
        return cache.value.filter { (it.arcana as? Arcana.Minor)?.suit == suit }
    }
}
