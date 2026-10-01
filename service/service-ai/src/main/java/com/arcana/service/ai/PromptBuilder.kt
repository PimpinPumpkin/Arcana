package com.arcana.service.ai

/**
 * The prompt for a large hosted model, which can be trusted with the whole reading at once. The
 * on-device model gets [ReadingScript] instead.
 */
internal object PromptBuilder {

    fun system(tone: InterpretationTone): String = """
${ReadingScript.voice(tone)}

Format the reading in Markdown, exactly like this:

One section per card, in the order given, each under the heading shown for it and 2 or 3 sentences long. Tie the card's meaning to its place in the spread.

Then a last section headed "## Summary": 2 to 4 sentences on what the cards add up to. If a question was asked, answer it plainly here.

Cover every card. Do not add cards that were not drawn. No preamble and no sign-off.
""".trim()

    fun user(request: InterpretationRequest): String = buildString {
        val drawn = request.drawnCards.sortedBy { it.positionIndex }
        val question = ReadingScript.question(request)
        appendLine(if (question != null) "The reader asks: \"$question\"" else "The reader has no particular question. They want to know where things stand right now.")
        appendLine("Spread: ${request.spread.name}.")
        appendLine()
        drawn.forEachIndexed { i, card ->
            val position = request.spread.positions.firstOrNull { it.index == card.positionIndex }
            appendLine(ReadingScript.cardBrief(i + 1, drawn.size, card, position))
            appendLine("Heading to use: ${ReadingScript.heading(card, position, numbered = drawn.size > 1)}")
            appendLine()
        }
    }
}
