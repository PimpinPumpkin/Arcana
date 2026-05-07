package com.arcana.feature.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.data.repository.CustomDeckStore
import com.arcana.core.data.repository.DeckImporter
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditDeckUiState(
    val deck: DeckArt? = null,
    val cards: List<Card> = emptyList(),
    /** Cards whose imageRef has a real file in this deck's folder. Drives the
     *  per-cell "has image / fallback" indicator. */
    val cardsWithImage: Set<String> = emptySet(),
    /** Bumped on every successful image swap so AsyncImage can bust its cache. */
    val imageVersion: Int = 0,
    val isLoading: Boolean = true,
    /** Card currently waiting for the user to pick an image (drives the launcher). */
    val cardPickingFor: Card? = null,
    val nameDraft: String = "",
    val artistDraft: String = "",
    val descriptionDraft: String = "",
    val isDirty: Boolean = false,
    val notFound: Boolean = false,
)

@HiltViewModel
class EditDeckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val cardRepository: CardRepository,
    private val customDeckStore: CustomDeckStore,
    private val deckImporter: DeckImporter,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"]) {
        "deckId required as nav arg"
    }

    private val _state = MutableStateFlow(EditDeckUiState(isLoading = true))
    val state: StateFlow<EditDeckUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == deckId }
            if (deck == null || deck.isBundled) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val cards = cardRepository.getAllCards()
            val refsOnDisk = cards
                .filter { customDeckStore.cardImageFile(deck.id, it.imageRef).exists() }
                .map { it.id }
                .toSet()
            _state.update {
                it.copy(
                    deck = deck,
                    cards = cards,
                    cardsWithImage = refsOnDisk,
                    isLoading = false,
                    nameDraft = deck.name,
                    artistDraft = deck.artist,
                    descriptionDraft = deck.description,
                )
            }
        }
    }

    fun beginPickFor(card: Card) =
        _state.update { it.copy(cardPickingFor = card) }

    fun cancelPick() = _state.update { it.copy(cardPickingFor = null) }

    fun onImagePicked(uri: Uri) {
        val current = _state.value
        val card = current.cardPickingFor ?: return
        val deck = current.deck ?: return
        viewModelScope.launch {
            val ok = deckImporter.replaceCardImage(deck.id, card.imageRef, uri)
            if (ok) {
                _state.update {
                    it.copy(
                        cardsWithImage = it.cardsWithImage + card.id,
                        imageVersion = it.imageVersion + 1,
                        cardPickingFor = null,
                    )
                }
            } else {
                _state.update { it.copy(cardPickingFor = null) }
            }
        }
    }

    fun deleteCardImage(card: Card) {
        val deck = _state.value.deck ?: return
        viewModelScope.launch {
            deckImporter.deleteCardImage(deck.id, card.imageRef)
            _state.update {
                it.copy(
                    cardsWithImage = it.cardsWithImage - card.id,
                    imageVersion = it.imageVersion + 1,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(nameDraft = value, isDirty = true) }
    fun setArtist(value: String) = _state.update { it.copy(artistDraft = value, isDirty = true) }
    fun setDescription(value: String) = _state.update { it.copy(descriptionDraft = value, isDirty = true) }

    fun saveMetadata() {
        val deck = _state.value.deck ?: return
        val current = _state.value
        viewModelScope.launch {
            val updated = deck.copy(
                name = current.nameDraft.ifBlank { deck.name },
                artist = current.artistDraft.ifBlank { deck.artist },
                description = current.descriptionDraft,
            )
            customDeckStore.writeManifest(updated)
            _state.update { it.copy(deck = updated, isDirty = false) }
        }
    }
}
