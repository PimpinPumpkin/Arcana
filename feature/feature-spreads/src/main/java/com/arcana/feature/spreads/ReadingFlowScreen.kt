package com.arcana.feature.spreads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.ui.components.MarkdownText
import com.arcana.core.ui.components.ShuffleAnimation
import com.arcana.core.ui.components.SpreadBoard
import com.arcana.service.ai.InterpretationTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingFlowScreen(
    onBack: () -> Unit,
    onCardClick: (cardId: String) -> Unit,
    onSaved: (readingId: String) -> Unit,
    viewModel: ReadingFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spread = state.spread
    val snackbarHostState = remember { SnackbarHostState() }
    val isSaved = state.savedReadingId != null

    val savedMessage = stringResource(R.string.spreads_saved_message)
    val viewLabel = stringResource(R.string.spreads_saved_view)
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
                title = { Text(spread?.name ?: stringResource(R.string.spreads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.spreads_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (spread == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        AnimatedContent(
            targetState = state.stage,
            label = "reading-stage",
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) { stage ->
            when (stage) {
                ReadingStage.QUESTION -> QuestionStage(
                    state = state,
                    onQuestionChange = viewModel::onQuestionChanged,
                    onToggleReversed = viewModel::onToggleReversed,
                    onToneChange = viewModel::onToneChanged,
                    onContinue = viewModel::startShuffle,
                )
                ReadingStage.SHUFFLING -> ShufflingStage()
                ReadingStage.REVEAL -> RevealStage(
                    state = state,
                    isSaved = isSaved,
                    onCardClick = onCardClick,
                    onInterpret = viewModel::onInterpretTapped,
                    onSave = {
                        viewModel.saveCurrentReading()
                    },
                )
                ReadingStage.INTERPRETATION -> InterpretationStage(
                    state = state,
                    isSaved = isSaved,
                    onCardClick = onCardClick,
                    onRetry = viewModel::requestInterpretation,
                    onSave = {
                        viewModel.saveCurrentReading()
                    },
                )
            }
        }

        if (state.showFirstTapPrompt) {
            FirstTapInstallDialog(
                downloadBytes = state.firstTapDownloadBytes,
                onInstall = viewModel::onFirstTapInstall,
                onNotNow = viewModel::onFirstTapNotNow,
                onDismiss = viewModel::onFirstTapDismissed,
            )
        }
    }
}

@Composable
private fun FirstTapInstallDialog(
    downloadBytes: Long,
    onInstall: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sizeLabel = remember(downloadBytes) { formatBytes(downloadBytes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spreads_first_install_title)) },
        text = {
            Text(stringResource(R.string.spreads_first_install_body, sizeLabel))
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.spreads_first_install_yes, sizeLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.spreads_first_install_no))
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIdx = 0
    while (value >= 1024.0 && unitIdx < units.size - 1) {
        value /= 1024.0
        unitIdx++
    }
    return "%.0f %s".format(value, units[unitIdx])
}

@Composable
private fun QuestionStage(
    state: ReadingFlowUiState,
    onQuestionChange: (String) -> Unit,
    onToggleReversed: (Boolean) -> Unit,
    onToneChange: (InterpretationTone) -> Unit,
    onContinue: () -> Unit,
) {
    val spread = state.spread ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(spread.description, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = state.question,
            onValueChange = onQuestionChange,
            label = { Text(stringResource(R.string.spreads_question_label)) },
            placeholder = { Text(stringResource(R.string.spreads_question_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            minLines = 2,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.spreads_allow_reversed), modifier = Modifier.weight(1f))
            Switch(checked = state.allowReversed, onCheckedChange = onToggleReversed)
        }

        Text(
            stringResource(R.string.spreads_tone),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InterpretationTone.entries.forEach { tone ->
                FilterChip(
                    selected = state.tone == tone,
                    onClick = { onToneChange(tone) },
                    label = { Text(tone.displayName, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text(stringResource(R.string.spreads_continue))
        }
    }
}

@Composable
private fun ShufflingStage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShuffleAnimation()
        Text(
            stringResource(R.string.spreads_shuffling),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun RevealStage(
    state: ReadingFlowUiState,
    isSaved: Boolean,
    onCardClick: (String) -> Unit,
    onInterpret: () -> Unit,
    onSave: () -> Unit,
) {
    val spread = state.spread ?: return
    val deck = state.deck ?: return
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SpreadBoard(
                spread = spread,
                deck = deck,
                drawnCards = state.drawn,
                onCardClick = { card, _ -> onCardClick(card.id) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSave,
                enabled = !isSaved,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (isSaved) R.string.spreads_save_done else R.string.spreads_save))
            }
            Button(onClick = onInterpret, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.spreads_interpret))
            }
        }
    }
}

@Composable
private fun InterpretationStage(
    state: ReadingFlowUiState,
    isSaved: Boolean,
    onCardClick: (String) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val spread = state.spread ?: return
    val deck = state.deck ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            SpreadBoard(
                spread = spread,
                deck = deck,
                drawnCards = state.drawn,
                onCardClick = { card, _ -> onCardClick(card.id) },
                showLabels = false,
                cardSizeFraction = 0.16f,
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                state.interpretationStatus?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = state.interpretation.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MarkdownText(text = state.interpretation)
                    }
                }
                state.interpretationError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Try again")
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = !isSaved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (isSaved) R.string.spreads_save_done else R.string.spreads_save))
            }
        }
    }
}
