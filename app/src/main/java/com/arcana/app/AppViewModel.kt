package com.arcana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.data.repository.CustomDeckStore
import com.arcana.core.data.repository.ThemePresets
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.service.ai.local.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppearanceState(
    val preset: ThemePreset = ThemePresets.MYSTIC_TWILIGHT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    customDeckStore: CustomDeckStore,
    modelStore: ModelStore,
) : ViewModel() {

    init {
        // A model download cut short by the app closing carries on as soon as it is opened again.
        modelStore.resumeInterrupted()
    }

    val appearance: StateFlow<AppearanceState> = settingsRepository.appearance
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceState())

    /** Rises when an imported deck's images change, so cards on screen reload them. */
    val deckVersion: StateFlow<Int> = customDeckStore.version

    private fun AppearanceSettings.toUi(): AppearanceState = AppearanceState(
        preset = ThemePresets.ALL.firstOrNull { it.id == themeId } ?: ThemePresets.MYSTIC_TWILIGHT,
        themeMode = themeMode,
    )
}
