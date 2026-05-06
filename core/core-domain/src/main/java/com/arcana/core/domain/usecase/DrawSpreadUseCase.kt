package com.arcana.core.domain.usecase

import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.repository.CardRepository
import javax.inject.Inject
import kotlin.random.Random

class DrawSpreadUseCase @Inject constructor(
    private val cardRepository: CardRepository,
) {
    private val random: Random = Random.Default
    suspend operator fun invoke(
        spread: Spread,
        allowReversed: Boolean = true,
    ): List<DrawnCard> {
        val deck: List<Card> = cardRepository.getAllCards().shuffled(random)
        require(deck.size >= spread.cardCount) {
            "Deck size ${deck.size} smaller than spread size ${spread.cardCount}"
        }
        return spread.positions.mapIndexed { idx, position ->
            DrawnCard(
                card = deck[idx],
                orientation = if (allowReversed && random.nextBoolean()) Orientation.REVERSED else Orientation.UPRIGHT,
                positionIndex = position.index,
            )
        }
    }
}
