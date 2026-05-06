package com.arcana.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.arcana.core.database.entity.CustomSpreadEntity
import com.arcana.core.database.entity.CustomSpreadPositionEntity
import kotlinx.coroutines.flow.Flow

data class CustomSpreadWithPositions(
    @Embedded val spread: CustomSpreadEntity,
    @Relation(parentColumn = "id", entityColumn = "spreadId")
    val positions: List<CustomSpreadPositionEntity>,
)

@Dao
interface CustomSpreadDao {

    @Transaction
    @Query("SELECT * FROM custom_spreads ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<CustomSpreadWithPositions>>

    @Transaction
    @Query("SELECT * FROM custom_spreads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomSpreadWithPositions?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpread(spread: CustomSpreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPositions(positions: List<CustomSpreadPositionEntity>)

    @Query("DELETE FROM custom_spread_positions WHERE spreadId = :spreadId")
    suspend fun deletePositionsFor(spreadId: String)

    @Query("DELETE FROM custom_spreads WHERE id = :spreadId")
    suspend fun deleteSpread(spreadId: String)

    /** Replaces the spread row + its position rows atomically. */
    @Transaction
    suspend fun saveWithPositions(
        spread: CustomSpreadEntity,
        positions: List<CustomSpreadPositionEntity>,
    ) {
        upsertSpread(spread)
        deletePositionsFor(spread.id)
        insertPositions(positions)
    }
}
