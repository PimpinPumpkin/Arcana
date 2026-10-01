package com.arcana.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFileNamesTest {
    private val trumps = listOf(
        "fool", "magician", "high_priestess", "empress", "emperor", "hierophant", "lovers", "chariot", "strength", "hermit", "wheel",
        "justice", "hanged_man", "death", "temperance", "devil", "tower", "star", "moon", "sun", "judgement", "world",
    )

    // The deck the way cards.json names it.
    private val cards: List<Card> = buildList {
        trumps.forEachIndexed { n, name -> add(card("major_%02d_%s".format(n, name), Arcana.Major(n))) }
        for (suit in Suit.entries) for (rank in Rank.entries) {
            val s = suit.name.lowercase()
            val id = when {
                rank == Rank.ACE -> "${s}_01_ace"
                rank.isCourt -> "${s}_${rank.name.lowercase()}"
                else -> "%s_%02d".format(s, rank.numericValue)
            }
            add(card(id, Arcana.Minor(suit, rank)))
        }
    }

    private fun card(id: String, arcana: Arcana) = Card(
        id = id, name = id, arcana = arcana, keywordsUpright = emptyList(), keywordsReversed = emptyList(),
        uprightMeaning = "", reversedMeaning = "", description = "", element = null, astrology = null, numerology = null,
        imageRef = "$id.jpg",
    )

    private fun find(name: String) = CardFileNames.cardFor(name, cards)?.id

    @Test
    fun `the app's own names find their cards whatever the case or extension`() {
        assertEquals(78, cards.size)
        cards.forEach { assertEquals(it.id, find(it.imageRef)) }
        assertEquals("major_00_fool", find("Major_00_Fool.PNG"))
        assertEquals("cups_05", find("CUPS_05.webp"))
    }

    @Test
    fun `scans named the way Wikimedia names them are recognized`() {
        assertEquals("major_10_wheel", find("RWS_Tarot_10_Wheel_of_Fortune.jpg"))
        assertEquals("major_02_high_priestess", find("RWS_Tarot_02_High_Priestess.jpg"))
        assertEquals("wands_01_ace", find("Wands01.jpg"))
        assertEquals("wands_09", find("Wands09.jpg"))
        assertEquals("cups_page", find("Cups11.jpg"))
        assertEquals("swords_knight", find("Swords12.jpg"))
        assertEquals("pentacles_queen", find("Pents13.jpg"))
        assertEquals("pentacles_king", find("Pents14.jpg"))
    }

    @Test
    fun `names written out in words are recognized`() {
        assertEquals("cups_queen", find("Queen of Cups.png"))
        assertEquals("swords_10", find("10-of-swords.jpg"))
        assertEquals("swords_10", find("Ten of Swords.jpg"))
        assertEquals("pentacles_01_ace", find("ace_of_coins.webp"))
        assertEquals("wands_page", find("Princess of Wands.jpg"))
        assertEquals("cups_05", find("cups_05_five.jpg"))
        assertEquals("major_00_fool", find("The Fool.jpg"))
        assertEquals("major_12_hanged_man", find("XII - The Hanged Man.jpg"))
        assertEquals("major_20_judgement", find("judgment.jpg"))
    }

    @Test
    fun `a trump goes by its name when its number disagrees`() {
        // A deck in the older order, with Justice eighth.
        assertEquals("major_11_justice", find("major_08_justice.jpg"))
        assertEquals("major_08_strength", find("11 Strength.png"))
    }

    @Test
    fun `a numbered trump with no name needs to say it is one`() {
        assertEquals("major_16_tower", find("major16.jpg"))
        assertEquals("major_07_chariot", find("trump_7.png"))
        assertNull(find("07.jpg"))
        assertNull(find("IMG_2041.jpg"))
        assertNull(find("major_22.jpg"))
    }

    @Test
    fun `a suit with no rank is nobody`() {
        assertNull(find("cups.jpg"))
        assertNull(find("wands_15.jpg"))
    }

    @Test
    fun `the card back is told apart from the cards`() {
        assertTrue(CardFileNames.isBack("back.jpg"))
        assertTrue(CardFileNames.isBack("Card Back.png"))
        assertFalse(CardFileNames.isBack("backdrop.png"))
        assertFalse(CardFileNames.isBack("major_00_fool.jpg"))
        assertNull(find("back.jpg"))
    }
}
