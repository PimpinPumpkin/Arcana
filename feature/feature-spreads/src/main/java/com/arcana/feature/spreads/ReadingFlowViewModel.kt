package com.arcana.feature.spreads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.ReadingRepository
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import com.arcana.core.domain.usecase.DrawSpreadUseCase
import com.arcana.core.domain.usecase.SaveReadingUseCase
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.InterpretationTone
import com.arcana.service.ai.InterpreterRegistry
import com.arcana.service.ai.local.LlamaEngine
import com.arcana.service.ai.local.ModelCatalog
import com.arcana.service.ai.local.ModelSpec
import com.arcana.service.ai.local.ModelStore
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

enum class ReadingStage { QUESTION, SHUFFLING, READING }

data class ReadingFlowUiState(
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    /** The spread this screen was opened for no longer exists (a custom spread that was deleted). */
    val spreadNotFound: Boolean = false,
    val stage: ReadingStage = ReadingStage.QUESTION,
    val question: String = "",
    val allowReversed: Boolean = true,
    val tone: InterpretationTone = InterpretationTone.GROUNDED,
    val drawn: List<DrawnCard> = emptyList(),
    val interpretation: String = "",
    val isInterpreting: Boolean = false,
    /** How many times a reading has been asked for here. The page moves to the text each time. */
    val run: Int = 0,
    /** What the interpreter is doing while there is nothing new to read. */
    val status: String? = null,
    val progress: Float? = null,
    val error: String? = null,
    /** Why this reading did not come from the backend chosen in Settings. */
    val notice: String? = null,
    val savedReadingId: String? = null,
    /** Offer to install an on-device model, the first time Interpret is tapped. */
    val offerInstall: Boolean = false,
)

