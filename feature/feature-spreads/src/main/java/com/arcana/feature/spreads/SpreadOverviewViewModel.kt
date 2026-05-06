package com.arcana.feature.spreads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpreadOverviewUiState(
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SpreadOverviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val spreadRepository: SpreadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val spreadId: String = checkNotNull(savedStateHandle["spreadId"])

    private val _state = MutableStateFlow(SpreadOverviewUiState())
    val state: StateFlow<SpreadOverviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val spread = spreadRepository.getSpreadById(spreadId)
            val appearance = settingsRepository.appearance.first()
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == appearance.deckArtId }
                ?: settingsRepository.getAvailableDecks().firstOrNull()
            _state.update { it.copy(spread = spread, deck = deck, isLoading = false) }
        }
    }
}
