package com.arcana.core.database

import android.content.Context
import androidx.room.Room
import com.arcana.core.database.dao.ReadingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideArcanaDatabase(@ApplicationContext context: Context): ArcanaDatabase =
        Room.databaseBuilder(
            context,
            ArcanaDatabase::class.java,
            "arcana.db",
        )
            .addMigrations(*Migrations.ALL)
            // Version 1 was never in a release. Anything later is migrated, never dropped: a
            // version with no migration fails loudly instead of quietly emptying the journal.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    @Provides
    fun provideReadingDao(database: ArcanaDatabase): ReadingDao = database.readingDao()

    @Provides
    fun provideCustomSpreadDao(database: ArcanaDatabase): com.arcana.core.database.dao.CustomSpreadDao =
        database.customSpreadDao()
}
