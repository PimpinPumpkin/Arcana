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
import androidx.compose.material3.AssistChip
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.DeckArt

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
                                "Pick a folder of card images named to match Arcana's convention.",
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
                                "Pick an Arcana deck export (.zip). Always lands as a fresh deck.",
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
                                "Cards without an image will fall back to the text plate. " +
                                    "You can fix individual ones via Edit deck.",
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
                    Text("This removes the deck and its image files from app storage. Saved readings keep working — they'll show with the bundled deck.")
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
                text = { Text("$msg Hand the .zip to anyone with Arcana — they can pull it in via \"Import a deck from a ZIP\".") },
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
                    AssistChip(
                        onClick = onSelect,
                        label = {
                            Text(
                                if (deck.isBundled) "Bundled" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
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
                    "Pick a folder containing card images. Files must be named to match Arcana's imageRef convention — e.g. major_00_fool.jpg, wands_01_ace.jpg, swords_page.jpg. " +
                        "Extension can be .jpg / .png / .webp; case doesn't matter. Missing files fall back to the text plate (you can fix specific cards later via Edit deck).",
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
                Text(
                    "Two ways to give Arcana your own card art.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    "Bulk import from a folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Tap \"Import a deck from a folder\" above. The folder you pick should contain image files named to match Arcana's imageRef convention. The convention is:\n\n" +
                        "  • Major arcana: major_NN_name.jpg  (e.g. major_00_fool.jpg, major_21_world.jpg)\n" +
                        "  • Minor arcana number cards: <suit>_NN_rank.jpg  (e.g. wands_01_ace.jpg, cups_05_five.jpg)\n" +
                        "  • Minor arcana courts: <suit>_<rank>.jpg  (e.g. swords_page.jpg, pentacles_king.jpg)\n" +
                        "  • Card back (optional): back.jpg\n\n" +
                        "Suits: wands, cups, swords, pentacles. Extensions .jpg / .png / .webp all work; case doesn't matter. Missing files just render the text fallback for that card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Text(
                    "Per-card override",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "After a deck is imported (or any time later), tap the pencil icon next to a CUSTOM deck row to open the per-card editor. Tap any individual card to replace just that card's image — useful for fixing one or two cards in an otherwise-good import without touching the rest. The pencil only appears for custom decks; bundled decks (e.g. Rider-Waite) are read-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Text(
                    "Share / move decks with ZIP",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "The download icon next to a custom deck packs its manifest + every card image into a single .zip you can save anywhere. Hand the .zip to anyone with Arcana — they tap \"Import a deck from a ZIP\" and pick the file. ZIP imports always land as fresh decks (won't overwrite anything you have).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Text(
                    "Files live in app-private storage and are wiped on uninstall — back them up if they're irreplaceable.",
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
