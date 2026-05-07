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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.data.repository.BackupRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFrom(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Save your custom spreads + saved journal readings to a JSON file you can keep around or share. Bundled spreads aren't included (they ship with the app); custom deck images aren't included yet (they'd bloat the file — separate export TBD).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ActionCard(
                    title = "Export to a file",
                    description = "Saves a single .json containing every custom spread and every saved reading.",
                    icon = Icons.Default.FileDownload,
                    busy = state.isExporting,
                    onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                )
            }
            item {
                ActionCard(
                    title = "Import from a file",
                    description = "Reads a previously exported .json and merges it into the journal. Spreads / readings with matching IDs are replaced.",
                    icon = Icons.Default.FileUpload,
                    busy = state.isImporting,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )
            }
        }

        state.lastExportTo?.let { uri ->
            AlertDialog(
                onDismissRequest = viewModel::dismissExportResult,
                title = { Text("Backup saved") },
                text = {
                    Text(
                        "Saved to ${uri.lastPathSegment ?: uri}. Keep it somewhere durable — losing it means losing those custom spreads and readings.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissExportResult) { Text("Done") }
                },
            )
        }

        state.importResult?.let { result ->
            when (result) {
                is BackupRepository.ImportResult.Success -> {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissResult,
                        title = { Text("Imported") },
                        text = {
                            Text(
                                "Imported ${result.customSpreads} custom spread${if (result.customSpreads == 1) "" else "s"} and ${result.readings} saved reading${if (result.readings == 1) "" else "s"}.",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissResult) { Text("Done") }
                        },
                    )
                }
                is BackupRepository.ImportResult.Failed -> {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissResult,
                        title = { Text("Import failed") },
                        text = { Text(result.message) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissResult) { Text("OK") }
                        },
                    )
                }
            }
        }

        state.errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Something went wrong") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) { Text("OK") }
                },
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp).fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
