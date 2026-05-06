package com.arcana.core.data.repository

import com.arcana.core.domain.model.ThemePreset

object ThemePresets {
    const val DEFAULT_ID = "mystic_twilight"

    val MYSTIC_TWILIGHT = ThemePreset(
        id = "mystic_twilight",
        name = "Mystic Twilight",
        description = "Deep purples and violet, with gold accents — the default mystical mood.",
        seedHex = "#6B3FA0",
        secondaryHex = "#D4AF37",
        tertiaryHex = "#A06CD5",
        isDark = true,
    )

    val MOONLIGHT = ThemePreset(
        id = "moonlight",
        name = "Moonlight",
        description = "Soft silver, lavender and ivory — a quiet light theme.",
        seedHex = "#7C82B5",
        secondaryHex = "#B8A6D9",
        tertiaryHex = "#E2D9F3",
        isDark = false,
    )

    val GOLDEN_SUN = ThemePreset(
        id = "golden_sun",
        name = "Golden Sun",
        description = "Amber, terracotta and cream — warm and grounded.",
        seedHex = "#C8843E",
        secondaryHex = "#A14E2A",
        tertiaryHex = "#E8C99B",
        isDark = false,
    )

    val FOREST_ORACLE = ThemePreset(
        id = "forest_oracle",
        name = "Forest Oracle",
        description = "Emerald, moss and copper — earthen, witchy, dark.",
        seedHex = "#2E6B3F",
        secondaryHex = "#A8632B",
        tertiaryHex = "#7CA47D",
        isDark = true,
    )

    val RIDER_WAITE_CLASSIC = ThemePreset(
        id = "rider_waite_classic",
        name = "Rider-Waite Classic",
        description = "Warm cream, traditional reds and yellows — straight from the 1909 deck.",
        seedHex = "#B8423D",
        secondaryHex = "#E5C770",
        tertiaryHex = "#3F6FAE",
        isDark = false,
    )

    val MIDNIGHT_INK = ThemePreset(
        id = "midnight_ink",
        name = "Midnight Ink",
        description = "Almost-black backgrounds with luminous violet — for late-night readings.",
        seedHex = "#7E5BEF",
        secondaryHex = "#1F1B2E",
        tertiaryHex = "#B8A4FF",
        isDark = true,
    )

    val DYNAMIC = ThemePreset(
        id = "dynamic",
        name = "Material You",
        description = "Use system wallpaper colors (Android 12+).",
        seedHex = "#000000",
        secondaryHex = "#000000",
        tertiaryHex = "#000000",
        isDark = false,
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
