package com.arcana.feature.spreads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.PositionCoords
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadDifficulty
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.repository.SpreadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Editor state for the custom-spread screen. Positions live in domain shape
 * so save() can hand them straight to the repo without a translation step.
 */
data class CustomSpreadEditorUiState(
    /** ID we're editing; null means we're creating a brand-new spread. */
    val editingExistingId: String? = null,
    val name: String = "",
    val description: String = "",
    val positions: List<Position> = emptyList(),
    /** Non-null while the position-editor dialog is open. */
    val draft: PositionDraft? = null,
    val isLoading: Boolean = false,
)

/**
 * Working copy of a position the user is currently editing or adding. Cell
 * coordinates are integers (column / row indexes within the editor grid)
 * so the dialog UI stays simple; we map them back to normalized 0..1
 * floats only when committing into [Position.coords].
 */
data class PositionDraft(
    val index: Int,
    val label: String,
    val meaning: String,
    val col: Int,
    val row: Int,
    val rotationDegrees: Float,
    val isNew: Boolean,
)

@HiltViewModel
class CustomSpreadEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val spreadRepository: SpreadRepository,
) : ViewModel() {

    private val editingId: String? = savedStateHandle["spreadId"]

    private val _state = MutableStateFlow(CustomSpreadEditorUiState(isLoading = editingId != null))
    val state: StateFlow<CustomSpreadEditorUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                spreadRepository.getSpreadById(editingId)?.let { existing ->
                    _state.value = CustomSpreadEditorUiState(
                        editingExistingId = existing.id,
                        name = existing.name,
                        description = existing.description,
                        positions = existing.positions,
                        isLoading = false,
                    )
                } ?: run { _state.update { it.copy(isLoading = false) } }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }

    /** Tap on an empty grid cell → start adding a new position there. */
    fun beginAddPositionAt(col: Int, row: Int) {
        _state.update { current ->
            val nextIndex = (current.positions.maxOfOrNull { it.index } ?: 0) + 1
            current.copy(
                draft = PositionDraft(
                    index = nextIndex,
                    label = "",
                    meaning = "",
                    col = col,
                    row = row,
                    rotationDegrees = 0f,
                    isNew = true,
                ),
            )
        }
    }

    /** Edit an existing position (from the list below the grid). */
    fun beginEditPosition(positionIndex: Int) {
        val current = _state.value
        val existing = current.positions.firstOrNull { it.index == positionIndex } ?: return
        _state.update {
            it.copy(
                draft = PositionDraft(
                    index = existing.index,
                    label = existing.label,
                    meaning = existing.meaning,
                    col = (existing.coords.x * COLS - 0.5f).roundToInt().coerceIn(0, COLS - 1),
                    row = (existing.coords.y * ROWS - 0.5f).roundToInt().coerceIn(0, ROWS - 1),
                    rotationDegrees = existing.coords.rotationDegrees,
                    isNew = false,
                ),
            )
        }
    }

    fun updateDraft(transform: (PositionDraft) -> PositionDraft) {
        _state.update { current ->
            val draft = current.draft ?: return@update current
            current.copy(draft = transform(draft))
        }
    }

    fun cancelDraft() = _state.update { it.copy(draft = null) }

    fun commitDraft() {
        val current = _state.value
        val draft = current.draft ?: return
        if (draft.label.isBlank()) return // require a label
        val pos = Position(
            index = draft.index,
            label = draft.label.trim(),
            meaning = draft.meaning.trim(),
            coords = PositionCoords(
                x = (draft.col + 0.5f) / COLS,
                y = (draft.row + 0.5f) / ROWS,
                rotationDegrees = draft.rotationDegrees,
            ),
        )
        _state.update {
            val updated = it.positions.toMutableList()
            val existingIdx = updated.indexOfFirst { p -> p.index == draft.index }
            if (existingIdx >= 0) updated[existingIdx] = pos else updated += pos
            it.copy(positions = updated, draft = null)
        }
    }

    fun deletePosition(positionIndex: Int) {
        _state.update { current ->
            val survivors = current.positions
                .filter { it.index != positionIndex }
                // Re-index 1..N so they stay contiguous; keeps display labels
                // and DrawnCard.positionIndex in sync after a delete.
                .sortedBy { it.index }
                .mapIndexed { i, pos -> pos.copy(index = i + 1) }
            current.copy(positions = survivors, draft = null)
        }
    }

    fun saveSpread(onSaved: (spreadId: String) -> Unit) {
        val current = _state.value
        if (current.name.isBlank() || current.positions.isEmpty()) return
        viewModelScope.launch {
            val id = current.editingExistingId
                ?: "custom-${System.currentTimeMillis().toString(16)}"
            val spread = Spread(
                id = id,
                name = current.name.trim(),
                description = current.description.trim(),
                positions = current.positions.sortedBy { it.index },
                layout = SpreadLayout.CUSTOM,
                difficulty = SpreadDifficulty.INTERMEDIATE,
            )
            spreadRepository.saveCustomSpread(spread)
            onSaved(id)
        }
    }

    companion object {
        /** Editor grid dimensions. Snap-to-cell placement keeps cards from overlapping unless explicitly stacked. */
        const val COLS: Int = 4
        const val ROWS: Int = 6
    }
}
