package com.arcana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDetailUiState(
    val card: Card? = null,
    val deck: DeckArt? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardDetailUiState())
    val state: StateFlow<CardDetailUiState> = _state.asStateFlow()

    fun load(cardId: String) {
        viewModelScope.launch {
            val card = cardRepository.getCardById(cardId)
            val appearance = settingsRepository.appearance.first()
            val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == appearance.deckArtId }
                ?: settingsRepository.getAvailableDecks().firstOrNull()
            _state.value = CardDetailUiState(card = card, deck = deck, isLoading = false)
        }
    }
}
