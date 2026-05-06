package com.arcana.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.domain.model.ThemePreset

@Composable
fun ArcanaTheme(
    preset: ThemePreset,
    themeMode: ThemeMode,
    useDynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val wantsDynamic = (useDynamicColor || preset.supportsDynamic) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (wantsDynamic) {
        val ctx = LocalContext.current
        // Dynamic schemes are cheap (system-cached) but still memoize to avoid
        // identity churn that can ripple through MaterialTheme readers.
        remember(isDark, ctx) {
            if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
    } else {
        // Static scheme construction does ~30 Color allocations and parses a half-
        // dozen hex strings — trivially cacheable by preset id + isDark.
        remember(preset.id, isDark) { buildColorScheme(preset, isDark) }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArcanaTypography,
        shapes = ArcanaShapes,
        content = content,
    )
}

private fun buildColorScheme(
    preset: ThemePreset,
    isDark: Boolean,
) = if (isDark) {
    darkColorScheme(
        primary = parseHex(preset.seedHex),
        onPrimary = Color.White,
        primaryContainer = parseHex(preset.seedHex).darken(0.5f),
        onPrimaryContainer = Color.White,
        secondary = parseHex(preset.secondaryHex),
        onSecondary = Color.Black,
        secondaryContainer = parseHex(preset.secondaryHex).darken(0.4f),
        onSecondaryContainer = Color.White,
        tertiary = parseHex(preset.tertiaryHex),
        onTertiary = Color.Black,
        tertiaryContainer = parseHex(preset.tertiaryHex).darken(0.4f),
        onTertiaryContainer = Color.White,
        background = ArcanaColors.NightVeil,
        onBackground = Color(0xFFE8E1F2),
        surface = ArcanaColors.NightVeil,
        onSurface = Color(0xFFE8E1F2),
        surfaceVariant = ArcanaColors.NightVeilContainer,
        onSurfaceVariant = Color(0xFFCBC0DC),
        outline = Color(0xFF6F6580),
    )
} else {
    lightColorScheme(
        primary = parseHex(preset.seedHex),
        onPrimary = Color.White,
        primaryContainer = parseHex(preset.seedHex).lighten(0.7f),
        onPrimaryContainer = parseHex(preset.seedHex).darken(0.6f),
        secondary = parseHex(preset.secondaryHex),
        onSecondary = Color.White,
        secondaryContainer = parseHex(preset.secondaryHex).lighten(0.7f),
        onSecondaryContainer = parseHex(preset.secondaryHex).darken(0.6f),
        tertiary = parseHex(preset.tertiaryHex),
        onTertiary = Color.White,
        tertiaryContainer = parseHex(preset.tertiaryHex).lighten(0.7f),
        onTertiaryContainer = parseHex(preset.tertiaryHex).darken(0.6f),
        background = Color(0xFFFAF6F0),
        onBackground = Color(0xFF1F1B16),
        surface = Color(0xFFFAF6F0),
        onSurface = Color(0xFF1F1B16),
        surfaceVariant = Color(0xFFEDE6DA),
        onSurfaceVariant = Color(0xFF4D463A),
        outline = Color(0xFF7C7468),
    )
}

private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    return Color(r, g, b)
}

private fun Color.darken(factor: Float): Color = Color(
    red = red * (1f - factor),
    green = green * (1f - factor),
    blue = blue * (1f - factor),
    alpha = alpha,
)

private fun Color.lighten(factor: Float): Color = Color(
    red = red + (1f - red) * factor,
    green = green + (1f - green) * factor,
    blue = blue + (1f - blue) * factor,
    alpha = alpha,
)
