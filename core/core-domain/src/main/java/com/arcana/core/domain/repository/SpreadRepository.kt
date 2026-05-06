package com.arcana.core.domain.repository

import com.arcana.core.domain.model.Spread
import kotlinx.coroutines.flow.Flow

interface SpreadRepository {
    fun observeAllSpreads(): Flow<List<Spread>>
    suspend fun getAllSpreads(): List<Spread>
    suspend fun getSpreadById(id: String): Spread?
}
