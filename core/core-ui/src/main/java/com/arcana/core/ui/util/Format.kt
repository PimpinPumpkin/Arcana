package com.arcana.core.ui.util

import java.util.Locale
import kotlin.math.roundToInt

/** A file size the way a phone's own settings would show it. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> "${(bytes / 1e6).roundToInt()} MB"
    else -> "${(bytes / 1e3).roundToInt()} KB"
}

fun cardCount(n: Int): String = if (n == 1) "1 card" else "$n cards"
