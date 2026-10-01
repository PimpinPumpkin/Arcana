package com.arcana.core.data.repository

import com.arcana.core.domain.model.ThemePreset

object ThemePresets {
    const val DEFAULT_ID = "mystic_twilight"

    val MYSTIC_TWILIGHT = ThemePreset(
        id = "mystic_twilight",
        name = "Mystic Twilight",
        description = "Deep purple and violet with gold accents.",
        seedHex = "#6B3FA0",
        secondaryHex = "#D4AF37",
        tertiaryHex = "#A06CD5",
    )

    val MOONLIGHT = ThemePreset(
        id = "moonlight",
        name = "Moonlight",
        description = "Soft silver, lavender and ivory.",
        seedHex = "#7C82B5",
        secondaryHex = "#B8A6D9",
        tertiaryHex = "#E2D9F3",
    )

    val GOLDEN_SUN = ThemePreset(
        id = "golden_sun",
        name = "Golden Sun",
        description = "Amber, terracotta and cream.",
        seedHex = "#C8843E",
        secondaryHex = "#A14E2A",
        tertiaryHex = "#E8C99B",
    )

    val FOREST_ORACLE = ThemePreset(
        id = "forest_oracle",
        name = "Forest Oracle",
        description = "Emerald, moss and copper.",
        seedHex = "#2E6B3F",
        secondaryHex = "#A8632B",
        tertiaryHex = "#7CA47D",
    )

    val RIDER_WAITE_CLASSIC = ThemePreset(
        id = "rider_waite_classic",
        name = "Rider-Waite Classic",
        description = "Warm cream with the reds and yellows of the 1909 deck.",
        seedHex = "#B8423D",
        secondaryHex = "#E5C770",
        tertiaryHex = "#3F6FAE",
        // Cream paper, not the pink a red seed would tint it.
        neutralHex = "#E5C770",
    )

    val MIDNIGHT_INK = ThemePreset(
        id = "midnight_ink",
        name = "Midnight Ink",
        description = "Near black with luminous violet, for late-night readings.",
        seedHex = "#7E5BEF",
        secondaryHex = "#1F1B2E",
        tertiaryHex = "#B8A4FF",
    )

    val DYNAMIC = ThemePreset(
        id = "dynamic",
        name = "Wallpaper colors",
        description = "Colors taken from your wallpaper.",
        seedHex = "#000000",
        secondaryHex = "#000000",
        tertiaryHex = "#000000",
        supportsDynamic = true,
    )

    val ALL = listOf(
        MYSTIC_TWILIGHT,
        MIDNIGHT_INK,
        FOREST_ORACLE,
        MOONLIGHT,
        GOLDEN_SUN,
        RIDER_WAITE_CLASSIC,
        DYNAMIC,
    )
}
