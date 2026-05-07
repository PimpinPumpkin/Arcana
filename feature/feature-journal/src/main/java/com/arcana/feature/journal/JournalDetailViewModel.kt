package com.arcana.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadDifficulty
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.repository.ReadingRepository
import com.arcana.core.domain.repository.SettingsRepository
import com.arcana.core.domain.repository.SpreadRepository
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.InterpreterRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    /**
     * Live-streaming interpretation while the user has tapped Generate /
     * Re-interpret. Replaces what's shown on the page until generation
     * completes; on Complete we persist it via updateInterpretation and
     * clear this back to null.
     */
    val interpretationDraft: String? = null,
    val isInterpreting: Boolean = false,
    val interpretationStatus: String? = null,
    val interpretationError: String? = null,
    val backendFallbackNotice: String? = null,
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
    private val spreadRepository: SpreadRepository,
    private val settingsRepository: SettingsRepository,
    private val interpreterRegistry: InterpreterRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(JournalDetailUiState())
    val state: StateFlow<JournalDetailUiState> = _state.asStateFlow()
    private var interpretJob: Job? = null

    fun load(readingId: String) {
        viewModelScope.launch {
            val reading = readingRepository.getReading(readingId)
            // Prefer the per-reading snapshot if present (v0.5.0+) so the
            // journal display is robust against later edit/delete of the
            // underlying spread. Fall back to the live repo lookup for old
            // pre-snapshot rows. Final fallback: synthesize a minimal Spread
            // from the drawn cards' position indexes so the screen still
            // renders something meaningful even if both lookups fail.
            val spread = when {
                reading == null -> null
                reading.spreadSnapshot != null -> reading.toSnapshotSpread()
                else -> reading.spreadId.let { spreadRepository.getSpreadById(it) }
                    ?: reading.toFallbackSpread()
            }
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

    private fun Reading.toSnapshotSpread(): Spread = Spread(
        id = spreadId,
        name = spreadName,
        description = "",
        positions = spreadSnapshot.orEmpty(),
        layout = SpreadLayout.CUSTOM,
        difficulty = SpreadDifficulty.INTERMEDIATE,
    )

    /**
     * Used when both the per-reading snapshot AND the live spread lookup
     * fail. Builds a no-op Spread so the screen renders the question /
     * interpretation / notes; cards won't appear in the spread board but
     * everything else is intact, which beats a "Reading not found" error
     * for a reading that does, in fact, exist.
     */
    private fun Reading.toFallbackSpread(): Spread = Spread(
        id = spreadId,
        name = spreadName,
        description = "",
        positions = emptyList(),
        layout = SpreadLayout.CUSTOM,
        difficulty = SpreadDifficulty.INTERMEDIATE,
    )

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

    /**
     * Run AI interpretation against this saved reading. Works for both
     * digital pulls (re-interpret) and physical readings (first-time
     * interpret). Writes the final text into the reading via
     * [ReadingRepository.updateInterpretation] when generation completes.
     */
    fun requestInterpretation() {
        val current = _state.value
        val reading = current.reading ?: return
        val spread = current.spread ?: return
        if (current.isInterpreting) return
        interpretJob?.cancel()
        interpretJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isInterpreting = true,
                    interpretationDraft = "",
                    interpretationError = null,
                    interpretationStatus = "Starting…",
                )
            }
            val ai = settingsRepository.ai.first()
            val interpreter = interpreterRegistry.activeInterpreter()
            val notice = when {
                ai.backendType == AiBackendType.LOCAL_LLM &&
                    interpreter.type == AiBackendType.RULE_BASED ->
                    "Local AI not installed yet — using rule-based interpretation. Install from Settings → AI Interpreter."
                ai.backendType == AiBackendType.CLAUDE_API &&
                    interpreter.type == AiBackendType.RULE_BASED ->
                    "Add a Claude API key in Settings to use the cloud backend. Falling back to rule-based."
                else -> null
            }
            _state.update { it.copy(backendFallbackNotice = notice) }

            val request = InterpretationRequest(
                spread = spread,
                drawnCards = reading.drawnCards,
                question = reading.question,
            )
            interpreter.interpret(request).collect { chunk ->
                when (chunk) {
                    is InterpretationChunk.Text -> _state.update {
                        it.copy(
                            interpretationDraft = (it.interpretationDraft.orEmpty()) + chunk.delta,
                            interpretationStatus = null,
                        )
                    }
                    is InterpretationChunk.Status -> _state.update {
                        it.copy(interpretationStatus = chunk.message)
                    }
                    is InterpretationChunk.Error -> _state.update {
                        it.copy(
                            interpretationError = chunk.message,
                            isInterpreting = false,
                            interpretationStatus = null,
                        )
                    }
                    is InterpretationChunk.Complete -> {
                        val final = _state.value.interpretationDraft.orEmpty().trim()
                        if (final.isNotEmpty()) {
                            readingRepository.updateInterpretation(reading.id, final)
                            _state.update {
                                it.copy(
                                    reading = it.reading?.copy(interpretation = final),
                                    interpretationDraft = null,
                                    isInterpreting = false,
                                    interpretationStatus = null,
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    isInterpreting = false,
                                    interpretationStatus = null,
                                    interpretationDraft = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun cancelInterpretation() {
        interpretJob?.cancel()
        _state.update {
            it.copy(
                isInterpreting = false,
                interpretationDraft = null,
                interpretationStatus = null,
            )
        }
    }
}
