package com.arcana.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.ui.components.MarkdownText
import com.arcana.core.ui.components.SpreadBoard
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    readingId: String,
    onBack: () -> Unit,
    onCardClick: (cardId: String) -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(readingId) { viewModel.load(readingId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.reading?.spreadName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.journal_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteReading(onBack) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.journal_delete))
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
                Text(if (state.isLoading) "Loading…" else "Reading not found")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(reading.timestampEpochMs)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            reading.question?.let {
                Text(
                    text = "“$it”",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                SpreadBoard(
                    spread = spread,
                    deck = deck,
                    drawnCards = reading.drawnCards,
                    onCardClick = { card, _ -> onCardClick(card.id) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            reading.interpretation?.takeIf { it.isNotBlank() }?.let {
                MarkdownText(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            OutlinedTextField(
                value = state.notesDraft,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.journal_notes_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                minLines = 4,
            )
            Button(
                onClick = viewModel::saveNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.journal_save_notes))
            }
        }
    }
}
