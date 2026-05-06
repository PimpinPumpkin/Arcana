package com.arcana.feature.spreads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Position
import com.arcana.feature.spreads.CustomSpreadEditorViewModel.Companion.COLS
import com.arcana.feature.spreads.CustomSpreadEditorViewModel.Companion.ROWS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSpreadEditorScreen(
    onBack: () -> Unit,
    onSaved: (spreadId: String) -> Unit,
    viewModel: CustomSpreadEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isEditing = state.editingExistingId != null

    // Intercept both the system back gesture and the toolbar back arrow so
    // either path runs through the discard-confirm gate.
    BackHandler(enabled = true) { viewModel.requestBack(onBack) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit spread" else "Create spread") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.requestBack(onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                placeholder = { Text("e.g. Morning Reflection") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Text(
                "Tap a cell to place a position. Cards stack if you tap a cell that's already occupied.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditorBoard(
                positions = state.positions,
                onCellTap = viewModel::beginAddPositionAt,
                onPositionTap = viewModel::beginEditPosition,
            )

            if (state.positions.isNotEmpty()) {
                Text(
                    "Positions (${state.positions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                state.positions.sortedBy { it.index }.forEach { pos ->
                    PositionListRow(
                        position = pos,
                        onEdit = { viewModel.beginEditPosition(pos.index) },
                        onDelete = { viewModel.deletePosition(pos.index) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveSpread(onSaved) },
                enabled = state.name.isNotBlank() && state.positions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEditing) "Save changes" else "Save spread")
            }
        }

        if (state.showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDiscard,
                title = { Text("Discard changes?") },
                text = { Text("You have unsaved changes to this spread. Going back now will lose them.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDiscard(onBack) }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDiscard) { Text("Keep editing") }
                },
            )
        }

        state.draft?.let { draft ->
            PositionEditorDialog(
                draft = draft,
                positions = state.positions,
                onLabelChange = { v -> viewModel.updateDraft { it.copy(label = v) } },
                onMeaningChange = { v -> viewModel.updateDraft { it.copy(meaning = v) } },
                onCellChange = { c, r -> viewModel.updateDraft { it.copy(col = c, row = r) } },
                onRotationChange = { deg -> viewModel.updateDraft { it.copy(rotationDegrees = deg) } },
                onSave = viewModel::commitDraft,
                onDelete = if (!draft.isNew) {
                    { viewModel.deletePosition(draft.index) }
                } else null,
                onDismiss = viewModel::cancelDraft,
            )
        }
    }
}

@Composable
private fun EditorBoard(
    positions: List<Position>,
    onCellTap: (col: Int, row: Int) -> Unit,
    onPositionTap: (positionIndex: Int) -> Unit,
) {
    // Group positions by their grid cell so we can show stacked indicators.
    val byCell: Map<Pair<Int, Int>, List<Position>> = positions.groupBy {
        val col = (it.coords.x * COLS - 0.5f).toInt().coerceIn(0, COLS - 1)
        val row = (it.coords.y * ROWS - 0.5f).toInt().coerceIn(0, ROWS - 1)
        col to row
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 4 cols × 6 rows. Aspect ratio matches: card is taller than wide.
            // Each cell is roughly card-aspect (0.62) so the grid shape looks
            // like a real spread board.
            for (row in 0 until ROWS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0 until COLS) {
                        val occupants = byCell[col to row].orEmpty()
                        BoardCell(
                            occupants = occupants,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.7f),
                            onTap = {
                                if (occupants.isEmpty()) onCellTap(col, row)
                                else onPositionTap(occupants.last().index)
                            },
                        )
                    }
                }
                if (row < ROWS - 1) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BoardCell(
    occupants: List<Position>,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val occupied = occupants.isNotEmpty()
    Box(
        modifier = modifier
            .background(
                color = if (occupied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                width = if (occupied) 2.dp else 1.dp,
                color = if (occupied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (occupied) {
            // Show the latest occupant's index, rotated to match its
            // chosen rotation, plus a "+N" indicator if stacked.
            val top = occupants.last()
            Box(
                modifier = Modifier
                    .graphicsLayer { rotationZ = top.coords.rotationDegrees },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${top.index}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (occupants.size > 1) {
                Text(
                    text = "+${occupants.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun PositionListRow(
    position: Position,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${position.index}.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (position.meaning.isNotBlank()) {
                    Text(
                        text = position.meaning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (position.coords.rotationDegrees != 0f) {
                Text(
                    text = "${position.coords.rotationDegrees.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionEditorDialog(
    draft: PositionDraft,
    positions: List<Position>,
    onLabelChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onCellChange: (col: Int, row: Int) -> Unit,
    onRotationChange: (Float) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isNew) "Add position" else "Edit position ${draft.index}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = onLabelChange,
                    label = { Text("Label (e.g. Past)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.meaning,
                    onValueChange = onMeaningChange,
                    label = { Text("What this position represents") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Text(
                    "Rotation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val rotations = listOf(0f, 90f, 180f, 270f)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    rotations.forEachIndexed { i, deg ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = rotations.size),
                            selected = draft.rotationDegrees == deg,
                            onClick = { onRotationChange(deg) },
                        ) { Text("${deg.toInt()}°") }
                    }
                }

                Text(
                    "Position on board",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                MiniBoardPicker(
                    selectedCol = draft.col,
                    selectedRow = draft.row,
                    occupiedCells = positions
                        .filter { it.index != draft.index }
                        .map {
                            val c = (it.coords.x * COLS - 0.5f).toInt().coerceIn(0, COLS - 1)
                            val r = (it.coords.y * ROWS - 0.5f).toInt().coerceIn(0, ROWS - 1)
                            c to r
                        }
                        .toSet(),
                    onCellTap = onCellChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.label.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete") }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Mini grid inside the position-editor dialog. Lets the user move a position
 * to a different cell. Cells already occupied by *other* positions are
 * dimmed; tapping one anyway is allowed (that's how stacking works).
 */
@Composable
private fun MiniBoardPicker(
    selectedCol: Int,
    selectedRow: Int,
    occupiedCells: Set<Pair<Int, Int>>,
    onCellTap: (col: Int, row: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until COLS) {
                    val isSelected = col == selectedCol && row == selectedRow
                    val isOccupied = (col to row) in occupiedCells
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.7f)
                            .background(
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isOccupied -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(4.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clickable { onCellTap(col, row) },
                    )
                }
            }
        }
    }
}
