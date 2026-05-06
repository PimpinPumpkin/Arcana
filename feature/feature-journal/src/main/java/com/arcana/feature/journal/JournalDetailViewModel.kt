package com.arcana.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.ReadingRepository
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

data class JournalDetailUiState(
    val reading: Reading? = null,
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    val notesDraft: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
    private val spreadRepository: SpreadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JournalDetailUiState())
    val state: StateFlow<JournalDetailUiState> = _state.asStateFlow()

    fun load(readingId: String) {
        viewModelScope.launch {
            val reading = readingRepository.getReading(readingId)
            val spread = reading?.spreadId?.let { spreadRepository.getSpreadById(it) }
            val deckId = reading?.deckArtId
                ?: settingsRepository.appearance.first().deckArtId
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == deckId }
                ?: settingsRepository.getAvailableDecks().firstOrNull()
            _state.value = JournalDetailUiState(
                reading = reading,
                spread = spread,
                deck = deck,
                notesDraft = reading?.notes.orEmpty(),
                isLoading = false,
            )
        }
    }

    fun onNotesChange(text: String) {
        _state.update { it.copy(notesDraft = text) }
    }

    fun saveNotes() {
        val current = _state.value
        val readingId = current.reading?.id ?: return
        viewModelScope.launch {
            readingRepository.updateNotes(readingId, current.notesDraft)
        }
    }

    fun deleteReading(onDone: () -> Unit) {
        val readingId = _state.value.reading?.id ?: return
        viewModelScope.launch {
            readingRepository.deleteReading(readingId)
            onDone()
        }
    }
}
