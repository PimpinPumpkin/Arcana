package com.arcana.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset
import com.arcana.service.ai.local.ModelInstaller
import com.arcana.service.ai.local.ModelManifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageDecks: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance = state.appearance
    val ai = state.ai

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        if (appearance == null || ai == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SectionLabel(stringResource(R.string.settings_appearance))
            }
            item {
                ThemeModeSelector(
                    mode = appearance.themeMode,
                    onChange = viewModel::setThemeMode,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_dynamic),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = appearance.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.themes, key = { it.id }) { theme ->
                ThemeRow(
                    preset = theme,
                    selected = appearance.themeId == theme.id,
                    onSelect = { viewModel.setTheme(theme.id) },
                )
            }

            item { HorizontalDivider() }
            item { SectionLabel(stringResource(R.string.settings_deck)) }
            items(state.decks, key = { it.id }) { deck ->
                DeckRow(
                    deck = deck,
                    selected = appearance.deckArtId == deck.id,
                    onSelect = { viewModel.setDeck(deck.id) },
                )
            }
            if (onManageDecks != null) {
                item {
                    Card(
                        onClick = onManageDecks,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_manage_decks),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.settings_manage_decks_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "→",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { SectionLabel(stringResource(R.string.settings_ai)) }
            item {
                BackendRow(
                    type = AiBackendType.RULE_BASED,
                    label = stringResource(R.string.settings_ai_rules),
                    selected = ai.backendType == AiBackendType.RULE_BASED,
                    onSelect = { viewModel.setBackend(AiBackendType.RULE_BASED) },
                )
            }
            item {
                BackendRow(
                    type = AiBackendType.LOCAL_LLM,
                    label = stringResource(R.string.settings_ai_local),
                    selected = ai.backendType == AiBackendType.LOCAL_LLM,
                    onSelect = { viewModel.setBackend(AiBackendType.LOCAL_LLM) },
                    sublabel = stringResource(R.string.settings_local_unavailable),
                )
            }
            items(state.availableModels, key = { it.id }) { model ->
                val isActive = state.activeModel.id == model.id
                ModelCard(
                    manifest = model,
                    selected = isActive,
                    // Install controls are only inlined on the selected card
                    // — no point showing them on alternates the user hasn't
                    // picked yet.
                    installState = state.installState.takeIf { isActive },
                    onSelect = { viewModel.selectLocalModel(model.id) },
                    onInstall = viewModel::installLocalModel,
                    onCancel = viewModel::cancelLocalInstall,
                    onRemove = viewModel::uninstallLocalModel,
                )
            }
            item {
                BackendRow(
                    type = AiBackendType.CLAUDE_API,
                    label = stringResource(R.string.settings_ai_cloud),
                    selected = ai.backendType == AiBackendType.CLAUDE_API,
                    onSelect = { viewModel.setBackend(AiBackendType.CLAUDE_API) },
                )
            }
            item {
                ApiKeyEditor(
                    initial = ai.claudeApiKey,
                    onSave = viewModel::saveApiKey,
                )
            }

            item { HorizontalDivider() }
            item { SectionLabel(stringResource(R.string.settings_about)) }
            item {
                Text(
                    stringResource(R.string.settings_about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val modes = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_mode_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_mode_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_mode_dark),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                selected = mode == m,
                onClick = { onChange(m) },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ThemeRow(preset: ThemePreset, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorSwatch(hex = preset.seedHex)
            ColorSwatch(hex = preset.secondaryHex)
            ColorSwatch(hex = preset.tertiaryHex)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun ColorSwatch(hex: String) {
    val color = remember(hex) { parseHex(hex) }
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .size(24.dp)
            .padding(end = 4.dp)
            .background(color, CircleShape)
            .border(1.dp, outline, CircleShape),
    )
}

private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    return Color(r, g, b)
}

@Composable
private fun DeckRow(deck: DeckArt, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deck.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${deck.artist}${deck.year?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    deck.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun BackendRow(
    type: AiBackendType,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    sublabel: String? = null,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (type.requiresNetwork) {
                    Text(
                        "Requires network",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sublabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

/**
 * One card per available local model. When selected, expands to show install
 * status + progress bar + Install/Cancel/Remove/Retry. Non-selected cards stay
 * compact (header only) — picking one is a single tap on the card or radio.
 */
@Composable
private fun ModelCard(
    manifest: ModelManifest,
    selected: Boolean,
    installState: ModelInstaller.State?,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${manifest.displayName} · ${formatBytes(manifest.expectedBytes)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = onSelect)
            }

            if (selected && installState != null) {
                val statusText = when (installState) {
                    is ModelInstaller.State.NotInstalled -> stringResource(
                        R.string.settings_local_status_not_installed,
                        manifest.displayName,
                        formatBytes(manifest.expectedBytes),
                    )
                    is ModelInstaller.State.Downloading -> stringResource(
                        R.string.settings_local_status_downloading,
                        (installState.progress * 100).toInt().coerceIn(0, 100),
                        formatBytes(installState.bytesDone),
                        formatBytes(installState.totalBytes),
                    )
                    is ModelInstaller.State.Installed -> stringResource(
                        R.string.settings_local_status_installed,
                        formatBytes(installState.sizeBytes),
                    )
                    is ModelInstaller.State.Failed -> stringResource(
                        R.string.settings_local_status_failed,
                        installState.message,
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )

                if (installState is ModelInstaller.State.Downloading) {
                    LinearProgressIndicator(
                        progress = { installState.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (installState) {
                        is ModelInstaller.State.NotInstalled -> {
                            Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_local_install))
                            }
                        }
                        is ModelInstaller.State.Downloading -> {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_local_cancel))
                            }
                        }
                        is ModelInstaller.State.Installed -> {
                            OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_local_remove))
                            }
                        }
                        is ModelInstaller.State.Failed -> {
                            Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_local_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unitIdx = 0
    while (value >= 1024.0 && unitIdx < units.size - 1) {
        value /= 1024.0
        unitIdx++
    }
    return "%.1f %s".format(value, units[unitIdx])
}

@Composable
private fun ApiKeyEditor(initial: String, onSave: (String) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.settings_api_key)) },
            placeholder = { Text(stringResource(R.string.settings_api_key_placeholder)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Text(
            stringResource(R.string.settings_api_key_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_api_key_save))
        }
    }
}
