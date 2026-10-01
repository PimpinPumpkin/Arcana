package com.arcana.feature.journal

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class JournalDetailUiState(
    val reading: Reading? = null,
    val spread: Spread? = null,
    val deck: DeckArt? = null,
    val notes: String = "",
    val isLoading: Boolean = true,
    /** The reading being written right now. It replaces the stored one on screen until it is done. */
    val draft: String? = null,
    val isInterpreting: Boolean = false,
    val status: String? = null,
    val progress: Float? = null,
    val error: String? = null,
    val notice: String? = null,
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val readingRepository: ReadingRepository,
    private val spreadRepository: SpreadRepository,
    private val settingsRepository: SettingsRepository,
    private val interpreterRegistry: InterpreterRegistry,
) : ViewModel() {

    private val readingId: String = checkNotNull(saved["readingId"])

    private val _state = MutableStateFlow(JournalDetailUiState())
    val state: StateFlow<JournalDetailUiState> = _state.asStateFlow()

    private var interpretJob: Job? = null
    private var notesJob: Job? = null

    // Loaded once, here. Loading from the screen ran again every time the screen came back into
    // view, which threw away notes that had been typed but not yet stored.
    init {
        viewModelScope.launch {
            val reading = readingRepository.getReading(readingId)
            // A saved reading carries its own copy of the layout, so it still draws after the
            // spread it came from is edited or deleted. Older ones look the spread up.
            val spread = when {
                reading == null -> null
                reading.spreadSnapshot != null -> reading.asSpread(reading.spreadSnapshot.orEmpty())
                else -> spreadRepository.getSpreadById(reading.spreadId) ?: reading.asSpread(emptyList())
            }
            val decks = settingsRepository.getAvailableDecks()
            _state.value = JournalDetailUiState(
                reading = reading,
                spread = spread,
                deck = decks.firstOrNull { it.id == reading?.deckArtId } ?: settingsRepository.currentDeck(),
                notes = reading?.notes.orEmpty(),
                isLoading = false,
            )
        }
    }

    private fun Reading.asSpread(positions: List<com.arcana.core.domain.model.Position>) = Spread(
        id = spreadId,
        name = spreadName,
        description = "",
        positions = positions,
        layout = SpreadLayout.CUSTOM,
        difficulty = SpreadDifficulty.INTERMEDIATE,
    )

    /** Notes are stored as they are typed, a moment after the typing pauses. */
    fun onNotesChange(text: String) {
        _state.update { it.copy(notes = text) }
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(NOTES_PAUSE_MS)
            readingRepository.updateNotes(readingId, text)
        }
    }

    /** Stores the notes at once. Called when the screen is left. */
    fun flushNotes() {
        if (notesJob?.isActive != true) return
        notesJob?.cancel()
        val text = _state.value.notes
        viewModelScope.launch { withContext(NonCancellable) { readingRepository.updateNotes(readingId, text) } }
    }

    fun deleteReading(onDone: () -> Unit) {
        notesJob?.cancel()
        interpretJob?.cancel()
        viewModelScope.launch {
            readingRepository.deleteReading(readingId)
            onDone()
        }
    }

    /** Writes a reading for these cards, or a fresh one in place of the one stored. */
    fun requestInterpretation() {
        val current = _state.value
        val reading = current.reading ?: return
        val spread = current.spread ?: return
        if (current.isInterpreting) return
        interpretJob?.cancel()
        interpretJob = viewModelScope.launch {
            _state.update { it.copy(isInterpreting = true, draft = "", error = null, status = null, progress = null) }
            val ai = settingsRepository.ai.first()
            val interpreter = interpreterRegistry.activeInterpreter()
            val fellBack = ai.backendType != AiBackendType.RULE_BASED && interpreter.type == AiBackendType.RULE_BASED
            _state.update { it.copy(notice = if (fellBack) "No reading model is installed yet, so this is the cards' own meanings." else null) }
            val request = InterpretationRequest(spread = spread, drawnCards = reading.drawnCards, question = reading.question)
            interpreter.interpret(request).collect { chunk ->
                when (chunk) {
                    is InterpretationChunk.Text -> _state.update { it.copy(draft = it.draft.orEmpty() + chunk.delta, status = null, progress = null) }
                    is InterpretationChunk.Status -> _state.update { it.copy(status = chunk.message, progress = chunk.progress) }
                    is InterpretationChunk.Error -> _state.update { it.copy(error = chunk.message, isInterpreting = false, draft = null, status = null, progress = null) }
                    is InterpretationChunk.Complete -> keep()
                }
            }
        }
    }

    /** Stops the writing and keeps what was written, if anything was. */
    fun stopInterpretation() {
        interpretJob?.cancel()
        viewModelScope.launch { keep() }
    }

    private suspend fun keep() {
        val text = _state.value.draft.orEmpty().trim()
        if (text.isNotEmpty()) readingRepository.updateInterpretation(readingId, text)
        _state.update {
            it.copy(
                reading = if (text.isNotEmpty()) it.reading?.copy(interpretation = text) else it.reading,
                draft = null,
                isInterpreting = false,
                status = null,
                progress = null,
            )
        }
    }

    private companion object {
        const val NOTES_PAUSE_MS = 600L
    }
}
