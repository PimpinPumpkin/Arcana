package com.arcana.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.ui.theme.CardShapes
import com.arcana.core.ui.util.deckArtIndex

/**
 * The back of a card. A deck that came with its own back image shows that; every other deck gets
 * a back drawn in the theme's colors, which needs no file at all.
 */
@Composable
fun CardBackView(
    modifier: Modifier = Modifier,
    deck: DeckArt? = null,
) {
    val index = deckArtIndex()
    val version = index.version
    val uri = remember(deck?.id, version) { deck?.let(index::backUri) }
    var broken by remember(uri) { mutableStateOf(false) }
    // The "fixed" roles are the same tones in light and dark, so the back of a card does not turn
    // pale when the app goes dark: a deep field in the theme's main color, trimmed in its second.
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.secondaryFixedDim
    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .shadow(8.dp, CardShapes.tarotCard)
            .clip(CardShapes.tarotCard)
            .background(Brush.linearGradient(listOf(scheme.onPrimaryFixed, scheme.onPrimaryFixedVariant, scheme.onPrimaryFixed)))
            .border(1.dp, accent.copy(alpha = 0.6f), CardShapes.tarotCard),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null && !broken) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(uri, version, context) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(false)
                        .memoryCacheKey("arcana-back-${deck?.id}-$version")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { broken = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Everything is sized from the card, so a small card gets a small frame.
                val unit = size.minDimension
                frame(inset = unit * 0.08f, width = unit * 0.02f, color = accent.copy(alpha = 0.85f))
                frame(inset = unit * 0.16f, width = unit * 0.01f, color = accent.copy(alpha = 0.4f))

                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = unit / 4f
                val innerR = outerR * 0.4f
                for (i in 0 until 8) {
                    rotate(degrees = i * 45f, pivot = Offset(cx, cy)) {
                        drawCircle(color = accent.copy(alpha = 0.75f), radius = innerR / 4f, center = Offset(cx, cy - outerR))
                    }
                }
                drawCircle(
                    color = accent.copy(alpha = 0.85f),
                    radius = innerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = unit * 0.015f),
                )
            }
        }
    }
}

private fun DrawScope.frame(inset: Float, width: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(size.minDimension * 0.08f),
        style = Stroke(width = width.coerceAtLeast(1f)),
    )
}
