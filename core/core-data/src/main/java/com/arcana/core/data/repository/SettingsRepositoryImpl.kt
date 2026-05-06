package com.arcana.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.AiSettings
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("arcana_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC = booleanPreferencesKey("use_dynamic_color")
        val DECK_ID = stringPreferencesKey("deck_art_id")
        val AI_BACKEND = stringPreferencesKey("ai_backend")
        val CLAUDE_KEY = stringPreferencesKey("claude_api_key")
        val CLAUDE_MODEL = stringPreferencesKey("claude_model_id")
        val LOCAL_MODEL = stringPreferencesKey("local_model_id")
        val LOCAL_MODEL_INSTALLED = booleanPreferencesKey("local_model_installed")
        val INTERPRET_PROMPT_SHOWN = booleanPreferencesKey("interpret_prompt_shown")
    }

    override val appearance: Flow<AppearanceSettings> = context.settingsDataStore.data.map { prefs ->
        AppearanceSettings(
            themeId = prefs[Keys.THEME_ID] ?: ThemePresets.DEFAULT_ID,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            useDynamicColor = prefs[Keys.USE_DYNAMIC] ?: false,
            deckArtId = prefs[Keys.DECK_ID] ?: DeckArtCatalog.DEFAULT_ID,
        )
    }

    override val ai: Flow<AiSettings> = context.settingsDataStore.data.map { prefs ->
        AiSettings(
            backendType = runCatching { AiBackendType.valueOf(prefs[Keys.AI_BACKEND] ?: AiBackendType.RULE_BASED.name) }
                .getOrDefault(AiBackendType.RULE_BASED),
            claudeApiKey = prefs[Keys.CLAUDE_KEY] ?: "",
            localModelInstalled = prefs[Keys.LOCAL_MODEL_INSTALLED] ?: false,
            // Default points at the smaller Qwen so first-run installs are fast.
            // Manifest IDs live in service-ai's ModelManifest.kt.
            localModelId = prefs[Keys.LOCAL_MODEL] ?: "qwen2.5-0.5b-instruct-q4_k_m",
            claudeModelId = prefs[Keys.CLAUDE_MODEL] ?: "claude-sonnet-4-5",
            interpretPromptShown = prefs[Keys.INTERPRET_PROMPT_SHOWN] ?: false,
        )
    }

    override suspend fun getAvailableThemes(): List<ThemePreset> = ThemePresets.ALL
    override suspend fun getAvailableDecks(): List<DeckArt> = DeckArtCatalog.ALL

    override suspend fun setThemeId(id: String) {
        context.settingsDataStore.edit { it[Keys.THEME_ID] = id }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_DYNAMIC] = enabled }
    }

    override suspend fun setDeckArtId(id: String) {
        context.settingsDataStore.edit { it[Keys.DECK_ID] = id }
    }

    override suspend fun setAiBackend(type: AiBackendType) {
        context.settingsDataStore.edit { it[Keys.AI_BACKEND] = type.name }
    }

    override suspend fun setClaudeApiKey(key: String) {
        context.settingsDataStore.edit { it[Keys.CLAUDE_KEY] = key }
    }

    override suspend fun setLocalModelInstalled(installed: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOCAL_MODEL_INSTALLED] = installed }
    }

    override suspend fun setLocalModelId(id: String) {
        context.settingsDataStore.edit { it[Keys.LOCAL_MODEL] = id }
    }

    override suspend fun setInterpretPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { it[Keys.INTERPRET_PROMPT_SHOWN] = shown }
    }
}
