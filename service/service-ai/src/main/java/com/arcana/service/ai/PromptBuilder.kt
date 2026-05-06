package com.arcana.service.ai

import com.arcana.core.domain.model.Orientation

internal object PromptBuilder {

    val SYSTEM_PROMPT = """
You are a thoughtful, grounded tarot reader. The querent has drawn a spread and is asking
for an interpretation. Your task is to weave the cards into a single coherent reading,
attending to:
  - the position of each card in the spread (its meaning matters as much as the card itself),
  - the orientation (upright or reversed),
  - any question the querent provided,
  - and the relationship between cards as a whole — themes, tensions, progressions.

Speak directly to the querent in second person. Avoid flattery, avoid hedging, avoid disclaimers
about whether tarot "really works." Treat it as a contemplative practice — a mirror for
reflection, not a fortune-telling oracle. Be honest if a reading is hard.

Length: 4–8 short paragraphs. Markdown headings are fine for sections.
""".trimIndent()

    fun userPrompt(request: InterpretationRequest): String = buildString {
        request.question?.takeIf { it.isNotBlank() }?.let {
            appendLine("Question: $it")
            appendLine()
        }
        appendLine("Spread: ${request.spread.name}")
        appendLine(request.spread.description)
        appendLine()
        appendLine("Cards drawn:")
        request.drawnCards.sortedBy { it.positionIndex }.forEach { drawn ->
            val pos = request.spread.positions.firstOrNull { it.index == drawn.positionIndex }
            val orient = if (drawn.orientation == Orientation.REVERSED) " (reversed)" else ""
            appendLine("- Position ${drawn.positionIndex} — ${pos?.label ?: "?"}: ${drawn.card.name}$orient")
            pos?.meaning?.let { appendLine("    (this position represents: $it)") }
        }
        appendLine()
        appendLine("Tone preference: ${request.tone.systemHint}")
        appendLine()
        appendLine("Please offer a thoughtful interpretation.")
    }
}
