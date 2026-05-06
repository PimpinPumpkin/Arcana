package com.arcana.feature.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.ReadingKind
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import com.arcana.core.domain.usecase.SaveReadingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogPhysicalReadingUiState(
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    val allCards: List<Card> = emptyList(),
    /** positionIndex -> selected drawn card (with orientation). Missing key = unfilled. */
    val selections: Map<Int, DrawnCard> = emptyMap(),
    val question: String = "",
    val notes: String = "",
    val pickerForPosition: Int? = null,
    val pickerQuery: String = "",
    val savedReadingId: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class LogPhysicalReadingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val spreadRepository: SpreadRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val saveReading: SaveReadingUseCase,
) : ViewModel() {

    private val spreadId: String = checkNotNull(savedStateHandle["spreadId"])

    private val _state = MutableStateFlow(LogPhysicalReadingUiState())
    val state: StateFlow<LogPhysicalReadingUiState> = _state.asStateFlow()

    val isComplete: Boolean
        get() {
            val s = _state.value.spread ?: return false
            return _state.value.selections.size == s.cardCount
        }

    init {
        viewModelScope.launch {
            val spread = spreadRepository.getSpreadById(spreadId)
            val cards = cardRepository.getAllCards()
            val appearance = settingsRepository.appearance.first()
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == appearance.deckArtId }
                ?: settingsRepository.getAvailableDecks().firstOrNull()
            _state.update { it.copy(spread = spread, deck = deck, allCards = cards) }
        }
    }

    fun openPicker(positionIndex: Int) =
        _state.update { it.copy(pickerForPosition = positionIndex, pickerQuery = "") }

    fun closePicker() = _state.update { it.copy(pickerForPosition = null, pickerQuery = "") }

    fun onPickerQueryChanged(q: String) = _state.update { it.copy(pickerQuery = q) }

    fun selectCardForPosition(card: Card) {
        val idx = _state.value.pickerForPosition ?: return
        val current = _state.value.selections[idx]
        val drawn = current?.copy(card = card) ?: DrawnCard(
            card = card,
            orientation = Orientation.UPRIGHT,
            positionIndex = idx,
        )
        _state.update {
            it.copy(
                selections = it.selections + (idx to drawn),
                pickerForPosition = null,
                pickerQuery = "",
            )
        }
    }

    fun toggleOrientation(positionIndex: Int) {
        _state.update {
            val current = it.selections[positionIndex] ?: return@update it
            val flipped = current.copy(
                orientation = if (current.orientation == Orientation.UPRIGHT) Orientation.REVERSED else Orientation.UPRIGHT,
            )
            it.copy(selections = it.selections + (positionIndex to flipped))
        }
    }

    fun clearPosition(positionIndex: Int) {
        _state.update { it.copy(selections = it.selections - positionIndex) }
    }

    fun onQuestionChanged(q: String) = _state.update { it.copy(question = q) }
    fun onNotesChanged(n: String) = _state.update { it.copy(notes = n) }

    fun save() {
        val current = _state.value
        val spread = current.spread ?: return
        val deck = current.deck ?: return
        if (!isComplete) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val drawn = spread.positions
                .map { current.selections.getValue(it.index) }
                .sortedBy { it.positionIndex }
            val id = saveReading(
                spread = spread,
                drawnCards = drawn,
                question = current.question.takeIf { it.isNotBlank() },
                interpretation = null,
                deckArtId = deck.id,
                kind = ReadingKind.PHYSICAL,
                notes = current.notes.takeIf { it.isNotBlank() },
            )
            _state.update { it.copy(savedReadingId = id, isSaving = false) }
        }
    }

    fun filteredCards(): List<Card> {
        val q = _state.value.pickerQuery
        val all = _state.value.allCards
        return if (q.isBlank()) all else all.filter { it.matchesQuery(q) }
    }
}
