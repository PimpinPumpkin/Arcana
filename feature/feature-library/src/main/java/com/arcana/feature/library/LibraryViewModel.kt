package com.arcana.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcana.core.domain.model.Arcana
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Suit
import com.arcana.core.domain.repository.CardRepository
import com.arcana.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardFilter { ALL, MAJOR, WANDS, CUPS, SWORDS, PENTACLES }

data class LibraryUiState(
    val cards: List<Card> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: CardFilter = CardFilter.ALL,
    val deck: DeckArt? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CardFilter.ALL)
    private val allCards = MutableStateFlow<List<Card>>(emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        allCards,
        query,
        filter,
        settingsRepository.appearance,
    ) { cards, q, f, appearance ->
        val deck = settingsRepository.getAvailableDecks().firstOrNull { it.id == appearance.deckArtId }
            ?: settingsRepository.getAvailableDecks().firstOrNull()
        LibraryUiState(
            cards = applyFilters(cards, q, f),
            isLoading = cards.isEmpty(),
            query = q,
            filter = f,
            deck = deck,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            allCards.value = cardRepository.getAllCards()
        }
    }

    fun onQueryChanged(q: String) {
        query.value = q
    }

    fun onFilterChanged(f: CardFilter) {
        filter.value = f
    }

    private fun applyFilters(cards: List<Card>, q: String, f: CardFilter): List<Card> {
        val byFilter = when (f) {
            CardFilter.ALL -> cards
            CardFilter.MAJOR -> cards.filter { it.arcana is Arcana.Major }
            CardFilter.WANDS -> cards.filter { (it.arcana as? Arcana.Minor)?.suit == Suit.WANDS }
            CardFilter.CUPS -> cards.filter { (it.arcana as? Arcana.Minor)?.suit == Suit.CUPS }
            CardFilter.SWORDS -> cards.filter { (it.arcana as? Arcana.Minor)?.suit == Suit.SWORDS }
            CardFilter.PENTACLES -> cards.filter { (it.arcana as? Arcana.Minor)?.suit == Suit.PENTACLES }
        }
        return if (q.isBlank()) byFilter else byFilter.filter { it.matchesQuery(q) }
    }
}
