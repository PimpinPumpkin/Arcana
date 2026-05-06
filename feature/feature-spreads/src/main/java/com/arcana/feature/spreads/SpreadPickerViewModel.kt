package com.arcana.feature.spreads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.SpreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpreadPickerUiState(
    val spreads: List<Spread> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SpreadPickerViewModel @Inject constructor(
    private val spreadRepository: SpreadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SpreadPickerUiState())
    val state: StateFlow<SpreadPickerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val spreads = spreadRepository.getAllSpreads()
            _state.value = SpreadPickerUiState(spreads = spreads, isLoading = false)
        }
    }
}
