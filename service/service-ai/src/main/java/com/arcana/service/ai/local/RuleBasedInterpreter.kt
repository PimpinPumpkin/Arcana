package com.arcana.service.ai.local

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.Orientation
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.ReadingScript
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a reading together from the cards' written meanings, position by position. No model, no
 * download, no network: it is what the app does out of the box, and what it falls back on.
 */
@Singleton
class RuleBasedInterpreter @Inject constructor() : TarotInterpreter {
    override val type = AiBackendType.RULE_BASED
    override val isAvailable: Boolean = true

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = flow {
        val drawn = request.drawnCards.sortedBy { it.positionIndex }
        val text = buildString {
            drawn.forEach { card ->
                val position = request.spread.positions.firstOrNull { it.index == card.positionIndex }
                val reversed = card.orientation == Orientation.REVERSED
                appendLine(ReadingScript.heading(card, position, numbered = drawn.size > 1))
                position?.meaning?.takeIf { it.isNotBlank() }?.let { appendLine("_${it}_") }
                appendLine()
                appendLine(if (reversed) card.card.reversedMeaning else card.card.uprightMeaning)
                appendLine()
            }
            appendLine("---")
            appendLine()
            append("_These are the cards' own meanings, one position at a time. The thread between them is yours to find._")
        }
        // Handed over in small pieces so it appears the way a written reading would.
        var index = 0
        while (index < text.length) {
            val end = (index + CHUNK).coerceAtMost(text.length)
            emit(InterpretationChunk.Text(text.substring(index, end)))
            index = end
            delay(12)
        }
        emit(InterpretationChunk.Complete)
    }

    private companion object {
        const val CHUNK = 28
    }
}
