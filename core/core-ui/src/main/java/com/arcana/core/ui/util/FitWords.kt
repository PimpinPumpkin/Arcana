package com.arcana.core.ui.util

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * How far [style] has to shrink, as a factor of at most 1, for the longest word of [text] to fit
 * in [widthPx]. Compose breaks a word that is wider than its line in two, which reads badly on
 * something as small as a card.
 */
fun TextMeasurer.scaleToFitWords(text: String, style: TextStyle, widthPx: Float): Float {
    val widest = text.split(' ', '\n').filter { it.isNotBlank() }
        .maxOfOrNull { measure(it, style, softWrap = false, maxLines = 1).size.width }
        ?: return 1f
    return if (widest <= widthPx || widest == 0) 1f else widthPx / widest
}

fun TextStyle.scaledBy(factor: Float): TextStyle =
    if (factor >= 1f) this
    else copy(fontSize = fontSize * factor, lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight)
