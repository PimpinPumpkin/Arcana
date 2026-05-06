package com.arcana.feature.journal

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Reading
import com.arcana.core.domain.model.ReadingKind
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onReadingClick: (readingId: String) -> Unit,
    onLogPhysicalReading: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.journal_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onLogPhysicalReading,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log a reading") },
            )
        },
    ) { padding ->
        if (state.readings.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.journal_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.journal_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Or tap the button below to log a physical reading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.readings, key = { it.id }) { reading ->
                JournalEntry(
                    reading = reading,
                    onClick = { onReadingClick(reading.id) },
                    onDelete = { viewModel.deleteReading(reading.id) },
                )
            }
        }
    }
}

@Composable
private fun JournalEntry(
    reading: Reading,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reading.spreadName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    KindBadge(kind = reading.kind)
                }
                Text(
                    formatDate(reading.timestampEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reading.question?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                    )
                }
                Text(
                    "${reading.drawnCards.size} cards",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.journal_delete))
            }
        }
    }
}

@Composable
private fun KindBadge(kind: ReadingKind) {
    val (icon, label, container) = when (kind) {
        ReadingKind.DIGITAL -> Triple(Icons.Default.AutoAwesome, "Digital", MaterialTheme.colorScheme.primaryContainer)
        ReadingKind.PHYSICAL -> Triple(Icons.Default.Edit, "Physical", MaterialTheme.colorScheme.tertiaryContainer)
    }
    val onContainer = when (kind) {
        ReadingKind.DIGITAL -> MaterialTheme.colorScheme.onPrimaryContainer
        ReadingKind.PHYSICAL -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.padding(end = 4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = onContainer)
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
