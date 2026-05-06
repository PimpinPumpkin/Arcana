package com.arcana.core.domain.model

data class Card(
    val id: String,
    val name: String,
    val arcana: Arcana,
    val keywordsUpright: List<String>,
    val keywordsReversed: List<String>,
    val uprightMeaning: String,
    val reversedMeaning: String,
    val description: String,
    val element: Element?,
    val astrology: String?,
    val numerology: String?,
    val imageRef: String,
) {
    val isMajor: Boolean get() = arcana is Arcana.Major
    val suit: Suit? get() = (arcana as? Arcana.Minor)?.suit

    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()

        // Numeric search: "5" should match Five of Wands, Hierophant (V), etc.
        // Word search: "five" should match the same.
        val numericMatch: Boolean = when (val a = arcana) {
            is Arcana.Major -> q == a.number.toString() ||
                q == romanNumeralLower(a.number)
            is Arcana.Minor -> q == a.rank.numericValue.toString() ||
                a.rank.displayName.lowercase() == q ||
                a.rank.displayName.lowercase().startsWith(q)
        }
        if (numericMatch) return true

        return name.lowercase().contains(q) ||
            keywordsUpright.any { it.lowercase().contains(q) } ||
            keywordsReversed.any { it.lowercase().contains(q) } ||
            uprightMeaning.lowercase().contains(q) ||
            reversedMeaning.lowercase().contains(q)
    }

    private fun romanNumeralLower(n: Int): String = when (n) {
        0 -> "0"
        1 -> "i"; 2 -> "ii"; 3 -> "iii"; 4 -> "iv"; 5 -> "v"
        6 -> "vi"; 7 -> "vii"; 8 -> "viii"; 9 -> "ix"; 10 -> "x"
        11 -> "xi"; 12 -> "xii"; 13 -> "xiii"; 14 -> "xiv"; 15 -> "xv"
        16 -> "xvi"; 17 -> "xvii"; 18 -> "xviii"; 19 -> "xix"; 20 -> "xx"
        21 -> "xxi"
        else -> n.toString()
    }
}

enum class Orientation { UPRIGHT, REVERSED }

data class DrawnCard(
    val card: Card,
    val orientation: Orientation,
    val positionIndex: Int,
)
