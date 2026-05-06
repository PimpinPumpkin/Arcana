package com.arcana.feature.spreads

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Spread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadPickerScreen(
    onPickSpread: (spreadId: String) -> Unit,
    /**
     * Optional. When null, the "Create custom spread" entry and the
     * edit/delete affordances on user-authored spreads are hidden.
     * Use null in contexts like the log-physical picker where customization
     * is out of scope.
     */
    onCreateCustom: (() -> Unit)? = null,
    onEditCustom: ((spreadId: String) -> Unit)? = null,
    viewModel: SpreadPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var spreadPendingDelete by remember { mutableStateOf<Spread?>(null) }

    // Local mutable copy of the order so drag swaps can run live without
    // a roundtrip through DataStore. We commit to the repo on drag-end.
    // Re-syncs whenever the repo emits (e.g. a custom spread is created
    // while we're not mid-drag).
    var orderedSpreads by remember { mutableStateOf<List<Spread>>(state.spreads) }
    LaunchedEffect(state.spreads) { orderedSpreads = state.spreads }

    val lazyListState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.spreads_title)) })
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading spreads…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") {
                Text(
                    text = stringResource(R.string.spreads_pick_one),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (onCreateCustom != null) {
                item("create-custom") {
                    CreateCustomCard(onClick = onCreateCustom)
                }
            }
            items(orderedSpreads, key = { it.id }) { spread ->
                val canCustomize = onEditCustom != null && spread.id in state.customIds
                val isDragging = draggingId == spread.id
                Box(
                    modifier = Modifier
                        .animateItem()
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffsetY
                                shadowElevation = 14.dp.toPx()
                            }
                        }
                        .pointerInput(spread.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = spread.id
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    dragOffsetY += drag.y
                                    val info = lazyListState.layoutInfo.visibleItemsInfo
                                    val draggedItem = info.firstOrNull { it.key == spread.id }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val center = draggedItem.offset + draggedItem.size / 2 + dragOffsetY
                                    val target = info.firstOrNull { other ->
                                        other.key != spread.id &&
                                            other.key is String &&
                                            orderedSpreads.any { it.id == other.key } &&
                                            center >= other.offset.toFloat() &&
                                            center <= (other.offset + other.size).toFloat()
                                    }
                                    if (target != null) {
                                        val fromIdx = orderedSpreads.indexOfFirst { it.id == spread.id }
                                        val toIdx = orderedSpreads.indexOfFirst { it.id == target.key }
                                        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                            orderedSpreads = orderedSpreads.toMutableList().apply {
                                                add(toIdx, removeAt(fromIdx))
                                            }
                                            // Compensate so the card stays under the finger
                                            // after the layout changes.
                                            dragOffsetY += (fromIdx - toIdx) * draggedItem.size
                                        }
                                    }
                                },
                                onDragEnd = {
                                    viewModel.setSpreadOrder(orderedSpreads.map { it.id })
                                    draggingId = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffsetY = 0f
                                },
                            )
                        },
                ) {
                    SpreadCard(
                        spread = spread,
                        isCustom = spread.id in state.customIds,
                        showAffordances = canCustomize,
                        onClick = { onPickSpread(spread.id) },
                        onEdit = { onEditCustom?.invoke(spread.id) },
                        onDelete = { spreadPendingDelete = spread },
                    )
                }
            }
        }
    }

    spreadPendingDelete?.let { spread ->
        AlertDialog(
            onDismissRequest = { spreadPendingDelete = null },
            title = { Text("Delete \"${spread.name}\"?") },
            text = {
                Text("This removes the custom spread and any saved readings keep their snapshot. Bundled spreads can't be deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomSpread(spread.id)
                    spreadPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { spreadPendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CreateCustomCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.spreads_create_custom),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.spreads_create_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SpreadCard(
    spread: Spread,
    isCustom: Boolean,
    showAffordances: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spread.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (spread.description.isNotBlank()) {
                        Text(
                            text = spread.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (showAffordances) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit spread")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Close, contentDescription = "Delete spread")
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            stringResource(R.string.spreads_card_count, spread.cardCount),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            spread.difficulty.displayName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                if (isCustom) {
                    AssistChip(
                        onClick = onClick,
                        label = {
                            Text("Custom", style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }
    }
}
