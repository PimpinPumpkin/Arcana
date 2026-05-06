package com.arcana.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Position
import com.arcana.core.ui.components.TarotCardView
import com.arcana.core.ui.theme.ArcanaColors
import com.arcana.core.ui.theme.CardShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogPhysicalReadingScreen(
    onBack: () -> Unit,
    onSaved: (readingId: String) -> Unit,
    viewModel: LogPhysicalReadingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spread = state.spread
    val deck = state.deck
    val isSaved = state.savedReadingId != null
    val snackbarHostState = remember { SnackbarHostState() }

    val savedMessage = stringResource(R.string.journal_saved_message)
    val viewLabel = stringResource(R.string.journal_saved_view)
    LaunchedEffect(state.savedReadingId) {
        val id = state.savedReadingId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = savedMessage,
            actionLabel = viewLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) onSaved(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spread?.let { "Log: ${it.name}" } ?: "Log a reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (spread == null || deck == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    "Tap each position to assign the card you drew. Tap again to change orientation or remove it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            spread.positions.forEach { pos ->
                val posIndex = pos.index
                item(key = "pos-$posIndex") {
                    PositionEditorRow(
                        position = pos,
                        drawn = state.selections[posIndex],
                        onTapEmpty = { viewModel.openPicker(posIndex) },
                        onChangeCard = { viewModel.openPicker(posIndex) },
                        onFlipOrientation = { viewModel.toggleOrientation(posIndex) },
                        onClear = { viewModel.clearPosition(posIndex) },
                        deck = deck,
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = viewModel::onQuestionChanged,
                    label = { Text("Question (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    minLines = 2,
                )
            }

            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChanged,
                    label = { Text("Notes") },
                    placeholder = { Text("What came up for you?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    minLines = 4,
                )
            }

            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = viewModel.isComplete && !state.isSaving && !isSaved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    val filled = state.selections.size
                    val total = spread.cardCount
                    Text(
                        when {
                            isSaved -> stringResource(R.string.journal_save_done)
                            viewModel.isComplete -> "Save reading"
                            else -> "Save reading ($filled / $total filled)"
                        },
                    )
                }
            }
        }

        // Card picker bottom sheet
        if (state.pickerForPosition != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.closePicker() },
                sheetState = sheetState,
            ) {
                CardPickerSheet(
                    query = state.pickerQuery,
                    onQueryChange = viewModel::onPickerQueryChanged,
                    cards = viewModel.filteredCards(),
                    onPick = { viewModel.selectCardForPosition(it) },
                    deck = deck,
                )
            }
        }
    }
}

@Composable
private fun PositionEditorRow(
    position: Position,
    drawn: DrawnCard?,
    onTapEmpty: () -> Unit,
    onChangeCard: () -> Unit,
    onFlipOrientation: () -> Unit,
    onClear: () -> Unit,
    deck: com.arcana.core.domain.model.DeckArt,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Card slot
        Box(
            modifier = Modifier.width(96.dp),
        ) {
            if (drawn != null) {
                TarotCardView(
                    card = drawn.card,
                    deck = deck,
                    orientation = drawn.orientation,
                    onClick = onChangeCard,
                )
            } else {
                EmptySlot(onClick = onTapEmpty)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                "${position.index}. ${position.label}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                position.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (drawn != null) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        drawn.card.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (drawn.orientation == Orientation.REVERSED) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(ArcanaColors.ReversedRibbon)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "Reversed",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = onFlipOrientation) {
                        Icon(
                            Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip orientation",
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Remove card")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(CardShapes.tarotCard)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CardShapes.tarotCard,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Tap to add",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    cards: List<com.arcana.core.domain.model.Card>,
    onPick: (com.arcana.core.domain.model.Card) -> Unit,
    deck: com.arcana.core.domain.model.DeckArt,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search cards…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 90.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cards, key = { it.id }) { card ->
                Card(
                    onClick = { onPick(card) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        TarotCardView(card = card, deck = deck)
                        Text(
                            card.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
