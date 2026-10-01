package com.arcana.core.data.repository

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("arcana_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customDeckStore: CustomDeckStore,
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
        val INTERPRET_PROMPT_SHOWN = booleanPreferencesKey("interpret_prompt_shown")

        // Comma-separated ids. No spread id contains a comma.
        val SPREAD_ORDER = stringPreferencesKey("spread_order")
    }

    private val themes: List<ThemePreset> =
        ThemePresets.ALL.filter { !it.supportsDynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    override val appearance: Flow<AppearanceSettings> = context.settingsDataStore.data.map { prefs ->
        // Wallpaper colors used to be a switch of their own. It is a theme like the others now.
        val chosen = if (prefs[Keys.USE_DYNAMIC] == true) ThemePresets.DYNAMIC.id else prefs[Keys.THEME_ID]
        AppearanceSettings(
            themeId = chosen?.takeIf { id -> themes.any { it.id == id } } ?: ThemePresets.DEFAULT_ID,
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            deckArtId = prefs[Keys.DECK_ID] ?: DeckArtCatalog.DEFAULT_ID,
        )
    }

    override val ai: Flow<AiSettings> = context.settingsDataStore.data.map { prefs ->
        AiSettings(
            backendType = runCatching { AiBackendType.valueOf(prefs[Keys.AI_BACKEND] ?: AiBackendType.RULE_BASED.name) }.getOrDefault(AiBackendType.RULE_BASED),
            claudeApiKey = prefs[Keys.CLAUDE_KEY] ?: "",
            localModelId = prefs[Keys.LOCAL_MODEL] ?: "",
            claudeModelId = prefs[Keys.CLAUDE_MODEL] ?: "",
            interpretPromptShown = prefs[Keys.INTERPRET_PROMPT_SHOWN] ?: false,
        )
    }

    override val spreadOrder: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SPREAD_ORDER].orEmpty().split(',').filter { it.isNotBlank() }
    }

    override suspend fun setSpreadOrder(ids: List<String>) {
        context.settingsDataStore.edit { it[Keys.SPREAD_ORDER] = ids.joinToString(",") }
    }

    override suspend fun getAvailableThemes(): List<ThemePreset> = themes

    /** Bundled decks first, then the ones the user imported. */
    override suspend fun getAvailableDecks(): List<DeckArt> = DeckArtCatalog.ALL + customDeckStore.list()

    override suspend fun currentDeck(): DeckArt {
        val id = appearance.first().deckArtId
        return getAvailableDecks().firstOrNull { it.id == id } ?: DeckArtCatalog.RIDER_WAITE_SMITH
    }

    override suspend fun setThemeId(id: String) {
        context.settingsDataStore.edit {
            it[Keys.THEME_ID] = id
            it.remove(Keys.USE_DYNAMIC)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
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

    override suspend fun setClaudeModelId(id: String) {
        context.settingsDataStore.edit { it[Keys.CLAUDE_MODEL] = id }
    }

    override suspend fun setLocalModelId(id: String) {
        context.settingsDataStore.edit { it[Keys.LOCAL_MODEL] = id }
    }

    override suspend fun setInterpretPromptShown(shown: Boolean) {
        context.settingsDataStore.edit { it[Keys.INTERPRET_PROMPT_SHOWN] = shown }
    }
}
