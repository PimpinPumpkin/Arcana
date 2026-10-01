package com.arcana.feature.spreads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.ui.components.MarkdownText
import com.arcana.core.ui.components.ShuffleAnimation
import com.arcana.core.ui.components.SpreadBoard
import com.arcana.core.ui.components.WritingIndicator
import com.arcana.core.ui.util.formatBytes
import com.arcana.service.ai.InterpretationTone
import com.arcana.service.ai.local.ModelSpec
import com.arcana.service.ai.local.ModelStore
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingFlowScreen(
    onBack: () -> Unit,
    onCardClick: (cardId: String) -> Unit,
    onSaved: (readingId: String) -> Unit,
    viewModel: ReadingFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val spread = state.spread
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.savedReadingId) {
        val id = state.savedReadingId ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Reading saved", actionLabel = "View", withDismissAction = true)
        if (result == SnackbarResult.ActionPerformed) onSaved(id)
    }

    // Tell them when a model they started downloading from here is ready to use.
    var wasDownloading by remember { mutableStateOf(false) }
    val downloading = modelStates.values.any { it is ModelStore.State.Downloading || it is ModelStore.State.Verifying }
    LaunchedEffect(downloading) {
        if (wasDownloading && !downloading && modelStates.values.any { it is ModelStore.State.Installed }) {
            snackbar.showSnackbar("The reading model is ready. Tap Interpret again to use it.", withDismissAction = true)
        }
        wasDownloading = downloading
    }

    // A reading can take minutes on a slow phone, and a sleeping phone stops writing it.
    val view = LocalView.current
    DisposableEffect(state.isInterpreting) {
        view.keepScreenOn = state.isInterpreting
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spread?.name ?: "Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.stage == ReadingStage.READING && spread != null) {
                ReadingActions(
                    state = state,
                    onSave = viewModel::saveCurrentReading,
                    onInterpret = viewModel::onInterpretTapped,
                    onAgain = viewModel::requestInterpretation,
                    onStop = viewModel::stopInterpretation,
                )
            }
        },
    ) { padding ->
        when {
            state.spreadNotFound -> Centered(padding, "This spread no longer exists.")
            spread == null -> Centered(padding, "")
            else -> AnimatedContent(
                targetState = state.stage,
                label = "reading-stage",
                modifier = Modifier.padding(padding).fillMaxSize(),
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
                    ReadingStage.READING -> ReadingPage(
                        state = state,
                        downloads = viewModel.installable.mapNotNull { spec -> modelStates[spec.id]?.let { spec to it } },
                        onCardClick = onCardClick,
                        onRetry = viewModel::requestInterpretation,
                        onInstall = viewModel::install,
                        onPause = viewModel::pauseInstall,
                    )
                }
            }
        }

        if (state.offerInstall) {
            val ask = notificationAsk()
            InstallOfferDialog(
                options = viewModel.installable,
                onInstall = {
                    ask()
                    viewModel.onInstallChosen(it)
                },
                onNotNow = viewModel::onInstallDeclined,
                onDismiss = viewModel::onInstallDismissed,
            )
        }
    }
}

