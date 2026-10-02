package com.arcana.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.data.repository.CustomDeckStore
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.AiSettings
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.cloud.ClaudeModels
import com.arcana.service.ai.local.LlamaEngine
import com.arcana.service.ai.local.ModelSpec
import com.arcana.service.ai.local.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One model in the picker. */
data class ModelRow(
    val spec: ModelSpec,
    val state: ModelStore.State,
    /** This is the model readings are written with. */
    val inUse: Boolean,
)

data class SettingsUiState(
    val appearance: AppearanceSettings? = null,
    val ai: AiSettings? = null,
    val themes: List<ThemePreset> = emptyList(),
    val decks: List<DeckArt> = emptyList(),
    val models: List<ModelRow> = emptyList(),
    /** False on a phone the on-device model was not built for. */
    val localSupported: Boolean = true,
    /** Which build of the engine this phone's processor gets, for the About text. */
    val engineLibrary: String? = null,
    val claudeModel: String = ClaudeModels.DEFAULT,
    val importing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelStore: ModelStore,
    private val engine: LlamaEngine,
    customDeckStore: CustomDeckStore,
) : ViewModel() {

    private val transient = MutableStateFlow(false to null as String?)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appearance,
        settingsRepository.ai,
        combine(modelStore.states, modelStore.imported) { states, _ -> states },
        customDeckStore.version,
        transient,
    ) { appearance, ai, states, _, (importing, message) ->
        val specs = modelStore.specs
        val inUse = modelStore.inUse(ai.localModelId)
        SettingsUiState(
            appearance = appearance,
            ai = ai,
            themes = settingsRepository.getAvailableThemes(),
            decks = settingsRepository.getAvailableDecks(),
            models = specs.map { ModelRow(it, modelStore.state(it), inUse = it.id == inUse?.id) },
            localSupported = engine.supported,
            engineLibrary = engine.cpuLibraries.firstOrNull(),
            claudeModel = ai.claudeModelId.ifBlank { ClaudeModels.DEFAULT },
            importing = importing,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(id: String) = viewModelScope.launch { settingsRepository.setThemeId(id) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDeck(id: String) = viewModelScope.launch { settingsRepository.setDeckArtId(id) }
    fun setBackend(type: AiBackendType) = viewModelScope.launch { settingsRepository.setAiBackend(type) }
    fun saveApiKey(key: String) = viewModelScope.launch { settingsRepository.setClaudeApiKey(key.trim()) }
    fun setClaudeModel(id: String) = viewModelScope.launch { settingsRepository.setClaudeModelId(id.trim()) }

    fun useModel(spec: ModelSpec) = viewModelScope.launch { settingsRepository.setLocalModelId(spec.id) }
    fun install(spec: ModelSpec) = modelStore.install(spec)
    fun pause(spec: ModelSpec) = modelStore.pause(spec)

    fun remove(spec: ModelSpec) {
        viewModelScope.launch {
            // Let go of the file first if it is the model in memory.
            modelStore.file(spec)?.let { engine.release(it) }
            modelStore.delete(spec)
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            transient.value = true to null
            val result = modelStore.importGguf(uri)
            transient.value = false to when (result) {
                is ModelStore.ImportResult.Done -> "${result.spec.title} is ready to use."
                is ModelStore.ImportResult.Failed -> result.message
            }
        }
    }

    fun dismissMessage() {
        transient.value = transient.value.first to null
    }
}
