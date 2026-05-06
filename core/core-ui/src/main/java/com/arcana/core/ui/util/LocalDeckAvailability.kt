package com.arcana.core.ui.util

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal so deeply-nested card composables can ask "does this deck
 * have art?" without threading the answer through every parameter.
 *
 * Default returns false — caller must override at the screen root.
 */
val LocalDeckHasArt = compositionLocalOf<(deckId: String) -> Boolean> { { _ -> false } }