/** Asks for the notification permission a download's progress needs, where Android has one. */
@Composable
private fun notificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun Centered(padding: androidx.compose.foundation.layout.PaddingValues, text: String) {
    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InstallOfferDialog(
    options: List<ModelSpec>,
    onInstall: (ModelSpec) -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Have readings written for you?") },
        text = {
            Column {
                Text("A small language model can write the reading on this phone. It is a one-time download, works with no connection after that, and nothing you ask ever leaves the phone. Times are for three cards on a mid-range phone from 2020.")
                Spacer(Modifier.height(12.dp))
                options.forEach { model ->
                    Card(
                        onClick = { onInstall(model) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${model.title} · ${formatBytes(model.bytes)}", style = MaterialTheme.typography.titleSmall)
                            Text(model.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(
                    "You can change or remove it in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
            label = { Text("Your question (optional)") },
            placeholder = { Text("What weighs on your mind?") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Allow reversed cards", modifier = Modifier.weight(1f))
            Switch(checked = state.allowReversed, onCheckedChange = onToggleReversed)
        }
        Text("Tone of the reading", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        // Wraps onto a second line on a narrow phone instead of running off the edge.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InterpretationTone.entries.forEach { tone ->
                FilterChip(
                    selected = state.tone == tone,
                    onClick = { onToneChange(tone) },
                    label = { Text(tone.displayName) },
                )
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
            Text("Shuffle the deck")
        }
    }
}

@Composable
private fun ShufflingStage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShuffleAnimation()
        Text(
            "Shuffling",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/**
 * The cards, and under them the reading once one is asked for. It is one page that scrolls, so
 * the cards can be as large as the screen is wide and the text as long as it needs to be; the
 * buttons live in the bar below and are always in reach.
 */
@Composable
private fun ReadingPage(
    state: ReadingFlowUiState,
    downloads: List<Pair<ModelSpec, ModelStore.State>>,
    onCardClick: (String) -> Unit,
    onRetry: () -> Unit,
    onInstall: (ModelSpec) -> Unit,
    onPause: (ModelSpec) -> Unit,
) {
    val spread = state.spread ?: return
    val deck = state.deck ?: return
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hasReading = state.isInterpreting || state.interpretation.isNotBlank() || state.error != null

    Column(modifier = Modifier.fillMaxSize()) {
        // A download in progress stays in sight above the page, however far the page is scrolled.
        downloads.forEach { (spec, s) -> DownloadBanner(spec, s, onRetry = { onInstall(spec) }, onPause = { onPause(spec) }) }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            val viewportPx = with(density) { viewport.toPx() }
            var boardHeight by remember { mutableIntStateOf(0) }
            // With no reading yet the cards sit in the middle of the screen; they move up to make
            // room when one starts.
            val slack = (viewport - with(density) { boardHeight.toDp() }) / 2
            val topSpace by animateDpAsState(if (hasReading) 0.dp else slack.coerceAtLeast(0.dp), label = "board-top")

            // A model writes faster than anyone reads, so the page does not chase the text. When a
            // reading is asked for it moves once, far enough to put the first lines in view, and
            // holds room below them so that move never has to wait for the text to arrive.
            var holdRoom by rememberSaveable { mutableStateOf(false) }
            var placedRun by rememberSaveable { mutableIntStateOf(state.run) }
            LaunchedEffect(state.run) {
                if (state.run == placedRun) return@LaunchedEffect
                placedRun = state.run
                val textTop = snapshotFlow { boardHeight }.first { it > 0 }
                if (textTop - scroll.value > viewportPx * TEXT_IN_VIEW) {
                    holdRoom = true
                    withFrameNanos { } // let the room be laid out before scrolling into it
                    scroll.animateScrollTo((textTop - viewportPx * TEXT_STARTS_AT).toInt().coerceAtLeast(0))
                }
            }

            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                Spacer(Modifier.height(topSpace))
                SpreadBoard(
                    spread = spread,
                    deck = deck,
                    drawnCards = state.drawn,
                    onCardClick = { card, _ -> onCardClick(card.id) },
                    // Up to a third more than the screen: big spreads scroll rather than shrink.
                    maxHeight = viewport * 1.35f,
                    preferredHeight = viewport,
                    modifier = Modifier.padding(horizontal = 8.dp).onSizeChanged { boardHeight = it.height },
                )
                if (hasReading) {
                    ReadingText(
                        state = state,
                        onRetry = onRetry,
                        modifier = Modifier.heightIn(min = if (holdRoom) viewport * (1f - TEXT_STARTS_AT) else 0.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// Where on the screen a reading's first line may already be for the page to stay put, and where
// the page puts it otherwise, as fractions of the screen's height from the top.
private const val TEXT_IN_VIEW = 0.55f
private const val TEXT_STARTS_AT = 0.3f

@Composable
private fun ReadingText(state: ReadingFlowUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        state.notice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (state.interpretation.isNotBlank()) {
            MarkdownText(text = state.interpretation)
        }
        if (state.isInterpreting) {
            WritingIndicator(status = state.status ?: "Writing", progress = state.progress, modifier = Modifier.padding(top = 8.dp))
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
        }
    }
}

@Composable
private fun ReadingActions(
    state: ReadingFlowUiState,
    onSave: () -> Unit,
    onInterpret: () -> Unit,
    onAgain: () -> Unit,
    onStop: () -> Unit,
) {
    val saved = state.savedReadingId != null
    Surface(tonalElevation = 3.dp) {
        Column {
            // The text being written is often below the fold; this is the sign that more is coming.
            if (state.isInterpreting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onSave, enabled = !saved, modifier = Modifier.weight(1f)) {
                    Text(if (saved) "Saved" else "Save")
                }
                when {
                    state.isInterpreting -> FilledTonalButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                    state.interpretation.isBlank() -> Button(onClick = onInterpret, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Interpret")
                    }
                    else -> FilledTonalButton(onClick = onAgain, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Again")
                    }
                }
            }
        }
    }
}

/** Shown while a model is on its way, so it is clear when Interpret will start using it. */
@Composable
private fun DownloadBanner(spec: ModelSpec, state: ModelStore.State, onRetry: () -> Unit, onPause: () -> Unit) {
    val visible = state is ModelStore.State.Downloading || state is ModelStore.State.Verifying || state is ModelStore.State.Failed
    AnimatedVisibility(visible = visible) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state is ModelStore.State.Failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (state) {
                            is ModelStore.State.Downloading -> "Getting the ${spec.title} model: ${formatBytes(state.doneBytes)} of ${formatBytes(state.totalBytes)}"
                            is ModelStore.State.Verifying -> "Checking the ${spec.title} model"
                            is ModelStore.State.Failed -> "${spec.title} model: ${state.message}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    when (state) {
                        is ModelStore.State.Downloading -> TextButton(onClick = onPause) { Text("Pause") }
                        is ModelStore.State.Failed -> TextButton(onClick = onRetry) { Text("Retry") }
                        else -> Unit
                    }
                }
                if (state is ModelStore.State.Downloading) {
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                } else if (state is ModelStore.State.Verifying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}
