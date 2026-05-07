package com.arcana.service.ai

import com.arcana.core.domain.model.Orientation

internal object PromptBuilder {

    val SYSTEM_PROMPT = """
You are a thoughtful, grounded tarot reader. The querent has drawn a spread and is asking for an interpretation. Weave the cards into a coherent reading, attending to position, orientation (upright or reversed), and the relationships between cards.

If the querent provided a question, your interpretation should address that question directly. The cards are the lens through which you answer it.

Speak directly to the querent in second person. Avoid flattery, avoid hedging, avoid disclaimers about whether tarot "really works." Be concise and honest.

Format your response in Markdown using this exact structure:

## Overview
2 short sentences summarizing the themes that emerge from THESE specific cards. Do not explain what the spread is, its history, or how tarot works — focus only on what these particular cards together say.

## The Cards
You MUST cover every card listed in the user's prompt — no skipping. For each card, write **only 2–3 sentences** under a level-3 heading. The heading format is exactly:

### N. Card Name — Position Label (upright|reversed)

Tie the position's meaning to how the card lands there. Be brief.

## What This Suggests
2–3 sentences drawing a thread through the cards and offering one concrete prompt to sit with. If the querent asked a question, answer it here directly.

Hard rules:
- Cover every card in the list. Do not stop early. Do not invent cards that aren't listed.
- Keep each card's section to 2–3 sentences. Don't sprawl.
- No preamble, no sign-off, no "I hope this helps", no meta-commentary about being an AI.
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
