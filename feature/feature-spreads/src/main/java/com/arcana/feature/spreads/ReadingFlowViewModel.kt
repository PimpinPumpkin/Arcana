package com.arcana.feature.spreads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.AiBackendType
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
import com.arcana.service.ai.local.ModelInstaller
import com.arcana.service.ai.local.ModelManifest
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
    /** True if [SpreadRepository.getSpreadById] returned null on init —
     *  e.g. user followed a stale deep-link to a deleted custom spread. */
    val spreadNotFound: Boolean = false,
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
    /** True when we should be showing the first-tap "install offline AI?" dialog. */
    val showFirstTapPrompt: Boolean = false,
    /** Bytes to display in the dialog; cached from [ModelManifest]. */
    val firstTapDownloadBytes: Long = ModelManifest.DEFAULT.expectedBytes,
    /**
     * Set when the chosen AI backend wasn't available and we fell through
     * to rule-based for this generation. Lets the screen surface a small
     * banner so the user understands why the prose isn't from their
     * chosen backend. Cleared by [requestInterpretation] on every new run.
     */
    val backendFallbackNotice: String? = null,
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
    private val modelInstaller: ModelInstaller,
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

    /**
     * Mirror of [ModelInstaller.state] so the reading screen can surface a
     * progress banner / failure card without each composable touching the
     * service-layer singleton directly.
     */
    val installState: StateFlow<ModelInstaller.State> = modelInstaller.state

    private var interpretJob: Job? = null

    init {
        viewModelScope.launch {
            val spread = spreadRepository.getSpreadById(spreadId)
            if (spread == null) {
                _state.update { it.copy(spreadNotFound = true) }
                return@launch
            }
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

    /**
     * Entry point the Interpret button calls. Gated on the
     * "first-tap-prompt-shown" flag: the very first time anyone taps it,
     * surface the install-offline-AI dialog instead of generating immediately.
     * On every subsequent tap (and after the dialog is answered once), this
     * just delegates to [goToInterpretation] + [requestInterpretation].
     */
    fun onInterpretTapped() {
        if (_state.value.isInterpreting) return
        viewModelScope.launch {
            val ai = settingsRepository.ai.first()
            if (!ai.interpretPromptShown) {
                _state.update {
                    it.copy(
                        showFirstTapPrompt = true,
                        firstTapDownloadBytes = modelInstaller.manifest.value.expectedBytes,
                    )
                }
            } else {
                beginInterpretation()
            }
        }
    }

    /** First-tap dialog: user picked "Install offline AI". */
    fun onFirstTapInstall() {
        viewModelScope.launch {
            settingsRepository.setInterpretPromptShown(true)
            settingsRepository.setAiBackend(AiBackendType.LOCAL_LLM)
            // Kick off the download in the background; the user will see
            // progress in Settings. THIS reading falls through to the
            // rule-based interpreter via InterpreterRegistry's fallback,
            // because the model isn't installed yet.
            modelInstaller.install()
            _state.update { it.copy(showFirstTapPrompt = false) }
            beginInterpretation()
        }
    }

    /** First-tap dialog: user picked "Not now". */
    fun onFirstTapNotNow() {
        viewModelScope.launch {
            settingsRepository.setInterpretPromptShown(true)
            _state.update { it.copy(showFirstTapPrompt = false) }
            beginInterpretation()
        }
    }

    /** Outside-tap / back-press on the dialog. Doesn't burn the flag — the user might tap Interpret again. */
    fun onFirstTapDismissed() {
        _state.update { it.copy(showFirstTapPrompt = false) }
    }

    private fun beginInterpretation() {
        goToInterpretation()
        requestInterpretation()
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
            val ai = settingsRepository.ai.first()
            val interpreter = interpreterRegistry.activeInterpreter()
            // The registry silently falls back to RULE_BASED when the user's
            // chosen backend is unavailable. That's the right default but
            // can be confusing — surface a one-shot notice so the user
            // knows why their reading just came out as rule-based prose.
            val notice = when {
                ai.backendType == AiBackendType.LOCAL_LLM &&
                    interpreter.type == AiBackendType.RULE_BASED ->
                    "Local AI not installed yet — using rule-based interpretation. Install from Settings → AI Interpreter."
                ai.backendType == AiBackendType.CLAUDE_API &&
                    interpreter.type == AiBackendType.RULE_BASED ->
                    "Add a Claude API key in Settings to use the cloud backend. Falling back to rule-based for this reading."
                else -> null
            }
            _state.update { it.copy(backendFallbackNotice = notice) }
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

    /** Called by the in-screen download banner if the install fails. */
    fun retryLocalInstall() = modelInstaller.install()

    /** Called by the in-screen download banner's cancel button. */
    fun cancelLocalInstall() = modelInstaller.cancel()

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
