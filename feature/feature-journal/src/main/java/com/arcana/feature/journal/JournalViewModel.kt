package com.arcana.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.repository.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalUiState(
    val readings: List<Reading> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
) : ViewModel() {

    val state: StateFlow<JournalUiState> =
        readingRepository.observeReadings()
            .map { JournalUiState(readings = it, isLoading = false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun deleteReading(id: String) {
        viewModelScope.launch { readingRepository.deleteReading(id) }
    }
}
