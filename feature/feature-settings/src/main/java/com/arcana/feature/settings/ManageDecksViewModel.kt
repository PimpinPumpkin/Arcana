package com.arcana.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.data.repository.CustomDeckStore
import com.arcana.core.data.repository.DeckImporter
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageDecksUiState(
    val decks: List<DeckArt> = emptyList(),
    val activeDeckId: String? = null,
    val isImporting: Boolean = false,
    val importResult: DeckImporter.Result.Success? = null,
    val importError: String? = null,
    val pendingDelete: DeckArt? = null,
)

@HiltViewModel
class ManageDecksViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deckImporter: DeckImporter,
    private val customDeckStore: CustomDeckStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageDecksUiState())
    val state: StateFlow<ManageDecksUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val decks = settingsRepository.getAvailableDecks()
            val activeId = settingsRepository.appearance.first().deckArtId
            _state.update { it.copy(decks = decks, activeDeckId = activeId) }
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch {
            settingsRepository.setDeckArtId(id)
            _state.update { it.copy(activeDeckId = id) }
        }
    }

    fun importDeck(folderUri: Uri, name: String, artist: String, description: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importError = null, importResult = null) }
            when (val result = deckImporter.importFromFolder(folderUri, name, artist, description)) {
                is DeckImporter.Result.Success -> {
                    val decks = settingsRepository.getAvailableDecks()
                    _state.update {
                        it.copy(
                            decks = decks,
                            isImporting = false,
                            importResult = result,
                        )
                    }
                }
                is DeckImporter.Result.Failed -> {
                    _state.update { it.copy(isImporting = false, importError = result.message) }
                }
            }
        }
    }

    fun dismissImportResult() = _state.update { it.copy(importResult = null, importError = null) }

    fun requestDelete(deck: DeckArt) {
        if (deck.isBundled) return
        _state.update { it.copy(pendingDelete = deck) }
    }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        if (target.isBundled) return
        viewModelScope.launch {
            customDeckStore.delete(target.id)
            // If the deleted deck was active, fall back to the default bundled.
            if (_state.value.activeDeckId == target.id) {
                settingsRepository.setDeckArtId(
                    com.arcana.core.data.repository.DeckArtCatalog.DEFAULT_ID,
                )
            }
            val decks = settingsRepository.getAvailableDecks()
            _state.update {
                it.copy(
                    decks = decks,
                    pendingDelete = null,
                    activeDeckId = if (it.activeDeckId == target.id) {
                        com.arcana.core.data.repository.DeckArtCatalog.DEFAULT_ID
                    } else it.activeDeckId,
                )
            }
        }
    }
}
