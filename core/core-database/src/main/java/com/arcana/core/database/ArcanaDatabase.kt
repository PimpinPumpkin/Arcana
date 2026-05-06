package com.arcana.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arcana.core.database.dao.CustomSpreadDao
import com.arcana.core.database.dao.ReadingDao
import com.arcana.core.database.entity.CustomSpreadEntity
import com.arcana.core.database.entity.CustomSpreadPositionEntity
import com.arcana.core.database.entity.DrawnCardEntity
import com.arcana.core.database.entity.ReadingEntity

@Database(
    entities = [
        ReadingEntity::class,
        DrawnCardEntity::class,
        CustomSpreadEntity::class,
        CustomSpreadPositionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ArcanaDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun customSpreadDao(): CustomSpreadDao
}
