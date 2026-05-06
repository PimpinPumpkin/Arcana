package com.arcana.feature.spreads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpreadPickerUiState(
    val spreads: List<Spread> = emptyList(),
    /** Subset of [spreads] IDs that are user-authored — these get edit/delete affordances. */
    val customIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SpreadPickerViewModel @Inject constructor(
    private val spreadRepository: SpreadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SpreadPickerUiState> = spreadRepository.observeAllSpreads()
        .map { spreads ->
            SpreadPickerUiState(
                spreads = spreads,
                customIds = spreads.filter { it.layout == SpreadLayout.CUSTOM }.map { it.id }.toSet(),
                isLoading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpreadPickerUiState())

    fun deleteCustomSpread(id: String) = viewModelScope.launch {
        spreadRepository.deleteCustomSpread(id)
    }

    /**
     * Persist the user's reordering of the picker. Caller is responsible for
     * passing the *full* list (every visible spread's ID, in the new order)
     * — we don't merge partial reorders here.
     */
    fun setSpreadOrder(orderedIds: List<String>) = viewModelScope.launch {
        settingsRepository.setSpreadOrder(orderedIds)
    }
}
