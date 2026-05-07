package com.arcana.feature.spreads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.ui.components.CardSparkleEmitter
import com.arcana.core.ui.components.MarkdownText
import com.arcana.core.ui.components.ShuffleAnimation
import com.arcana.core.ui.components.SpreadBoard
import com.arcana.service.ai.InterpretationTone
import com.arcana.service.ai.local.ModelInstaller
import com.arcana.service.ai.local.ModelManifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingFlowScreen(
    onBack: () -> Unit,
    onCardClick: (cardId: String) -> Unit,
    onSaved: (readingId: String) -> Unit,
    viewModel: ReadingFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
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

    // When the install transitions Downloading → Installed (i.e. the user's
    // first-tap install just finished while they were on this screen), pop a
    // snackbar so they know they can tap Interpret again to use the offline
    // model. Track previous state in a remember so we only fire on the actual
    // transition, not every recomposition while in Installed.
    var previousInstallState by remember {
        mutableStateOf<ModelInstaller.State>(installState)
    }
    val readyMessage = stringResource(R.string.spreads_install_ready)
    LaunchedEffect(installState) {
        val justFinished = previousInstallState is ModelInstaller.State.Downloading &&
            installState is ModelInstaller.State.Installed
        if (justFinished) {
            snackbarHostState.showSnackbar(readyMessage, withDismissAction = true)
        }
        previousInstallState = installState
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
        if (state.spreadNotFound) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This spread no longer exists.\nIt may have been deleted from Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        if (spread == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            InstallBanner(
                state = installState,
                onCancel = viewModel::cancelLocalInstall,
                onRetry = viewModel::retryLocalInstall,
            )
            AnimatedContent(
                targetState = state.stage,
                label = "reading-stage",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
        }

        if (state.showFirstTapPrompt) {
            FirstTapInstallDialog(
                options = ModelManifest.ALL,
                onInstall = viewModel::onFirstTapInstall,
                onNotNow = viewModel::onFirstTapNotNow,
                onDismiss = viewModel::onFirstTapDismissed,
            )
        }
    }
}

@Composable
private fun FirstTapInstallDialog(
    options: List<ModelManifest>,
    onInstall: (ModelManifest) -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spreads_first_install_title)) },
        text = {
            Column {
                Text(stringResource(R.string.spreads_first_install_body))
                Spacer(modifier = Modifier.height(12.dp))
                options.forEach { model ->
                    Button(
                        onClick = { onInstall(model) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${model.displayName} · ${formatBytes(model.expectedBytes)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = model.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.spreads_first_install_no))
            }
        },
    )
}

/**
 * Strip-style banner shown above the reading stages while the offline-AI
 * model is downloading or after a download has just failed. Hidden when the
 * installer is in [ModelInstaller.State.NotInstalled] or [ModelInstaller.State.Installed]
 * — the success-transition snackbar handles "ready to use" feedback.
 */
@Composable
private fun InstallBanner(
    state: ModelInstaller.State,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = state is ModelInstaller.State.Downloading ||
            state is ModelInstaller.State.Failed,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state is ModelInstaller.State.Failed) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (state) {
                    is ModelInstaller.State.Downloading -> {
                        val pct = (state.progress * 100).toInt().coerceIn(0, 100)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.spreads_install_progress,
                                    pct,
                                    formatBytes(state.bytesDone),
                                    formatBytes(state.totalBytes),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancel) {
                                Text(stringResource(R.string.spreads_install_cancel))
                            }
                        }
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                    is ModelInstaller.State.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.spreads_install_failed, state.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.spreads_install_retry))
                            }
                        }
                    }
                    else -> Unit // not visible
                }
            }
        }
    }
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
                showLabels = true,
                cardSizeFraction = 0.24f,
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
                state.backendFallbackNotice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                state.interpretationStatus?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Spinner ringed by a continuous burst of sparkles —
                        // re-uses CardSparkleEmitter with a periodic trigger
                        // bump so the loop pulses every ~700ms. Particles fade
                        // over ~650ms so the sparkle ring stays roughly
                        // continuous without piling up.
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            var sparkleKey by remember { mutableIntStateOf(1) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    kotlinx.coroutines.delay(700)
                                    sparkleKey++
                                }
                            }
                            CardSparkleEmitter(
                                triggerKey = sparkleKey,
                                modifier = Modifier.fillMaxSize(),
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = state.interpretation.isNotBlank()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Re-interpret action shows up only once a generation
                        // has completed (or paused) — useful when the small
                        // model produces wonky output and you want another roll.
                        // Promoted to a full-width filled Button so it carries
                        // the same visual weight as Interpret/Save and isn't
                        // hidden in a corner.
                        if (!state.isInterpreting && state.interpretationError == null) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.spreads_reinterpret))
                            }
                        }

                        // Scrollable interpretation body, with an explicit
                        // scrollbar overlay (Compose's default verticalScroll
                        // doesn't show one).
                        val scrollState = rememberScrollState()
                        val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(end = 12.dp),
                            ) {
                                MarkdownText(text = state.interpretation)
                            }
                            if (scrollState.maxValue > 0) {
                                Canvas(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .fillMaxHeight()
                                        .width(6.dp),
                                ) {
                                    val viewport = size.height
                                    val totalContent = viewport + scrollState.maxValue
                                    val thumbHeight = (viewport * viewport / totalContent)
                                        .coerceAtLeast(40f)
                                    val frac = scrollState.value.toFloat() /
                                        scrollState.maxValue.toFloat().coerceAtLeast(1f)
                                    val thumbY = (viewport - thumbHeight) * frac
                                    drawRoundRect(
                                        color = scrollbarColor.copy(alpha = 0.65f),
                                        topLeft = Offset(0f, thumbY),
                                        size = Size(size.width, thumbHeight),
                                        cornerRadius = CornerRadius(size.width / 2f),
                                    )
                                }
                            }
                        }
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
                        Text(stringResource(R.string.spreads_try_again))
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
