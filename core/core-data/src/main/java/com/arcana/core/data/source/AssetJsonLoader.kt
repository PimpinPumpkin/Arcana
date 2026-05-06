package com.arcana.core.data.source

import android.content.Context
import com.arcana.core.common.DispatcherProvider
import com.arcana.core.domain.model.Arcana
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.Element
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.PositionCoords
import com.arcana.core.domain.model.Rank
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadDifficulty
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.model.Suit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetJsonLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadCards(): List<Card> = withContext(dispatchers.io) {
        val raw = context.assets.open(CARDS_ASSET).bufferedReader().use { it.readText() }
        val catalog = json.decodeFromString<CardCatalogDto>(raw)
        catalog.cards.map { it.toDomain() }
    }

    suspend fun loadSpreads(): List<Spread> = withContext(dispatchers.io) {
        val raw = context.assets.open(SPREADS_ASSET).bufferedReader().use { it.readText() }
        val catalog = json.decodeFromString<SpreadCatalogDto>(raw)
        catalog.spreads.map { it.toDomain() }
    }

    private fun CardDto.toDomain(): Card = Card(
        id = id,
        name = name,
        arcana = arcana.toDomain(),
        keywordsUpright = keywordsUpright,
        keywordsReversed = keywordsReversed,
        uprightMeaning = uprightMeaning,
        reversedMeaning = reversedMeaning,
        description = description,
        element = element?.let { runCatching { Element.valueOf(it) }.getOrNull() },
        astrology = astrology,
        numerology = numerology,
        imageRef = imageRef,
    )

    private fun ArcanaDto.toDomain(): Arcana = when (type.lowercase()) {
        "major" -> Arcana.Major(number ?: 0)
        "minor" -> {
            val s = Suit.valueOf(requireNotNull(suit) { "Minor card needs suit" })
            val r = Rank.valueOf(requireNotNull(rank) { "Minor card needs rank" })
            Arcana.Minor(s, r)
        }
        else -> error("Unknown arcana type: $type")
    }

    private fun SpreadDto.toDomain(): Spread = Spread(
        id = id,
        name = name,
        description = description,
        difficulty = SpreadDifficulty.valueOf(difficulty),
        layout = SpreadLayout.valueOf(layout),
        positions = positions.map { it.toDomain() },
    )

    private fun PositionDto.toDomain(): Position = Position(
        index = index,
        label = label,
        meaning = meaning,
        coords = PositionCoords(x = x, y = y, rotationDegrees = rotation),
    )

    companion object {
        private const val CARDS_ASSET = "cards.json"
        private const val SPREADS_ASSET = "spreads.json"
    }
}
