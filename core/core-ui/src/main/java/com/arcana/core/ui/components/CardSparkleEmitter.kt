package com.arcana.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 12
private const val ANIM_DURATION_MS = 650
private const val MIN_TRAVEL_FRACTION = 0.45f
private const val MAX_TRAVEL_FRACTION = 0.95f
private const val MIN_RADIUS_PX = 2f
private const val MAX_RADIUS_PX = 4f

private val SparkleColors = listOf(
    Color(0xFFFFF4C2),
    Color(0xFFFFE08A),
    Color(0xFFFFFFFF),
    Color(0xFFE7D8FF),
)

private data class Particle(
    val angle: Float,
    val travelFraction: Float,
    val radius: Float,
    val color: Color,
)

/**
 * Emits a short burst of dust/star particles from the center whenever
 * [triggerKey] changes. Designed to be overlaid on a tappable card so each tap
 * produces a quick sparkle. The initial composition does not emit — only later
 * key changes do, so a freshly composed card stays quiet until tapped.
 */
@Composable
fun CardSparkleEmitter(
    triggerKey: Any,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    val particles = remember(triggerKey) { generateParticles() }

    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(ANIM_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cur = progress.value
        if (cur >= 1f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxReach = minOf(size.width, size.height) / 2f
        particles.forEach { p ->
            val travelled = p.travelFraction * maxReach * cur
            val pos = Offset(
                center.x + cos(p.angle) * travelled,
                center.y + sin(p.angle) * travelled,
            )
            val alpha = (1f - cur).coerceIn(0f, 1f)
            val r = p.radius * (1f - cur * 0.4f)
            drawCircle(color = p.color.copy(alpha = alpha), radius = r, center = pos)
        }
    }
}

private fun generateParticles(): List<Particle> = List(PARTICLE_COUNT) {
    Particle(
        angle = (Random.nextFloat() * 2f * PI).toFloat(),
        travelFraction = MIN_TRAVEL_FRACTION + Random.nextFloat() * (MAX_TRAVEL_FRACTION - MIN_TRAVEL_FRACTION),
        radius = MIN_RADIUS_PX + Random.nextFloat() * (MAX_RADIUS_PX - MIN_RADIUS_PX),
        color = SparkleColors.random(),
    )
}
