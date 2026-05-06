package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.arcana.core.ui.theme.CardShapes
import androidx.compose.foundation.Canvas

/**
 * A decorative card back. Pure-Compose so it works with no asset shipped.
 */
@Composable
fun CardBackView(
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val deep = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .shadow(8.dp, CardShapes.tarotCard)
            .clip(CardShapes.tarotCard)
            .background(
                brush = Brush.verticalGradient(
                    listOf(deep, deep.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.6f), CardShapes.tarotCard)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Outer frame
            drawRoundedFrame(stroke = Stroke(width = 2.dp.toPx()), color = accent.copy(alpha = 0.7f))
            drawRoundedFrame(stroke = Stroke(width = 1.dp.toPx()), color = accent.copy(alpha = 0.3f), inset = 12.dp.toPx())

            // Star burst in center
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = (size.minDimension / 4f)
            val innerR = outerR * 0.4f
            for (i in 0 until 8) {
                rotate(degrees = i * 45f, pivot = Offset(cx, cy)) {
                    drawCircle(
                        color = accent.copy(alpha = 0.6f),
                        radius = innerR / 4f,
                        center = Offset(cx, cy - outerR),
                    )
                }
            }
            drawCircle(
                color = accent.copy(alpha = 0.7f),
                radius = innerR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedFrame(
    stroke: Stroke,
    color: Color,
    inset: Float = 0f,
) {
    val left = inset
    val top = inset
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        style = stroke,
    )
}
