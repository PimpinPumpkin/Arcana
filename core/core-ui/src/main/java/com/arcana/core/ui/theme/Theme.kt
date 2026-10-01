package com.arcana.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.Variant

@Composable
fun ArcanaTheme(
    preset: ThemePreset,
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (preset.supportsDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        remember(isDark, context) { if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
    } else {
        remember(preset, isDark) { schemeFor(preset, isDark) }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArcanaTypography,
        shapes = ArcanaShapes,
        content = content,
    )
}

/**
 * A whole Material color scheme from a theme's three colors. Each color becomes a tonal palette,
 * and every role (containers, surfaces at each height, outlines, the colors that sit on them) is
 * a tone picked from one of those, so text always has contrast and nothing is left at the
 * library's stock purple.
 */
fun schemeFor(preset: ThemePreset, dark: Boolean): ColorScheme {
    val seed = argb(preset.seedHex)
    val tint = Hct.fromInt(argb(preset.neutralHex ?: preset.seedHex)).hue
    val s = DynamicScheme(
        Hct.fromInt(seed),
        Variant.TONAL_SPOT,
        dark,
        0.0,
        TonalPalette.fromInt(seed),
        TonalPalette.fromInt(argb(preset.secondaryHex)),
        TonalPalette.fromInt(argb(preset.tertiaryHex)),
        TonalPalette.fromHueAndChroma(tint, SURFACE_CHROMA),
        TonalPalette.fromHueAndChroma(tint, SURFACE_VARIANT_CHROMA),
    )
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        error = Color(s.error),
        onError = Color(s.onError),
        errorContainer = Color(s.errorContainer),
        onErrorContainer = Color(s.onErrorContainer),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        surfaceBright = Color(s.surfaceBright),
        surfaceDim = Color(s.surfaceDim),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        primaryFixed = Color(s.primaryFixed),
        primaryFixedDim = Color(s.primaryFixedDim),
        onPrimaryFixed = Color(s.onPrimaryFixed),
        onPrimaryFixedVariant = Color(s.onPrimaryFixedVariant),
        secondaryFixed = Color(s.secondaryFixed),
        secondaryFixedDim = Color(s.secondaryFixedDim),
        onSecondaryFixed = Color(s.onSecondaryFixed),
        onSecondaryFixedVariant = Color(s.onSecondaryFixedVariant),
        tertiaryFixed = Color(s.tertiaryFixed),
        tertiaryFixedDim = Color(s.tertiaryFixedDim),
        onTertiaryFixed = Color(s.onTertiaryFixed),
        onTertiaryFixedVariant = Color(s.onTertiaryFixedVariant),
    )
}

// How strongly backgrounds take on the theme's hue. Material's own default is 6 and 8; a little
// more gives each theme a paper of its own.
private const val SURFACE_CHROMA = 8.0
private const val SURFACE_VARIANT_CHROMA = 12.0

private fun argb(hex: String): Int = (0xFF000000 or hex.removePrefix("#").toLong(16)).toInt()
