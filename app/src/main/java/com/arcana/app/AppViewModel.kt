package com.arcana.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.data.repository.ThemePresets
import com.arcana.core.domain.model.AppearanceSettings
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppearanceState(
    val preset: ThemePreset = ThemePresets.MYSTIC_TWILIGHT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val appearance: StateFlow<AppearanceState> = settingsRepository.appearance
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceState())

    private fun AppearanceSettings.toUi(): AppearanceState {
        val preset = ThemePresets.ALL.firstOrNull { it.id == themeId } ?: ThemePresets.MYSTIC_TWILIGHT
        return AppearanceState(
            preset = preset,
            themeMode = themeMode,
            useDynamicColor = useDynamicColor,
        )
    }
}
