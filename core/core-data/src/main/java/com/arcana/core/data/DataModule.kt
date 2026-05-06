package com.arcana.core.data

import com.arcana.core.data.repository.CardRepositoryImpl
import com.arcana.core.data.repository.ReadingRepositoryImpl
import com.arcana.core.data.repository.SettingsRepositoryImpl
import com.arcana.core.data.repository.SpreadRepositoryImpl
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.ReadingRepository
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindCardRepository(impl: CardRepositoryImpl): CardRepository

    @Binds
    @Singleton
    abstract fun bindSpreadRepository(impl: SpreadRepositoryImpl): SpreadRepository

    @Binds
    @Singleton
    abstract fun bindReadingRepository(impl: ReadingRepositoryImpl): ReadingRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
