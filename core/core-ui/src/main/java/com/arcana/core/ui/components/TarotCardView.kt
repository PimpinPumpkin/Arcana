package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Orientation
import com.arcana.core.ui.theme.ArcanaColors
import com.arcana.core.ui.theme.CardShapes
import com.arcana.core.ui.util.DeckAssetResolver
import com.arcana.core.ui.util.LocalDeckHasArt
import androidx.compose.ui.platform.LocalContext

/**
 * A face-up tarot card with optional reversed rotation.
 * Falls back to a styled name plate if the deck art is missing.
 *
 * Perf: when the surrounding deck has no bundled art (the default before the
 * user drops in scans), this composable skips Coil entirely and just renders
 * the static fallback. Without this, every card item in a 78-card grid spins up
 * a new image request that fails — ruinous for scrolling perf.
 */
@Composable
fun TarotCardView(
    card: Card,
    deck: DeckArt,
    orientation: Orientation = Orientation.UPRIGHT,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showLabel: Boolean = false,
) {
    val deckHasArt = LocalDeckHasArt.current(deck.id)
    val isReversed = orientation == Orientation.REVERSED

    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .shadow(8.dp, CardShapes.tarotCard)
            .clip(CardShapes.tarotCard)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, CardShapes.tarotCard)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        // Rotate the inner content (the art / fallback) when reversed.
        // Avoid graphicsLayer when upright so we don't allocate a layer per item.
        val artModifier = Modifier
            .fillMaxSize()
            .let { if (isReversed) it.graphicsLayer { rotationZ = 180f } else it }

        Box(modifier = artModifier) {
            if (deckHasArt) {
                val context = LocalContext.current
                // Memoize the ImageRequest by card+deck so we don't allocate a fresh
                // builder on every recomposition during scroll.
                val request = remember(card.id, deck.id, context) {
                    ImageRequest.Builder(context)
                        .data(DeckAssetResolver.fileUri(deck, card))
                        .crossfade(false)
                        .memoryCacheKey("arcana-${deck.id}-${card.id}")
                        // The asset already lives on disk inside the APK — Coil
                        // disk-caching it again is redundant IO that can stutter
                        // first-frame.
                        .diskCachePolicy(CachePolicy.DISABLED)
                        // Tarot art tolerates RGB_565 fine; halves bitmap memory
                        // and decode cost.
                        .allowRgb565(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CardArtFallback(card = card)
            }
        }
        if (showLabel) {
            CardNamePlate(name = card.name)
        }
        if (isReversed) {
            ReversedRibbon()
        }
    }
}

@Composable
private fun CardArtFallback(card: Card) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.arcana.displayLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CardNamePlate(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReversedRibbon() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(ArcanaColors.ReversedRibbon)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "Reversed",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

const val CARD_ASPECT: Float = 0.62f
