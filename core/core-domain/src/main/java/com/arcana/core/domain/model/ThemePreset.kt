package com.arcana.core.domain.model

data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val seedHex: String,
    val secondaryHex: String,
    val tertiaryHex: String,
    val isDark: Boolean,
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
    val useDynamicColor: Boolean,
    val deckArtId: String,
)
