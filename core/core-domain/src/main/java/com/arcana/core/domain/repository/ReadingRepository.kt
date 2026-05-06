package com.arcana.core.domain.repository

import com.arcana.core.domain.model.Reading
import kotlinx.coroutines.flow.Flow

interface ReadingRepository {
    fun observeReadings(): Flow<List<Reading>>
    suspend fun getReading(id: String): Reading?
    suspend fun saveReading(reading: Reading)
    suspend fun updateInterpretation(id: String, interpretation: String)
    suspend fun updateNotes(id: String, notes: String)
    suspend fun deleteReading(id: String)
    suspend fun deleteAll()
}
