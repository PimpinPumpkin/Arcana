package com.arcana.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arcana.core.database.dao.ReadingDao
import com.arcana.core.database.entity.DrawnCardEntity
import com.arcana.core.database.entity.ReadingEntity

@Database(
    entities = [ReadingEntity::class, DrawnCardEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ArcanaDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
}
