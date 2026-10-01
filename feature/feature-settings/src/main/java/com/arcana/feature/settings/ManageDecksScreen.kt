package com.arcana.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.ui.components.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDecksScreen(
    onBack: () -> Unit,
    onEditDeck: (deckId: String) -> Unit,
    viewModel: ManageDecksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var importDialogOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }

    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importZip(uri) }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.completeExport(uri) else viewModel.cancelExport()
    }
    // When the VM enters pendingExport state, kick off the SAF picker.
    LaunchedEffect(state.pendingExport?.id) {
        val deck = state.pendingExport ?: return@LaunchedEffect
        zipExportLauncher.launch(viewModel.suggestedExportName(deck))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage decks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { helpOpen = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About custom decks")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    onClick = { importDialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Add, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "Import a deck from a folder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "A folder of card images, each named for its card.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = { zipImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Unarchive, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                "Import a deck from a ZIP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "A deck exported from Arcana, or any ZIP of card images. It is added as a new deck.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
            items(state.decks, key = { it.id }) { deck ->
                DeckRow(
                    deck = deck,
                    selected = state.activeDeckId == deck.id,
                    onSelect = { viewModel.setActive(deck.id) },
                    onEdit = if (!deck.isBundled) { -> onEditDeck(deck.id) } else null,
                    onDelete = if (!deck.isBundled) { -> viewModel.requestDelete(deck) } else null,
                    onExportZip = if (!deck.isBundled) { -> viewModel.beginExport(deck) } else null,
                )
            }
        }

        if (importDialogOpen) {
            ImportDeckDialog(
                isImporting = state.isImporting,
                onDismiss = { importDialogOpen = false },
                onImport = { uri, name, artist, description ->
                    viewModel.importDeck(uri, name, artist, description)
                },
            )
        }

        state.importResult?.let { result ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.dismissImportResult()
                    importDialogOpen = false
                },
                title = { Text("Imported \"${state.decks.firstOrNull { it.id == result.deckId }?.name ?: "deck"}\"") },
                text = {
                    Column {
                        Text(
                            "Matched ${result.matched} of ${result.total} cards.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (result.missingRefs.isNotEmpty()) {
                            Text(
                                "A card with no image shows its name instead. " +
                                    "You can give it one in Edit deck.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissImportResult()
                        importDialogOpen = false
                        onEditDeck(result.deckId)
                    }) { Text("Edit deck") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.dismissImportResult()
                        importDialogOpen = false
                    }) { Text("Done") }
                },
            )
        }

        state.importError?.let { msg ->
            AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                title = { Text("Import failed") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImportResult) { Text("OK") }
                },
            )
        }

        state.pendingDelete?.let { deck ->
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Delete \"${deck.name}\"?") },
                text = {
                    Text("This removes the deck and its images from the phone. Saved readings keep working and show with the bundled deck.")
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                },
            )
        }

        state.exportSuccess?.let { msg ->
            AlertDialog(
                onDismissRequest = viewModel::dismissExportSuccess,
                title = { Text("Deck exported") },
                text = { Text("$msg Anyone with Arcana can add it with \"Import a deck from a ZIP\".") },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissExportSuccess) { Text("Done") }
                },
            )
        }

        if (helpOpen) {
            HelpDialog(onDismiss = { helpOpen = false })
        }
    }
}

@Composable
private fun DeckRow(
    deck: DeckArt,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onExportZip: (() -> Unit)?,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${deck.artist}${deck.year?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Tag(if (deck.isBundled) "Bundled" else "Custom")
                }
            }
            if (onExportZip != null) {
                IconButton(onClick = onExportZip) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export deck as ZIP")
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit deck")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "Delete deck")
                }
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun ImportDeckDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onImport: (folderUri: android.net.Uri, name: String, artist: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> pickedUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import deck") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Pick a folder of card images. Each file is named for its card, like \"The Fool\", \"Queen of Cups\" or \"wands_05\", as a JPEG, PNG or WebP. " +
                        "A card with no image shows its name instead, and can be given one later in Edit deck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                )
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(pickedUri?.lastPathSegment?.let { "Folder: …${it.takeLast(30)}" } ?: "Pick folder")
                }
                if (isImporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            "Importing…",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isImporting && pickedUri != null && name.isNotBlank(),
                onClick = {
                    val uri = pickedUri ?: return@TextButton
                    onImport(uri, name.trim(), artist.trim(), description.trim())
                },
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) { Text("Cancel") }
        },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom decks") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpHeading("A whole deck at once")
                HelpText(
                    "Import a folder or a ZIP of images. Arcana reads which card each one is from its file name, so name each file for its card. All of these work:\n\n" +
                        "  • The Fool, The High Priestess, Wheel of Fortune\n" +
                        "  • Queen of Cups, 10 of Swords, Ace of Pentacles\n" +
                        "  • wands_05, Cups11, pents14 (11 to 14 are page, knight, queen, king)\n" +
                        "  • major_16 for a trump with no name on it\n" +
                        "  • back, for the card back\n\n" +
                        "JPEG, PNG and WebP all work. Very large images are scaled down as they come in. A card with no image shows its name instead.",
                )
                HelpHeading("One card at a time")
                HelpText("The pencil beside a custom deck opens it card by card. Tap a card to give it an image or swap the one it has. The bundled deck cannot be changed.")
                HelpHeading("Sharing a deck")
                HelpText(
                    "The download icon beside a custom deck saves it as one ZIP. Anyone with Arcana can add it with \"Import a deck from a ZIP\". An import is always added as a new deck and never replaces one you have.",
                )
                Text(
                    "Imported decks are stored inside the app and are removed if it is uninstalled. Export any you cannot replace.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun HelpHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
}
