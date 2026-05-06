package com.arcana.feature.spreads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import com.arcana.core.domain.usecase.DrawSpreadUseCase
import com.arcana.core.domain.usecase.SaveReadingUseCase
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.InterpretationTone
import com.arcana.service.ai.InterpreterRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReadingStage { QUESTION, SHUFFLING, REVEAL, INTERPRETATION }

data class ReadingFlowUiState(
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    val stage: ReadingStage = ReadingStage.QUESTION,
    val question: String = "",
    val allowReversed: Boolean = true,
    val tone: InterpretationTone = InterpretationTone.GROUNDED,
    val drawn: List<DrawnCard> = emptyList(),
    val interpretation: String = "",
    val isInterpreting: Boolean = false,
    val interpretationStatus: String? = null,
    val interpretationError: String? = null,
    val savedReadingId: String? = null,
)

@HiltViewModel
class ReadingFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val spreadRepository: SpreadRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val drawSpread: DrawSpreadUseCase,
    private val saveReading: SaveReadingUseCase,
    private val interpreterRegistry: InterpreterRegistry,
) : ViewModel() {

    init {
        // Force-reference cardRepository to keep it bound (used indirectly by DrawSpreadUseCase).
        cardRepository.hashCode()
    }

    private val spreadId: String = checkNotNull(savedStateHandle["spreadId"]) {
        "spreadId required as nav argument"
    }

    private val _state = MutableStateFlow(ReadingFlowUiState())
    val state: StateFlow<ReadingFlowUiState> = _state.asStateFlow()

    private var interpretJob: Job? = null

    init {
        viewModelScope.launch {
            val spread = spreadRepository.getSpreadById(spreadId)
            val appearance = settingsRepository.appearance.first()
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == appearance.deckArtId }
                ?: settingsRepository.getAvailableDecks().firstOrNull()
            _state.update { it.copy(spread = spread, deck = deck) }
        }
    }

    fun onQuestionChanged(q: String) = _state.update { it.copy(question = q) }
    fun onToggleReversed(allow: Boolean) = _state.update { it.copy(allowReversed = allow) }
    fun onToneChanged(tone: InterpretationTone) = _state.update { it.copy(tone = tone) }

    fun startShuffle() {
        viewModelScope.launch {
            _state.update { it.copy(stage = ReadingStage.SHUFFLING) }
            val spread = _state.value.spread ?: return@launch
            delay(SHUFFLE_DURATION_MS)
            val drawn = drawSpread(spread, allowReversed = _state.value.allowReversed)
            _state.update { it.copy(drawn = drawn, stage = ReadingStage.REVEAL) }
        }
    }

    fun goToInterpretation() {
        _state.update { it.copy(stage = ReadingStage.INTERPRETATION) }
    }

    fun requestInterpretation() {
        val current = _state.value
        val spread = current.spread ?: return
        if (current.isInterpreting) return
        interpretJob?.cancel()
        interpretJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isInterpreting = true,
                    interpretation = "",
                    interpretationError = null,
                    interpretationStatus = null,
                )
            }
            val interpreter = interpreterRegistry.activeInterpreter()
            val request = InterpretationRequest(
                spread = spread,
                drawnCards = current.drawn,
                question = current.question.takeIf { it.isNotBlank() },
                tone = current.tone,
            )
            interpreter.interpret(request).collect { chunk ->
                when (chunk) {
                    is InterpretationChunk.Text -> _state.update {
                        it.copy(interpretation = it.interpretation + chunk.delta, interpretationStatus = null)
                    }
                    is InterpretationChunk.Status -> _state.update {
                        it.copy(interpretationStatus = chunk.message)
                    }
                    is InterpretationChunk.Error -> _state.update {
                        it.copy(interpretationError = chunk.message, isInterpreting = false)
                    }
                    is InterpretationChunk.Complete -> _state.update {
                        it.copy(isInterpreting = false, interpretationStatus = null)
                    }
                }
            }
        }
    }

    fun saveCurrentReading() {
        val current = _state.value
        val spread = current.spread ?: return
        val deck = current.deck ?: return
        if (current.drawn.isEmpty()) return
        viewModelScope.launch {
            val id = saveReading(
                spread = spread,
                drawnCards = current.drawn,
                question = current.question,
                interpretation = current.interpretation.ifBlank { null },
                deckArtId = deck.id,
            )
            _state.update { it.copy(savedReadingId = id) }
        }
    }

    companion object {
        private const val SHUFFLE_DURATION_MS = 2_000L
    }
}
