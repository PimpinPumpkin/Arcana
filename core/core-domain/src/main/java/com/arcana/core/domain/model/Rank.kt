package com.arcana.core.domain.model

enum class Rank(val displayName: String, val numericValue: Int) {
    ACE("Ace", 1),
    TWO("Two", 2),
    THREE("Three", 3),
    FOUR("Four", 4),
    FIVE("Five", 5),
    SIX("Six", 6),
    SEVEN("Seven", 7),
    EIGHT("Eight", 8),
    NINE("Nine", 9),
    TEN("Ten", 10),
    PAGE("Page", 11),
    KNIGHT("Knight", 12),
    QUEEN("Queen", 13),
    KING("King", 14),
    ;

    val isCourt: Boolean get() = this in COURT_RANKS

    companion object {
        val COURT_RANKS = setOf(PAGE, KNIGHT, QUEEN, KING)
    }
}
