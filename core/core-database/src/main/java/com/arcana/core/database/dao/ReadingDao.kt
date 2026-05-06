package com.arcana.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arcana.core.database.entity.DrawnCardEntity
import com.arcana.core.database.entity.ReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Query("SELECT * FROM readings ORDER BY timestampEpochMs DESC")
    fun observeReadings(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE id = :id LIMIT 1")
    suspend fun getReading(id: String): ReadingEntity?

    @Query("SELECT * FROM drawn_cards WHERE readingId = :readingId ORDER BY positionIndex ASC")
    suspend fun getDrawnCards(readingId: String): List<DrawnCardEntity>

    @Query("SELECT * FROM drawn_cards WHERE readingId IN (:readingIds) ORDER BY readingId, positionIndex ASC")
    suspend fun getDrawnCardsForReadings(readingIds: List<String>): List<DrawnCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: ReadingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawnCards(cards: List<DrawnCardEntity>)

    @Transaction
    suspend fun insertReadingWithCards(reading: ReadingEntity, cards: List<DrawnCardEntity>) {
        insertReading(reading)
        insertDrawnCards(cards)
    }

    @Query("UPDATE readings SET interpretation = :interpretation WHERE id = :id")
    suspend fun updateInterpretation(id: String, interpretation: String)

    @Query("UPDATE readings SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteReading(id: String)

    @Query("DELETE FROM readings")
    suspend fun deleteAll()
}
