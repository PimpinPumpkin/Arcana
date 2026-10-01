package com.arcana.core.domain.model

/**
 * A color theme. The three colors are where its palette starts; the full scheme, light and dark,
 * is worked out from them when the theme is applied.
 */
data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val seedHex: String,
    val secondaryHex: String,
    val tertiaryHex: String,
    /** The color backgrounds are tinted toward. The seed, unless the theme wants something warmer or cooler. */
    val neutralHex: String? = null,
    /** Takes its colors from the wallpaper instead (Android 12 and later). */
    val supportsDynamic: Boolean = false,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppearanceSettings(
    val themeId: String,
    val themeMode: ThemeMode,
    val deckArtId: String,
)
