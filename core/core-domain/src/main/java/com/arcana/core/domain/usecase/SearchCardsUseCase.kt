package com.arcana.core.domain.usecase

import com.arcana.core.domain.model.Card
import com.arcana.core.domain.repository.CardRepository
import javax.inject.Inject

class SearchCardsUseCase @Inject constructor(
    private val cardRepository: CardRepository,
) {
    suspend operator fun invoke(query: String): List<Card> {
        if (query.isBlank()) return cardRepository.getAllCards()
        return cardRepository.searchCards(query)
    }
}
