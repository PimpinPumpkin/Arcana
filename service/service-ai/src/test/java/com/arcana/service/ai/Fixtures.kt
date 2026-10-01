package com.arcana.service.ai

import com.arcana.core.domain.model.Arcana
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Element
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.PositionCoords
import com.arcana.core.domain.model.Rank
import com.arcana.core.domain.model.Spread
import com.arcana.core.domain.model.SpreadDifficulty
import com.arcana.core.domain.model.SpreadLayout
import com.arcana.core.domain.model.Suit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** The app's real cards and spreads, read from the JSON the app ships. */
internal object Fixtures {
    private val assets: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "core/core-data/src/main/assets") }
        .first { it.isDirectory }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.texts(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content }

    val cards: Map<String, Card> = Json.parseToJsonElement(File(assets, "cards.json").readText())
        .jsonObject.getValue("cards").jsonArray.map { it.jsonObject }.associate { c ->
            val a = c.getValue("arcana").jsonObject
            val arcana = if (a.text("type") == "major") Arcana.Major(a.getValue("number").jsonPrimitive.int)
            else Arcana.Minor(Suit.valueOf(a.text("suit")), Rank.valueOf(a.text("rank")))
            c.text("id") to Card(
                id = c.text("id"),
                name = c.text("name"),
                arcana = arcana,
                keywordsUpright = c.texts("keywordsUpright"),
                keywordsReversed = c.texts("keywordsReversed"),
                uprightMeaning = c.text("uprightMeaning"),
                reversedMeaning = c.text("reversedMeaning"),
                description = c.text("description"),
                element = c["element"]?.jsonPrimitive?.content?.let { runCatching { Element.valueOf(it) }.getOrNull() },
                astrology = null,
                numerology = null,
                imageRef = c.text("imageRef"),
            )
        }

    val spreads: Map<String, Spread> = Json.parseToJsonElement(File(assets, "spreads.json").readText())
        .jsonObject.getValue("spreads").jsonArray.map { it.jsonObject }.associate { s ->
            s.text("id") to Spread(
                id = s.text("id"),
                name = s.text("name"),
                description = s.text("description"),
                positions = s.getValue("positions").jsonArray.map { it.jsonObject }.map { p ->
                    Position(
                        index = p.getValue("index").jsonPrimitive.int,
                        label = p.text("label"),
                        meaning = p.text("meaning"),
                        coords = PositionCoords(p.getValue("x").jsonPrimitive.float, p.getValue("y").jsonPrimitive.float),
                    )
                },
                layout = SpreadLayout.valueOf(s.text("layout")),
                difficulty = SpreadDifficulty.valueOf(s.text("difficulty")),
            )
        }

    /** A reading of [spreadId] with [cardIds] in position order. An id ending in `!` is reversed. */
    fun reading(
        spreadId: String,
        vararg cardIds: String,
        question: String? = null,
        tone: InterpretationTone = InterpretationTone.GROUNDED,
    ): InterpretationRequest {
        val spread = spreads.getValue(spreadId)
        require(cardIds.size == spread.positions.size) { "$spreadId takes ${spread.positions.size} cards" }
        return InterpretationRequest(
            spread = spread,
            drawnCards = cardIds.mapIndexed { i, id ->
                DrawnCard(
                    card = cards.getValue(id.removeSuffix("!")),
                    orientation = if (id.endsWith("!")) Orientation.REVERSED else Orientation.UPRIGHT,
                    positionIndex = spread.positions[i].index,
                )
            },
            question = question,
            tone = tone,
        )
    }
}
