package com.arcana.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.core.ui.util.formatBytes
import com.arcana.service.ai.cloud.ClaudeModels
import com.arcana.service.ai.local.ModelSpec
import com.arcana.service.ai.local.ModelStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageDecks: () -> Unit,
    onBackupRestore: () -> Unit,
    /** Opens the list of recent changes. Null in a build that carries none. */
    onWhatsNew: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance = state.appearance
    val ai = state.ai
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<ModelSpec?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message, withDismissAction = true)
        viewModel.dismissMessage()
    }

    val pickModelFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startDownload: (ModelSpec) -> Unit = { spec ->
        // The download shows its progress in a notification, which Android 13 and later ask about.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.install(spec)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (appearance == null || ai == null) {
            Box(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionLabel("Appearance") }
            item { ThemeModeSelector(mode = appearance.themeMode, onChange = viewModel::setThemeMode) }
            items(state.themes, key = { "theme-" + it.id }) { theme ->
                ThemeRow(preset = theme, selected = appearance.themeId == theme.id, onSelect = { viewModel.setTheme(theme.id) })
            }

            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item { SectionLabel("Card deck") }
            items(state.decks, key = { "deck-" + it.id }) { deck ->
                DeckRow(deck = deck, selected = appearance.deckArtId == deck.id, onSelect = { viewModel.setDeck(deck.id) })
            }
            item {
                LinkCard(
                    title = "Your own decks",
                    hint = "Import card images from a folder or ZIP, or set them one card at a time.",
                    onClick = onManageDecks,
                )
            }

            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item { SectionLabel("Who writes the reading") }
            item {
                ChoiceRow(
                    title = "The cards' own meanings",
                    hint = "Built in. Each card's written meaning, position by position.",
                    selected = ai.backendType == AiBackendType.RULE_BASED,
                    onSelect = { viewModel.setBackend(AiBackendType.RULE_BASED) },
                )
            }
            item {
                ChoiceRow(
                    title = "A model on this phone",
                    hint = "Private, and works with no connection once it is downloaded.",
                    selected = ai.backendType == AiBackendType.LOCAL_LLM,
                    onSelect = { viewModel.setBackend(AiBackendType.LOCAL_LLM) },
                )
            }
            if (!state.localSupported) {
                item {
                    Text(
                        "This phone cannot run a model: it needs a 64-bit ARM system.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "Times are for a three-card reading on a mid-range phone from 2020. Newer phones are quicker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                items(state.models, key = { "model-" + it.spec.id }) { row ->
                    ModelCard(
                        row = row,
                        onUse = { viewModel.useModel(row.spec) },
                        onInstall = { startDownload(row.spec) },
                        onPause = { viewModel.pause(row.spec) },
                        onRemove = { removing = row.spec },
                        // Part of a download is not worth a question. A whole model is.
                        onDiscard = { viewModel.remove(row.spec) },
                    )
                }
                item {
                    TextButton(
                        onClick = { pickModelFile.launch(arrayOf("*/*")) },
                        enabled = !state.importing,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(if (state.importing) "Copying the file" else "Use a model file I already have (.gguf)")
                    }
                }
            }
            item {
                ChoiceRow(
                    title = "Claude",
                    hint = "Anthropic's hosted model, with your own API key. Needs a connection; the cards and your question are sent to Anthropic.",
                    selected = ai.backendType == AiBackendType.CLAUDE_API,
                    onSelect = { viewModel.setBackend(AiBackendType.CLAUDE_API) },
                )
            }
            if (ai.backendType == AiBackendType.CLAUDE_API) {
                item { ClaudeSettings(key = ai.claudeApiKey, model = state.claudeModel, onSaveKey = viewModel::saveApiKey, onModel = viewModel::setClaudeModel) }
            }

            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item { SectionLabel("Backup") }
            item {
                LinkCard(
                    title = "Back up and restore",
                    hint = "Save your readings and custom spreads to a file, or bring them back from one.",
                    onClick = onBackupRestore,
                )
            }

            item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
            item { SectionLabel("About") }
            if (onWhatsNew != null) {
                item { LinkCard(title = "What's new", hint = "The latest changes in this version.", onClick = onWhatsNew) }
            }
            item {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                Text(
                    "Arcana $version. Free software under the GPL 3.0. The card meanings are written for this app; the Rider-Waite-Smith art is in the public domain. " +
                        "Models run with llama.cpp (MIT).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    removing?.let { spec ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${spec.title}?") },
            text = { Text("This frees ${formatBytes(spec.bytes)}. Getting it back means downloading it again.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(spec)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val modes = listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                selected = mode == m,
                onClick = { onChange(m) },
            ) { Text(label) }
        }
    }
}

@Composable
private fun selectableColors(selected: Boolean) = CardDefaults.cardColors(
    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun ThemeRow(preset: ThemePreset, selected: Boolean, onSelect: () -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(), colors = selectableColors(selected)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!preset.supportsDynamic) {
                ColorSwatch(preset.seedHex)
                ColorSwatch(preset.secondaryHex)
                ColorSwatch(preset.tertiaryHex)
            }
            Column(modifier = Modifier.weight(1f).padding(start = if (preset.supportsDynamic) 0.dp else 12.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun ColorSwatch(hex: String) {
    val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(20.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
private fun DeckRow(deck: DeckArt, selected: Boolean, onSelect: () -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(), colors = selectableColors(selected)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleSmall)
                Text("${deck.artist}${deck.year?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelMedium)
                if (deck.description.isNotBlank()) Text(deck.description, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, hint: String, selected: Boolean, onSelect: () -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(), colors = selectableColors(selected)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun LinkCard(title: String, hint: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One model. It sits a step in from the choice above it, and carries its own download state and
 * buttons: picking a model and having it on the phone are separate things.
 */
@Composable
private fun ModelCard(
    row: ModelRow,
    onUse: () -> Unit,
    onInstall: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit,
    onDiscard: () -> Unit,
) {
    val spec = row.spec
    val state = row.state
    Card(
        onClick = { if (state is ModelStore.State.Installed) onUse() },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
        colors = selectableColors(row.inUse),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${spec.title} · ${formatBytes(spec.bytes)}", style = MaterialTheme.typography.titleSmall)
                    Text(spec.summary, style = MaterialTheme.typography.bodySmall)
                    Text(spec.credit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                }
                if (state is ModelStore.State.Installed) RadioButton(selected = row.inUse, onClick = onUse)
            }
            when (state) {
                is ModelStore.State.Downloading -> {
                    Text(
                        "${formatBytes(state.doneBytes)} of ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                is ModelStore.State.Verifying -> {
                    Text("Checking the file", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                is ModelStore.State.Paused -> Text(
                    "Paused at ${formatBytes(state.doneBytes)} of ${formatBytes(state.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is ModelStore.State.Failed -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Unit
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is ModelStore.State.Missing -> Button(onClick = onInstall) { Text("Download") }
                    is ModelStore.State.Downloading -> OutlinedButton(onClick = onPause) { Text("Pause") }
                    is ModelStore.State.Paused -> {
                        Button(onClick = onInstall) { Text("Resume") }
                        OutlinedButton(onClick = onDiscard) { Text("Discard") }
                    }
                    is ModelStore.State.Failed -> {
                        Button(onClick = onInstall) { Text("Try again") }
                        OutlinedButton(onClick = onDiscard) { Text("Discard") }
                    }
                    is ModelStore.State.Installed -> OutlinedButton(onClick = onRemove) { Text("Remove") }
                    is ModelStore.State.Verifying -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaudeSettings(key: String, model: String, onSaveKey: (String) -> Unit, onModel: (String) -> Unit) {
    var draft by remember(key) { mutableStateOf(key) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Anthropic API key") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The key stays on this phone and is sent only to api.anthropic.com.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Button(onClick = { onSaveKey(draft) }, enabled = draft.trim() != key) {
            Text(if (draft.trim() == key && key.isNotEmpty()) "Key saved" else "Save key")
        }
        Text("Model", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClaudeModels.offered.forEach { (id, name) ->
                FilterChip(selected = model == id, onClick = { onModel(id) }, label = { Text(name) })
            }
        }
    }
}
