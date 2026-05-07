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

        // Numeric/exact-rank match: "5", "v", "five" → match this card if it's a Five.
        // Cheap path tried first.
        val exactRankMatch: Boolean = when (val a = arcana) {
            is Arcana.Major -> q == a.number.toString() ||
                q == romanNumeralLower(a.number)
            is Arcana.Minor -> q == a.rank.numericValue.toString() ||
                a.rank.displayName.lowercase() == q
        }
        if (exactRankMatch) return true

        // Then substring search across the regular text fields AND a synthetic
        // "5 of cups" form so multi-word queries with digits work
        // (e.g. "5 of cups", "viii of swords", "ii hierophant").
        val haystack = searchHaystack
        return haystack.contains(q)
    }

    /**
     * Lowercased, space-joined bag of words this card should match. Built once per
     * card instance; cheap because Card is a stable data class held in a singleton
     * cache (CardRepositoryImpl).
     */
    private val searchHaystack: String by lazy {
        buildString {
            append(name.lowercase())
            append(' ')
            // Synthetic numeric and roman forms of the name — so "5 of cups" finds
            // Five of Cups and "xvi tower" finds The Tower.
            when (val a = arcana) {
                is Arcana.Major -> {
                    append(a.number).append(' ')
                    append(romanNumeralLower(a.number)).append(' ')
                }
                is Arcana.Minor -> {
                    append(a.rank.numericValue).append(" of ").append(a.suit.displayName.lowercase()).append(' ')
                    append(romanNumeralLower(a.rank.numericValue)).append(" of ").append(a.suit.displayName.lowercase()).append(' ')
                }
            }
            keywordsUpright.forEach { append(it.lowercase()).append(' ') }
            keywordsReversed.forEach { append(it.lowercase()).append(' ') }
            append(uprightMeaning.lowercase()).append(' ')
            append(reversedMeaning.lowercase()).append(' ')
            // Tag-style fields: element, astrology, numerology, suit, arcana
            // tier. Lets searches like "fire", "aries", "saturn", "wands",
            // "major" land on the right cards.
            element?.displayName?.let { append(it.lowercase()).append(' ') }
            astrology?.let { append(it.lowercase()).append(' ') }
            numerology?.let { append(it.lowercase()).append(' ') }
            (arcana as? Arcana.Minor)?.suit?.displayName?.let { append(it.lowercase()).append(' ') }
            append(if (isMajor) "major" else "minor").append(' ')
            // The card's long-form description rounds out the haystack so
            // searches against thematic phrases (e.g. "new beginnings",
            // "shadow") find related cards.
            append(description.lowercase())
        }
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
