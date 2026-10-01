package com.arcana.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.ui.util.deckArtIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeckScreen(
    onBack: () -> Unit,
    viewModel: EditDeckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var helpOpen by remember { mutableStateOf(false) }
    var pendingDeleteImage by remember { mutableStateOf<Card?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.onImagePicked(uri)
        } else {
            viewModel.cancelPick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deck?.name ?: "Edit deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { helpOpen = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About per-card editing")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…")
                }
            }
            state.notFound -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Deck not found or can't be edited (bundled decks are read-only).")
                }
            }
            else -> {
                val deck = state.deck ?: return@Scaffold
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "metadata", span = { GridItemSpan(maxLineSpan) }) {
                        DeckMetadataEditor(
                            name = state.nameDraft,
                            artist = state.artistDraft,
                            description = state.descriptionDraft,
                            isDirty = state.isDirty,
                            onNameChange = viewModel::setName,
                            onArtistChange = viewModel::setArtist,
                            onDescriptionChange = viewModel::setDescription,
                            onSave = viewModel::saveMetadata,
                        )
                    }
                    item(key = "divider", span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Tap a card to pick an image for it. Long-press to revert to the text fallback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(state.cards, key = { it.id }) { card ->
                        CardCell(
                            card = card,
                            deck = deck,
                            hasImage = card.id in state.cardsWithImage,
                            onTap = {
                                viewModel.beginPickFor(card)
                                imagePicker.launch("image/*")
                            },
                            onLongTap = {
                                if (card.id in state.cardsWithImage) {
                                    pendingDeleteImage = card
                                }
                            },
                        )
                    }
                }
            }
        }

        if (helpOpen) {
            AlertDialog(
                onDismissRequest = { helpOpen = false },
                title = { Text("Per-card editor") },
                text = {
                    Column {
                        Text(
                            "Replace any single card's image without re-importing the whole deck.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Tap a card to pick an image for it from your photos or files. " +
                                "A card with no image shows its name instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "Long-press a card with a custom image to revert it to the text fallback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { helpOpen = false }) { Text("Got it") }
                },
            )
        }

        pendingDeleteImage?.let { card ->
            AlertDialog(
                onDismissRequest = { pendingDeleteImage = null },
                title = { Text("Revert ${card.name}?") },
                text = { Text("Removes the custom image for this one card. The card will render the text fallback.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCardImage(card)
                        pendingDeleteImage = null
                    }) { Text("Revert") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteImage = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun DeckMetadataEditor(
    name: String,
    artist: String,
    description: String,
    isDirty: Boolean,
    onNameChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Deck name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = artist,
            onValueChange = onArtistChange,
            label = { Text("Artist") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            minLines = 2,
        )
        Button(
            onClick = onSave,
            enabled = isDirty,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (isDirty) "Save deck info" else "Saved")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardCell(
    card: Card,
    deck: DeckArt,
    hasImage: Boolean,
    onTap: () -> Unit,
    onLongTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val index = deckArtIndex()
            val version = index.version
            val uri = remember(deck.id, card.imageRef, version, hasImage) { index.cardUri(deck, card.imageRef) }
            if (hasImage && uri != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(uri, version, context) {
                        ImageRequest.Builder(context)
                            .data(uri)
                            .memoryCacheKey("arcana-edit-${deck.id}-${card.id}-$version")
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Text fallback so the user sees which card the cell is for.
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(4.dp),
                )
            }
            if (hasImage) {
                // Tiny pill in the corner to indicate this card has a custom image.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .background(Color(0xFF52A57A), RoundedCornerShape(4.dp)),
                )
            }
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            maxLines = 1,
        )
    }
}