@HiltViewModel
class ReadingFlowViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val spreadRepository: SpreadRepository,
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val readingRepository: ReadingRepository,
    private val drawSpread: DrawSpreadUseCase,
    private val saveReading: SaveReadingUseCase,
    private val interpreterRegistry: InterpreterRegistry,
    private val modelStore: ModelStore,
    engine: LlamaEngine,
) : ViewModel() {

    private val spreadId: String = checkNotNull(saved["spreadId"])

    private val _state = MutableStateFlow(ReadingFlowUiState())
    val state: StateFlow<ReadingFlowUiState> = _state.asStateFlow()

    val modelStates: StateFlow<Map<String, ModelStore.State>> = modelStore.states

    /** The models offered in the install dialog. Empty on a phone that cannot run one. */
    val installable: List<ModelSpec> = if (engine.supported) ModelCatalog.offered else emptyList()

    private var interpretJob: Job? = null

    init {
        viewModelScope.launch {
            val spread = spreadRepository.getSpreadById(spreadId)
            if (spread == null) {
                _state.update { it.copy(spreadNotFound = true) }
                return@launch
            }
            _state.update { restore(it.copy(spread = spread, deck = settingsRepository.currentDeck())) }
        }
    }

    fun onQuestionChanged(q: String) = change { it.copy(question = q) }
    fun onToggleReversed(allow: Boolean) = change { it.copy(allowReversed = allow) }
    fun onToneChanged(tone: InterpretationTone) = change { it.copy(tone = tone) }

    fun startShuffle() {
        val spread = _state.value.spread ?: return
        viewModelScope.launch {
            _state.update { it.copy(stage = ReadingStage.SHUFFLING) }
            delay(SHUFFLE_DURATION_MS)
            val drawn = drawSpread(spread, allowReversed = _state.value.allowReversed)
            change { it.copy(drawn = drawn, stage = ReadingStage.READING) }
        }
    }

    /**
     * The Interpret button. The first time, on a phone with no model yet, it offers to install
     * one instead of quietly producing the built-in text.
     */
    fun onInterpretTapped() {
        if (_state.value.isInterpreting) return
        viewModelScope.launch {
            val ai = settingsRepository.ai.first()
            val hasModel = modelStore.active() != null
            if (!ai.interpretPromptShown && !hasModel && installable.isNotEmpty() && ai.backendType != AiBackendType.CLAUDE_API) {
                _state.update { it.copy(offerInstall = true) }
            } else {
                if (!ai.interpretPromptShown) settingsRepository.setInterpretPromptShown(true)
                requestInterpretation()
            }
        }
    }

    fun onInstallChosen(spec: ModelSpec) {
        viewModelScope.launch {
            settingsRepository.setInterpretPromptShown(true)
            settingsRepository.setAiBackend(AiBackendType.LOCAL_LLM)
            modelStore.install(spec)
            _state.update { it.copy(offerInstall = false) }
            // This reading uses the built-in text; the model is ready for the next one.
            requestInterpretation()
        }
    }

    fun onInstallDeclined() {
        viewModelScope.launch {
            settingsRepository.setInterpretPromptShown(true)
            _state.update { it.copy(offerInstall = false) }
            requestInterpretation()
        }
    }

    /** Dismissed without answering: ask again next time. */
    fun onInstallDismissed() = _state.update { it.copy(offerInstall = false) }

    fun requestInterpretation() {
        val current = _state.value
        val spread = current.spread ?: return
        if (current.isInterpreting || current.drawn.isEmpty()) return
        interpretJob?.cancel()
        interpretJob = viewModelScope.launch {
            change { it.copy(isInterpreting = true, run = it.run + 1, interpretation = "", error = null, status = null, progress = null) }
            val ai = settingsRepository.ai.first()
            val interpreter = interpreterRegistry.activeInterpreter()
            val fellBack = ai.backendType != AiBackendType.RULE_BASED && interpreter.type == AiBackendType.RULE_BASED
            _state.update {
                it.copy(notice = if (fellBack) "No reading model is installed yet, so this is the cards' own meanings." else null)
            }
            val request = InterpretationRequest(
                spread = spread,
                drawnCards = current.drawn,
                question = current.question.takeIf { it.isNotBlank() },
                tone = current.tone,
            )
            interpreter.interpret(request).collect { chunk ->
                when (chunk) {
                    is InterpretationChunk.Text -> _state.update { it.copy(interpretation = it.interpretation + chunk.delta, status = null, progress = null) }
                    is InterpretationChunk.Status -> _state.update { it.copy(status = chunk.message, progress = chunk.progress) }
                    is InterpretationChunk.Error -> _state.update { it.copy(error = chunk.message, isInterpreting = false, status = null, progress = null) }
                    is InterpretationChunk.Complete -> finished()
                }
            }
        }
    }

    /** Stops the writing and keeps what was written so far. */
    fun stopInterpretation() {
        interpretJob?.cancel()
        finished()
    }

    private fun finished() {
        change { it.copy(isInterpreting = false, status = null, progress = null) }
        // A reading that was saved before it was interpreted gets the text added to it.
        val current = _state.value
        val id = current.savedReadingId ?: return
        if (current.interpretation.isBlank()) return
        viewModelScope.launch { readingRepository.updateInterpretation(id, current.interpretation.trim()) }
    }

    fun install(spec: ModelSpec) = modelStore.install(spec)
    fun pauseInstall(spec: ModelSpec) = modelStore.pause(spec)

    fun saveCurrentReading() {
        val current = _state.value
        val spread = current.spread ?: return
        val deck = current.deck ?: return
        if (current.drawn.isEmpty() || current.savedReadingId != null) return
        viewModelScope.launch {
            val id = saveReading(
                spread = spread,
                drawnCards = current.drawn,
                question = current.question,
                interpretation = current.interpretation.trim().ifBlank { null },
                deckArtId = deck.id,
            )
            change { it.copy(savedReadingId = id) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // A reading in progress has to outlive the app being pushed out of memory, which is likely
    // right after a model has been loaded. What matters is kept in the saved state.

    private fun change(transform: (ReadingFlowUiState) -> ReadingFlowUiState) {
        _state.update(transform)
        val s = _state.value
        saved[KEY_STAGE] = if (s.stage == ReadingStage.SHUFFLING) ReadingStage.QUESTION.name else s.stage.name
        saved[KEY_QUESTION] = s.question
        saved[KEY_REVERSED] = s.allowReversed
        saved[KEY_TONE] = s.tone.name
        saved[KEY_DRAWN] = ArrayList(s.drawn.map { "${it.card.id}|${it.orientation.name}|${it.positionIndex}" })
        // While the model is still writing, the half-written text is not worth restoring.
        saved[KEY_TEXT] = if (s.isInterpreting) "" else s.interpretation
        saved[KEY_SAVED_ID] = s.savedReadingId
    }

    private suspend fun restore(base: ReadingFlowUiState): ReadingFlowUiState {
        val stage = saved.get<String>(KEY_STAGE)?.let { runCatching { ReadingStage.valueOf(it) }.getOrNull() } ?: return base
        val drawn = saved.get<ArrayList<String>>(KEY_DRAWN).orEmpty().mapNotNull { line ->
            val parts = line.split('|')
            val card = cardRepository.getCardById(parts.getOrNull(0).orEmpty()) ?: return@mapNotNull null
            DrawnCard(
                card = card,
                orientation = runCatching { Orientation.valueOf(parts[1]) }.getOrDefault(Orientation.UPRIGHT),
                positionIndex = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null,
            )
        }
        return base.copy(
            stage = if (stage == ReadingStage.READING && drawn.isEmpty()) ReadingStage.QUESTION else stage,
            question = saved[KEY_QUESTION] ?: "",
            allowReversed = saved[KEY_REVERSED] ?: true,
            tone = saved.get<String>(KEY_TONE)?.let { runCatching { InterpretationTone.valueOf(it) }.getOrNull() } ?: InterpretationTone.GROUNDED,
            drawn = drawn,
            interpretation = saved[KEY_TEXT] ?: "",
            savedReadingId = saved[KEY_SAVED_ID],
        )
    }

    private companion object {
        const val SHUFFLE_DURATION_MS = 2_000L
        const val KEY_STAGE = "stage"
        const val KEY_QUESTION = "question"
        const val KEY_REVERSED = "reversed"
        const val KEY_TONE = "tone"
        const val KEY_DRAWN = "drawn"
        const val KEY_TEXT = "text"
        const val KEY_SAVED_ID = "savedId"
    }
}
