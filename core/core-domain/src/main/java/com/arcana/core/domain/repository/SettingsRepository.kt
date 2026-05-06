package com.arcana.core.domain.repository

import com.arcana.core.domain.model.AiSettings
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemePreset
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>
    val ai: Flow<AiSettings>

    /**
     * User-defined spread order as a list of IDs. Empty until the user
     * reorders for the first time. Spreads not present here use default
     * ordering (bundled first, then custom by createdAt) appended after.
     */
    val spreadOrder: Flow<List<String>>

    suspend fun setSpreadOrder(ids: List<String>)

    suspend fun getAvailableThemes(): List<ThemePreset>
    suspend fun getAvailableDecks(): List<DeckArt>

    suspend fun setThemeId(id: String)
    suspend fun setThemeMode(mode: com.arcana.core.domain.model.ThemeMode)
    suspend fun setUseDynamicColor(enabled: Boolean)
    suspend fun setDeckArtId(id: String)

    suspend fun setAiBackend(type: com.arcana.core.domain.model.AiBackendType)
    suspend fun setClaudeApiKey(key: String)
    suspend fun setLocalModelInstalled(installed: Boolean)
    suspend fun setLocalModelId(id: String)
    suspend fun setInterpretPromptShown(shown: Boolean)
}
