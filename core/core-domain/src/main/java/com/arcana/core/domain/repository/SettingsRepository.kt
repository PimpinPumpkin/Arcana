package com.arcana.core.domain.repository

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.AiSettings
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>
    val ai: Flow<AiSettings>

    /**
     * The order the user dragged the spreads into, as ids. Empty until they reorder for the first
     * time; spreads not in it follow in their default order.
     */
    val spreadOrder: Flow<List<String>>

    suspend fun setSpreadOrder(ids: List<String>)

    suspend fun getAvailableThemes(): List<ThemePreset>
    suspend fun getAvailableDecks(): List<DeckArt>

    /** The deck chosen in Settings, or the bundled one if that deck is gone. */
    suspend fun currentDeck(): DeckArt

    suspend fun setThemeId(id: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDeckArtId(id: String)

    suspend fun setAiBackend(type: AiBackendType)
    suspend fun setClaudeApiKey(key: String)
    suspend fun setClaudeModelId(id: String)
    suspend fun setLocalModelId(id: String)
    suspend fun setInterpretPromptShown(shown: Boolean)
}
