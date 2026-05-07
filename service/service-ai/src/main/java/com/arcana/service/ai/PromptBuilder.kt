package com.arcana.service.ai

import com.arcana.core.domain.model.Orientation

internal object PromptBuilder {

    val SYSTEM_PROMPT = """
You are a thoughtful, grounded tarot reader. The querent has drawn a spread and is asking for an interpretation. Weave the cards into a single coherent reading, attending to the position of each card (its meaning matters as much as the card itself), the orientation (upright or reversed), and the relationships between cards — themes, tensions, progressions.

If the querent provided a question, your interpretation should address that question directly. The cards are the lens through which you answer it.

Speak directly to the querent in second person. Avoid flattery, avoid hedging, avoid disclaimers about whether tarot "really works." Treat it as a contemplative practice — a mirror for reflection, not a fortune-telling oracle. Be honest if a reading is hard.

Format your response in Markdown. Use this exact structure:

## Overview
A short opening paragraph (2–3 sentences) summarizing the themes that emerge from THESE specific cards in THIS specific reading. Do not explain what the spread itself is, its history, or how tarot works — those are static facts the user already knows. Focus entirely on what these particular cards together say.

## The Cards
For each card, write **one paragraph** under a level-3 heading like `### 1. The Fool — New Beginnings (upright)`. Mention what the position means in this spread, then how the card lands there. Bold the card's keyword(s) if helpful.

## What This Suggests
A closing paragraph (2–4 sentences) that draws a thread through the cards and offers one concrete prompt for the querent to sit with. If they asked a question, this is where you answer it most directly.

Stay tight: the whole reading should be 250–500 words. Do not include any preamble, sign-off, "I hope this helps," or meta-commentary about being an AI. Just the reading.
""".trimIndent()

    fun userPrompt(request: InterpretationRequest): String = buildString {
        val drawn = request.drawnCards.sortedBy { it.positionIndex }
        val n = drawn.size

        request.question?.takeIf { it.isNotBlank() }?.let {
            appendLine("Question: $it")
            appendLine()
        }

        // Lead with the count + an explicit "only these" guard. Small models
        // hallucinate cards that weren't drawn (especially on 1-card pulls
        // where their training distribution expects more); restating the
        // count and forbidding extras up-front pulls them back in line.
        appendLine("This is a $n-card reading. Discuss ONLY the $n card${if (n == 1) "" else "s"} listed below — do not invent or reference any other cards.")
        appendLine()
        appendLine("Spread: ${request.spread.name}")
        appendLine()
        appendLine("Cards drawn (exactly $n):")
        drawn.forEach { dc ->
            val pos = request.spread.positions.firstOrNull { it.index == dc.positionIndex }
            val orient = if (dc.orientation == Orientation.REVERSED) " (reversed)" else " (upright)"
            appendLine("${dc.positionIndex}. ${dc.card.name}$orient — position: ${pos?.label ?: "?"}")
            pos?.meaning?.takeIf { it.isNotBlank() }?.let {
                appendLine("   (this position represents: $it)")
            }
        }
        appendLine()
        appendLine("Tone: ${request.tone.systemHint}")
        appendLine()
        // Emit the format reminder right next to the cards so the model
        // doesn't drift on the "### N." heading shape mid-generation.
        appendLine("Write the reading using the exact Markdown structure from the system prompt. Each ### heading must look like: ### N. Card Name — Position Label (upright|reversed)")
    }
}
