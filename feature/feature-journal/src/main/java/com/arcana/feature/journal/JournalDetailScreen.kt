package com.arcana.feature.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.ui.components.MarkdownText
import com.arcana.core.ui.components.SpreadBoard
import com.arcana.core.ui.components.WritingIndicator
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    onBack: () -> Unit,
    onCardClick: (cardId: String) -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    // Notes are stored a moment after typing stops; leaving the screen stores them at once.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNotes() }
    DisposableEffect(Unit) { onDispose { viewModel.flushNotes() } }

    val view = LocalView.current
    DisposableEffect(state.isInterpreting) {
        view.keepScreenOn = state.isInterpreting
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.reading?.spreadName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.reading != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete this reading") }
                    }
                },
            )
        },
    ) { padding ->
        val reading = state.reading
        val spread = state.spread
        val deck = state.deck
        if (reading == null || spread == null || deck == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.isLoading) "" else "This reading is no longer in the journal.")
            }
            return@Scaffold
        }
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            val viewport = maxHeight
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(reading.timestampEpochMs)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                reading.question?.let {
                    Text(text = "“$it”", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                SpreadBoard(
                    spread = spread,
                    deck = deck,
                    drawnCards = reading.drawnCards,
                    onCardClick = { card, _ -> onCardClick(card.id) },
                    // The cards are the header here; the words below are what this page is for.
                    maxHeight = viewport * 0.62f,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    state.notice?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val shown = state.draft?.takeIf { it.isNotBlank() } ?: reading.interpretation?.takeIf { it.isNotBlank() && !state.isInterpreting }
                    shown?.let { MarkdownText(text = it) }
                    if (state.isInterpreting) {
                        WritingIndicator(status = state.status ?: "Writing", progress = state.progress, modifier = Modifier.padding(top = 8.dp))
                    }
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    val hasReading = !reading.interpretation.isNullOrBlank()
                    when {
                        state.isInterpreting -> FilledTonalButton(onClick = viewModel::stopInterpretation, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                        hasReading -> FilledTonalButton(onClick = viewModel::requestInterpretation, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Interpret again")
                        }
                        else -> Button(onClick = viewModel::requestInterpretation, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Interpret")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Your notes") },
                    supportingText = { Text("Saved as you type") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    minLines = 4,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reading?") },
            text = { Text("The cards, the reading and your notes are removed from the journal. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteReading(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
