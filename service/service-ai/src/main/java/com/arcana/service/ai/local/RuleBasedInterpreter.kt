package com.arcana.service.ai.local

import com.arcana.core.domain.model.AiBackendType
import com.arcana.core.domain.model.Orientation
import com.arcana.service.ai.InterpretationChunk
import com.arcana.service.ai.InterpretationRequest
import com.arcana.service.ai.TarotInterpreter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The simplest, fully-offline interpreter. Composes a reading from the canonical
 * card meanings — no LLM required. Works on day one with no API key, no model download.
 *
 * The text is honest about being template-based; we don't pretend it's AI-generated.
 */
@Singleton
class RuleBasedInterpreter @Inject constructor() : TarotInterpreter {
    override val type = AiBackendType.RULE_BASED
    override val isAvailable: Boolean = true

    override fun interpret(request: InterpretationRequest): Flow<InterpretationChunk> = flow {
        emit(InterpretationChunk.Status("Composing reading from card meanings…"))

        val sb = StringBuilder()

        request.question?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("**Your question:** $it")
            sb.appendLine()
        }

        sb.appendLine("**Spread:** ${request.spread.name} — ${request.spread.description}")
        sb.appendLine()

        request.drawnCards.sortedBy { it.positionIndex }.forEach { drawn ->
            val pos = request.spread.positions.firstOrNull { it.index == drawn.positionIndex }
            val isReversed = drawn.orientation == Orientation.REVERSED
            val meaningText = if (isReversed) drawn.card.reversedMeaning else drawn.card.uprightMeaning
            val keywords = if (isReversed) drawn.card.keywordsReversed else drawn.card.keywordsUpright

            sb.appendLine("### ${pos?.label ?: "Position ${drawn.positionIndex}"} — ${drawn.card.name}${if (isReversed) " (reversed)" else ""}")
            pos?.meaning?.let { sb.appendLine("_${it}_") }
            sb.appendLine()
            sb.appendLine("Keywords: ${keywords.joinToString(", ")}")
            sb.appendLine()
            sb.appendLine(meaningText)
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("_This reading is a composition of canonical card meanings, position by position. Connect the threads yourself — the cards speak more clearly when you sit with them._")

        // Stream the text in small chunks so the UI can render progressively.
        val text = sb.toString()
        val chunkSize = 24
        var index = 0
        while (index < text.length) {
            val end = (index + chunkSize).coerceAtMost(text.length)
            emit(InterpretationChunk.Text(text.substring(index, end)))
            index = end
            delay(20)
        }
        emit(InterpretationChunk.Complete)
    }
}
