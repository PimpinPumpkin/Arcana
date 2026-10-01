package com.arcana.app

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.arcana.core.data.repository.ThemePresets
import com.arcana.core.ui.theme.schemeFor
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Every theme, light and dark, keeps its text readable on the color behind it. */
class ThemeContrastTest {
    private val themes = ThemePresets.ALL.filterNot { it.supportsDynamic }

    @Test
    fun `text has contrast on every surface it is drawn on`() {
        for (preset in themes) for (dark in listOf(false, true)) {
            val s = schemeFor(preset, dark)
            val pairs = mapOf(
                "primary" to (s.onPrimary to s.primary),
                "primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
                "secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
                "tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
                "surface" to (s.onSurface to s.surface),
                "surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
                "surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
                "surfaceVariant" to (s.onSurfaceVariant to s.surfaceVariant),
                "primary on surface" to (s.primary to s.surface),
                "error" to (s.onError to s.error),
            )
            pairs.forEach { (name, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertTrue("${preset.id} ${if (dark) "dark" else "light"} $name: $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun `each theme has surfaces of its own`() {
        val navBars = themes.map { schemeFor(it, dark = false).surfaceContainer }
        // Six themes; the two warm ones may share a paper, the rest must differ.
        assertTrue(navBars.toSet().size >= 5)
        val stock: ColorScheme = androidx.compose.material3.lightColorScheme()
        themes.forEach { assertNotEquals(it.id, stock.surfaceContainer, schemeFor(it, dark = false).surfaceContainer) }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
