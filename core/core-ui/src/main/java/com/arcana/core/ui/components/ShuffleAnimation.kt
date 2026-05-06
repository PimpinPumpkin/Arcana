package com.arcana.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Three card-back silhouettes orbiting / rotating, evoking a shuffle.
 * Self-contained — no card data required.
 */
@Composable
fun ShuffleAnimation(
    cardWidth: Dp = 120.dp,
    modifier: Modifier = Modifier,
    durationMs: Int = 1800,
) {
    val cardHeight: Dp = cardWidth / com.arcana.core.ui.components.CARD_ASPECT
    val transition = rememberInfiniteTransition(label = "shuffle")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shuffle-angle",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight + 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        val radius = 28.dp
        repeat(3) { i ->
            val phase = angle + i * 120f
            val rad = phase * Math.PI.toFloat() / 180f
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        translationX = cos(rad) * radius.toPx()
                        translationY = sin(rad) * radius.toPx() * 0.4f
                        rotationZ = sin(rad) * 6f
                    },
            ) {
                CardBackView()
            }
        }
    }
}
