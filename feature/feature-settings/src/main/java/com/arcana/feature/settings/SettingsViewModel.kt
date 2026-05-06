package com.arcana.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.AiSettings
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.local.ModelInstaller
import com.arcana.service.ai.local.ModelManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val appearance: AppearanceSettings? = null,
    val ai: AiSettings? = null,
    val themes: List<ThemePreset> = emptyList(),
    val decks: List<DeckArt> = emptyList(),
    val apiKeyDraft: String = "",
    val localModel: ModelManifest = ModelManifest.DEFAULT,
    val installState: ModelInstaller.State = ModelInstaller.State.NotInstalled,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelInstaller: ModelInstaller,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appearance,
        settingsRepository.ai,
        modelInstaller.state,
    ) { appearance, ai, installState ->
        SettingsUiState(
            appearance = appearance,
            ai = ai,
            themes = settingsRepository.getAvailableThemes(),
            decks = settingsRepository.getAvailableDecks(),
            apiKeyDraft = ai.claudeApiKey,
            localModel = modelInstaller.manifest,
            installState = installState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(id: String) = viewModelScope.launch { settingsRepository.setThemeId(id) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    fun setDeck(id: String) = viewModelScope.launch { settingsRepository.setDeckArtId(id) }
    fun setBackend(type: AiBackendType) = viewModelScope.launch { settingsRepository.setAiBackend(type) }
    fun saveApiKey(key: String) = viewModelScope.launch { settingsRepository.setClaudeApiKey(key.trim()) }

    fun installLocalModel() = modelInstaller.install()
    fun cancelLocalInstall() = modelInstaller.cancel()
    fun uninstallLocalModel() = modelInstaller.uninstall()
}
