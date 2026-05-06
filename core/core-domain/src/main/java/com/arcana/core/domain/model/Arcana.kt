package com.arcana.core.domain.model

sealed interface Arcana {
    val displayLabel: String

    data class Major(val number: Int) : Arcana {
        override val displayLabel: String get() = romanNumeral(number)

        companion object {
            private fun romanNumeral(n: Int): String = when (n) {
                0 -> "0"
                1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
                6 -> "VI"; 7 -> "VII"; 8 -> "VIII"; 9 -> "IX"; 10 -> "X"
                11 -> "XI"; 12 -> "XII"; 13 -> "XIII"; 14 -> "XIV"; 15 -> "XV"
                16 -> "XVI"; 17 -> "XVII"; 18 -> "XVIII"; 19 -> "XIX"; 20 -> "XX"
                21 -> "XXI"
                else -> n.toString()
            }
        }
    }

    data class Minor(val suit: Suit, val rank: Rank) : Arcana {
        override val displayLabel: String get() = "${rank.displayName} of ${suit.displayName}"
    }
}
