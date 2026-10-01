package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.arcana.core.domain.model.Arcana
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.Orientation
import com.arcana.core.ui.theme.ArcanaColors
import com.arcana.core.ui.theme.CardShapes
import com.arcana.core.ui.util.deckArtIndex
import com.arcana.core.ui.util.scaleToFitWords
import com.arcana.core.ui.util.scaledBy

/**
 * A face-up tarot card, turned upside down when reversed. A card the deck has no image for shows
 * its name on a plain plate instead.
 */
@Composable
fun TarotCardView(
    card: Card,
    deck: DeckArt,
    orientation: Orientation = Orientation.UPRIGHT,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showLabel: Boolean = false,
    /** The red arrow badge on a reversed card. Smaller on small cards so it leaves the art visible. */
    reversedBadgeSize: Dp = 26.dp,
) {
    val index = deckArtIndex()
    val version = index.version
    val uri = remember(deck.id, card.imageRef, version) { index.cardUri(deck, card.imageRef) }
    // A file that is listed but will not decode falls back to the plate too.
    var broken by remember(uri) { mutableStateOf(false) }
    val isReversed = orientation == Orientation.REVERSED
    var tapCounter by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .aspectRatio(CARD_ASPECT)
            .shadow(8.dp, CardShapes.tarotCard)
            .clip(CardShapes.tarotCard)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // The outline is on the outer box, so it does not turn with the art.
            .border(
                width = if (isReversed) 2.5.dp else 1.dp,
                color = if (isReversed) ArcanaColors.ReversedRibbon else MaterialTheme.colorScheme.outline,
                shape = CardShapes.tarotCard,
            )
            .let {
                if (onClick != null) it.clickable {
                    tapCounter++
                    onClick()
                } else it
            },
        contentAlignment = Alignment.Center,
    ) {
        // No layer for an upright card: one per card adds up in a 78-card grid.
        val artModifier = Modifier
            .fillMaxSize()
            .let { if (isReversed) it.graphicsLayer { rotationZ = 180f } else it }

        Box(modifier = artModifier) {
            if (uri != null && !broken) {
                val context = LocalContext.current
                val request = remember(uri, version, context) {
                    ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(false)
                        .memoryCacheKey("arcana-${deck.id}-${card.id}-$version")
                        // Already a local file: a second copy in Coil's disk cache is wasted IO.
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .allowRgb565(true)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    onError = { broken = true },
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
            ReversedBadge(reversedBadgeSize)
        }
        if (tapCounter > 0) {
            CardSparkleEmitter(triggerKey = tapCounter)
        }
    }
}

/** What a card shows when the deck has no picture for it: its number, if it has one, and its name. */
@Composable
private fun CardArtFallback(card: Card) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val inset = maxWidth * 0.08f
        val measurer = rememberTextMeasurer()
        val room = with(LocalDensity.current) { (maxWidth - inset * 2).toPx() }
        val base = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        // Lettering sized to the card, so a long name is never broken mid-word on a small one.
        val nameStyle = remember(card.name, room, base) { base.scaledBy(measurer.scaleToFitWords(card.name, base, room)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(inset)) {
            (card.arcana as? Arcana.Major)?.let {
                Text(
                    text = it.displayLabel,
                    style = nameStyle.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = card.name,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
private fun ReversedBadge(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(size * 0.2f),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(ArcanaColors.ReversedRibbon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Reversed",
                tint = Color.White,
                modifier = Modifier.size(size * 0.7f),
            )
        }
    }
}

const val CARD_ASPECT: Float = 0.62f
