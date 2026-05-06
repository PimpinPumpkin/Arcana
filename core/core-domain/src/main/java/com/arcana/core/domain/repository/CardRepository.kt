package com.arcana.core.domain.repository

import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.Suit
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun observeAllCards(): Flow<List<Card>>
    suspend fun getAllCards(): List<Card>
    suspend fun getCardById(id: String): Card?
    suspend fun searchCards(query: String): List<Card>
    suspend fun getMajorArcana(): List<Card>
    suspend fun getCardsBySuit(suit: Suit): List<Card>
}
