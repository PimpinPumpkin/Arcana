package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.ui.util.DeckAssetResolver
import com.arcana.core.ui.util.LocalDeckHasArt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_THRESHOLD_DP = 160f

/**
 * Fullscreen tarot-card art viewer with pinch-to-zoom, drag-to-pan, and
 * swipe-down to dismiss. Double-tap toggles between fit-to-screen and a
 * moderate zoom.
 *
 * Gesture routing (single pointerInput so handlers don't fight):
 *   - 2+ fingers: pinch zoom (and pan when zoomed)
 *   - 1 finger while zoomed: pan
 *   - 1 finger while not zoomed: swipe-down dismiss (downward only)
 */
@Composable
fun ZoomableCardImage(
    card: Card,
    deck: DeckArt,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val deckHasArt = LocalDeckHasArt.current(deck.id)
        var scale by remember { mutableFloatStateOf(MIN_SCALE) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var dismissDrag by remember { mutableFloatStateOf(0f) }

        val density = LocalDensity.current
        val dismissThresholdPx = remember(density) {
            with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }
        }
        val dismissProgress = (dismissDrag / dismissThresholdPx).coerceIn(0f, 1f)
        val backdropAlpha = 1f - dismissProgress * 0.6f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha)),
        ) {
            val transformModifier = Modifier
                .fillMaxSize()
                .pointerInput(card.id) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > MIN_SCALE) {
                                scale = MIN_SCALE
                                offset = Offset.Zero
                            } else {
                                scale = DOUBLE_TAP_SCALE
                            }
                        },
                    )
                }
                .pointerInput(card.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.count { it.pressed }
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()

                            if (pointerCount >= 2) {
                                // Pinch — zoom and (when zoomed) pan
                                scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                if (scale > MIN_SCALE) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scale > MIN_SCALE) {
                                // Single finger while zoomed — pan
                                offset += pan
                                event.changes.forEach { it.consume() }
                            } else {
                                // Single finger, not zoomed — swipe down to dismiss
                                val newDrag = (dismissDrag + pan.y).coerceAtLeast(0f)
                                if (newDrag != dismissDrag) {
                                    dismissDrag = newDrag
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Gesture ended — commit dismiss or snap back
                        if (dismissDrag > dismissThresholdPx) {
                            onDismiss()
                        } else {
                            dismissDrag = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y + dismissDrag,
                    alpha = 1f - dismissProgress * 0.7f,
                )

            if (deckHasArt) {
                val context = LocalContext.current
                val request = remember(card.id, deck.id, context) {
                    ImageRequest.Builder(context)
                        .data(DeckAssetResolver.fileUri(deck, card))
                        .crossfade(false)
                        .memoryCacheKey("arcana-${deck.id}-${card.id}-full")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = transformModifier,
                )
            } else {
                Box(
                    modifier = transformModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = card.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        }
    }
}
