package com.arcana.feature.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.common.DispatcherProvider
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditDeckUiState(
    val deck: DeckArt? = null,
    val cards: List<Card> = emptyList(),
    /** Ids of the cards this deck has an image for. */
    val cardsWithImage: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    /** The card an image is being picked for, while the system picker is open. */
    val cardPickingFor: Card? = null,
    val nameDraft: String = "",
    val artistDraft: String = "",
    val descriptionDraft: String = "",
    val isDirty: Boolean = false,
    val notFound: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class EditDeckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val cardRepository: CardRepository,
    private val customDeckStore: CustomDeckStore,
    private val deckImporter: DeckImporter,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle["deckId"])

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
            val present = withContext(dispatchers.io) {
                cards.filter { customDeckStore.imageFile(deck.id, it.imageRef) != null }.map { it.id }.toSet()
            }
            _state.update {
                it.copy(
                    deck = deck,
                    cards = cards,
                    cardsWithImage = present,
                    isLoading = false,
                    nameDraft = deck.name,
                    artistDraft = deck.artist,
                    descriptionDraft = deck.description,
                )
            }
        }
    }

    fun beginPickFor(card: Card) = _state.update { it.copy(cardPickingFor = card) }

    fun cancelPick() = _state.update { it.copy(cardPickingFor = null) }

    fun onImagePicked(uri: Uri) {
        val current = _state.value
        val card = current.cardPickingFor ?: return
        val deck = current.deck ?: return
        viewModelScope.launch {
            val ok = deckImporter.replaceCardImage(deck.id, card.imageRef, uri)
            _state.update {
                it.copy(
                    cardsWithImage = if (ok) it.cardsWithImage + card.id else it.cardsWithImage,
                    cardPickingFor = null,
                    message = if (ok) null else "That file could not be read as an image.",
                )
            }
        }
    }

    fun deleteCardImage(card: Card) {
        val deck = _state.value.deck ?: return
        viewModelScope.launch {
            deckImporter.deleteCardImage(deck.id, card.imageRef)
            _state.update { it.copy(cardsWithImage = it.cardsWithImage - card.id) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun setName(value: String) = _state.update { it.copy(nameDraft = value, isDirty = true) }
    fun setArtist(value: String) = _state.update { it.copy(artistDraft = value, isDirty = true) }
    fun setDescription(value: String) = _state.update { it.copy(descriptionDraft = value, isDirty = true) }

    fun saveMetadata() {
        val current = _state.value
        val deck = current.deck ?: return
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
