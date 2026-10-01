package com.arcana.service.ai

import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Orientation
import com.arcana.core.domain.model.Position

/**
 * A reading, planned as a conversation for a small on-device model.
 *
 * Small models drift when asked to hold a format across a whole reading: they skip cards, invent
 * ones that were not drawn, and mangle headings. So the app keeps the structure and the model is
 * only ever asked for a few sentences about one card at a time, with that card's own keywords in
 * front of it. The headings are written by the app and never pass through the model.
 *
 * The wording here was settled by running it against real models with `tools/reading-cli`. What
 * mattered most, in order: asking as a brief ("write to the reader") rather than in the reader's
 * own voice, which the smallest models answered in the first person; making each reply open on
 * "You"; and keeping the reply to whole sentences with a grammar.
 */
data class ReadingScript(
    val system: String,
    /** What is being read: the question and the spread. It leads the first turn. */
    val opening: String,
    val turns: List<Turn>,
) {
    data class Turn(
        /** Markdown shown above what the model writes. */
        val heading: String,
        /** What the model is asked. */
        val prompt: String,
        /** The shape the answer must take, in llama.cpp's GBNF. */
        val grammar: String,
        val maxTokens: Int,
        /** One line that stands for this card when the conversation has to be started over. */
        val recap: String?,
    )

    /**
     * What to say to begin the conversation at turn [index]: the opening, the cards before it in a
     * line each, then the turn. Turn 0 always starts this way. A later turn does when the model's
     * context has filled up, which a spread of twenty-odd cards can do.
     */
    fun promptFrom(index: Int): String = buildString {
        append(opening)
        val before = turns.take(index).mapNotNull { it.recap }
        if (before.isNotEmpty()) {
            append("Cards already read:\n")
            before.forEach { append(it).append('\n') }
            append('\n')
        }
        append(turns[index].prompt)
    }

    /** Who a reply opens on. */
    enum class Subject { READER, SOMEONE_ELSE }

    companion object {
        private const val CARD_TOKENS = 260
        private const val SUMMARY_TOKENS = 340
        private const val QUESTION_LIMIT = 300

        fun of(request: InterpretationRequest): ReadingScript {
            val drawn = request.drawnCards.sortedBy { it.positionIndex }
            val question = question(request)
            val single = drawn.size == 1
            // A long spread gets less on each card, or reading it takes minutes.
            val length = when {
                single -> 3..4
                drawn.size <= 5 -> 2..3
                else -> 2..2
            }
            val turns = ArrayList<Turn>()

            drawn.forEachIndexed { i, card ->
                val position = request.spread.positions.firstOrNull { it.index == card.positionIndex }
                turns += Turn(
                    heading = heading(card, position, numbered = !single),
                    prompt = cardBrief(i + 1, drawn.size, card, position) + "\n\n" +
                        "Write ${count(length)} sentences to the reader about what this card says" +
                        (if (question != null) " about their question." else " about their life right now."),
                    grammar = sentences(length, subject(position)),
                    maxTokens = CARD_TOKENS,
                    recap = recap(card, position),
                )
            }

            if (!single) {
                turns += Turn(
                    heading = "## Summary",
                    prompt = "That was the last card. Write 2 to 4 sentences to the reader that tie all ${drawn.size} cards together" +
                        (if (question != null) " and answer their question plainly." else " and say what to carry forward."),
                    grammar = sentences(2..4, Subject.READER),
                    maxTokens = SUMMARY_TOKENS,
                    recap = null,
                )
            }

            val opening = buildString {
                append(
                    if (question != null) "The reader asks: \"$question\"\n"
                    else "The reader has no particular question. They want to know where things stand right now.\n",
                )
                append("Spread: ${request.spread.name}.\n\n")
            }
            return ReadingScript(system(request.tone), opening, turns)
        }

        /** The question as it goes into a prompt: one line, no quote marks of its own, not too long. */
        fun question(request: InterpretationRequest): String? =
            request.question?.replace(Regex("\\s+"), " ")?.replace('"', '\'')?.trim()?.take(QUESTION_LIMIT)?.takeIf { it.isNotEmpty() }

        /** How the reading should sound, whoever writes it. */
        fun voice(tone: InterpretationTone): String =
            "You write tarot readings. You speak straight to the reader as \"you\", in plain everyday sentences. " +
                "Each card comes with its meaning: say what it means for the reader's own life, in the place it fell in the spread. " +
                "Never explain what tarot is and never describe the picture on the card. " +
                tone.systemHint

        fun system(tone: InterpretationTone): String = voice(tone) + " Write no lists and no headings."

        /** The heading for one card: position first, since that is how a spread is read. */
        fun heading(card: DrawnCard, position: Position?, numbered: Boolean): String {
            val place = position?.label?.takeIf { it.isNotBlank() } ?: "Card ${card.positionIndex}"
            val reversed = if (card.orientation == Orientation.REVERSED) " (reversed)" else ""
            val number = if (numbered) "${card.positionIndex}. " else ""
            return "### $number$place: ${card.card.name}$reversed"
        }

        /**
         * Everything the model is told about one card. The keywords are the app's own, from
         * `cards.json`: a small model's memory of what a card means is not to be relied on.
         */
        fun cardBrief(number: Int, total: Int, card: DrawnCard, position: Position?): String = buildString {
            val reversed = card.orientation == Orientation.REVERSED
            append("Card $number of $total")
            val label = position?.label?.trim().orEmpty()
            if (label.isNotEmpty()) {
                append(" fell in the place called \"${label.replace('"', '\'')}\"")
                meaning(position)?.let { append(" ($it)") }
            }
            append(".\nThe card is ${card.card.name}${if (reversed) ", reversed" else ""}.")
            val keywords = if (reversed) card.card.keywordsReversed else card.card.keywordsUpright
            if (keywords.isNotEmpty()) append(" It means: ${keywords.joinToString(", ")}.")
        }

        private fun recap(card: DrawnCard, position: Position?): String {
            val reversed = card.orientation == Orientation.REVERSED
            val keywords = if (reversed) card.card.keywordsReversed else card.card.keywordsUpright
            val place = position?.label?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it: " }.orEmpty()
            return "$place${card.card.name}${if (reversed) ", reversed" else ""}" +
                (if (keywords.isNotEmpty()) " (${keywords.joinToString(", ")})" else "")
        }

        /** What a position stands for, worded to sit inside a sentence. */
        private fun meaning(position: Position?): String? =
            position?.meaning?.replace(Regex("\\s+"), " ")?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }?.replaceFirstChar(Char::lowercase)

        // A place in the spread that is about another person ("Them", "What they want") is read
        // about them. Every other place is read about the reader.
        private val someoneElse = Regex("\\b(them|they|their)\\b", RegexOption.IGNORE_CASE)

        private fun subject(position: Position?): Subject =
            if (position != null && someoneElse.containsMatchIn(position.label + " " + position.meaning)) Subject.SOMEONE_ELSE else Subject.READER

        private fun count(range: IntRange) = if (range.first == range.last) "${range.first}" else "${range.first} or ${range.last}"

        /**
         * A grammar for a short run of ordinary sentences. Each holds no line break, list marker,
         * heading mark or dash, and ends at the first full stop, so the reply cannot run on and
         * cannot turn into a list. The first one opens on its [subject]: left to themselves the
         * smaller models open with a greeting, or talk about the reader instead of to them.
         */
        fun sentences(range: IntRange, subject: Subject): String {
            val optional = range.last - range.first
            // "Your" is not allowed to lead into "question", which the smallest models reach for.
            val opener = when (subject) {
                Subject.READER -> "\"You\" [ '\\u2019] | \"Your \" [a-pr-z]"
                Subject.SOMEONE_ELSE -> "\"They\" [ '\\u2019] | \"Their \""
            }
            return buildString {
                append("root ::= first")
                repeat(range.first - 1) { append(" \" \" sentence") }
                if (optional > 0) append(" (\" \" sentence){0,$optional}")
                append("\n")
                append("first ::= ($opener) char{10,$SENTENCE_CHARS} end\n")
                append("sentence ::= [A-Z0-9\"'] char{12,$SENTENCE_CHARS} end\n")
                append("char ::= [^\\n.!?#*<>\\[\\]{}|\\u2013\\u2014]\n")
                append("end ::= [.!?] [\"']?\n")
            }
        }

        private const val SENTENCE_CHARS = 300
    }
}
