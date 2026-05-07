package com.arcana.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastExportTo: Uri? = null,
    val importResult: BackupRepository.ImportResult? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val appVersionName: String =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: "unknown"

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }
            try {
                val jsonString = backupRepository.exportJson(appVersionName)
                withContext(dispatchers.io) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("Couldn't open file for writing")
                }
                _state.update { it.copy(isExporting = false, lastExportTo = uri) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = "Export failed: ${e.message ?: e::class.java.simpleName}",
                    )
                }
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, errorMessage = null, importResult = null) }
            try {
                val jsonString = withContext(dispatchers.io) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw java.io.IOException("Couldn't open file for reading")
                }
                val result = backupRepository.importJson(jsonString)
                _state.update { it.copy(isImporting = false, importResult = result) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Import failed: ${e.message ?: e::class.java.simpleName}",
                    )
                }
            }
        }
    }

    fun dismissResult() = _state.update { it.copy(importResult = null) }
    fun dismissError() = _state.update { it.copy(errorMessage = null) }
    fun dismissExportResult() = _state.update { it.copy(lastExportTo = null) }

    /** Default filename for the SAF CreateDocument picker. */
    fun suggestedFileName(): String {
        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "arcana-backup-$ts.json"
    }
}
