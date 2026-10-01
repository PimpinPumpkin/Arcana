package com.arcana.core.domain.model

/**
 * Works out which card an image file is for from the file's name, so a deck can be imported
 * without renaming 78 files first. Besides the app's own names (`major_00_fool`, `cups_05`,
 * `swords_page`) it reads the ways decks are commonly named: `Wands05`, `Queen of Cups`,
 * `10-of-swords`, `RWS_Tarot_16_Tower`, `pents14`.
 *
 * A trump is found by its name when the file has one and by its number otherwise. Decks disagree
 * on whether Strength or Justice is number 8, and the name is the one that cannot be wrong.
 */
object CardFileNames {
    /** The card [fileName] is a picture of, or null if the name does not say. */
    fun cardFor(fileName: String, cards: Collection<Card>): Card? {
        val base = fileName.substringAfterLast('/').substringBeforeLast('.').lowercase()
        cards.firstOrNull { it.imageRef.substringBeforeLast('.').lowercase() == base }?.let { return it }

        // "Wands05" and "major10" are two words each; so is "queen-of_cups".
        val words = base.replace(Regex("(?<=[a-z])(?=[0-9])|(?<=[0-9])(?=[a-z])"), " ").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val numbers = words.mapNotNull { it.toIntOrNull() }

        val suit = words.firstNotNullOfOrNull { SUITS[it] }
        if (suit != null) {
            val rank = words.firstNotNullOfOrNull { RANKS[it] } ?: numbers.firstNotNullOfOrNull { n -> Rank.entries.firstOrNull { it.numericValue == n } }
            return rank?.let { r -> cards.firstOrNull { (it.arcana as? Arcana.Minor)?.let { m -> m.suit == suit && m.rank == r } == true } }
        }

        val number = words.firstNotNullOfOrNull { TRUMPS[it] }
            ?: numbers.firstOrNull { it in 0..21 }?.takeIf { words.any { w -> w in TRUMP_MARKS } }
        return number?.let { n -> cards.firstOrNull { (it.arcana as? Arcana.Major)?.number == n } }
    }

    /** Whether [fileName] is the deck's card back rather than a card. */
    fun isBack(fileName: String): Boolean =
        fileName.substringAfterLast('/').substringBeforeLast('.').lowercase().replace(Regex("[^a-z]"), "") in BACKS

    private val SUITS = mapOf(
        "wands" to Suit.WANDS, "wand" to Suit.WANDS, "rods" to Suit.WANDS, "staves" to Suit.WANDS, "batons" to Suit.WANDS,
        "cups" to Suit.CUPS, "cup" to Suit.CUPS, "chalices" to Suit.CUPS,
        "swords" to Suit.SWORDS, "sword" to Suit.SWORDS,
        "pentacles" to Suit.PENTACLES, "pentacle" to Suit.PENTACLES, "pents" to Suit.PENTACLES,
        "coins" to Suit.PENTACLES, "coin" to Suit.PENTACLES, "disks" to Suit.PENTACLES, "discs" to Suit.PENTACLES,
    )

    private val RANKS = mapOf(
        "ace" to Rank.ACE, "two" to Rank.TWO, "three" to Rank.THREE, "four" to Rank.FOUR, "five" to Rank.FIVE,
        "six" to Rank.SIX, "seven" to Rank.SEVEN, "eight" to Rank.EIGHT, "nine" to Rank.NINE, "ten" to Rank.TEN,
        "page" to Rank.PAGE, "knave" to Rank.PAGE, "princess" to Rank.PAGE,
        "knight" to Rank.KNIGHT, "prince" to Rank.KNIGHT,
        "queen" to Rank.QUEEN, "king" to Rank.KING,
    )

    private val TRUMPS = mapOf(
        "fool" to 0, "magician" to 1, "priestess" to 2, "empress" to 3, "emperor" to 4, "hierophant" to 5,
        "lovers" to 6, "chariot" to 7, "strength" to 8, "hermit" to 9, "wheel" to 10, "justice" to 11,
        "hanged" to 12, "death" to 13, "temperance" to 14, "devil" to 15, "tower" to 16, "star" to 17,
        "moon" to 18, "sun" to 19, "judgement" to 20, "judgment" to 20, "world" to 21,
    )

    // A bare number says nothing by itself: "07" could be a trump or the seventh file of anything.
    private val TRUMP_MARKS = setOf("major", "maj", "trump", "trumps", "arcana")

    private val BACKS = setOf("back", "cardback", "backofcard")
}
