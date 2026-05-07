package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.Spread
import kotlin.math.min

/**
 * Renders a spread by placing each card at its normalized [0..1] coordinate within
 * the bounding box. Pass [drawnCards]=null to render face-down placeholders.
 *
 * Uses [Modifier.offset] (signed) rather than [Modifier.padding] (which crashes on
 * negative values) so spreads with positions near the top or left edge — Horseshoe,
 * Year Wheel — don't blow up on container shapes that aren't square.
 */
@Composable
fun SpreadBoard(
    spread: Spread,
    deck: DeckArt,
    drawnCards: List<DrawnCard>?,
    modifier: Modifier = Modifier,
    onCardClick: ((Card, Int) -> Unit)? = null,
    /** Show the position's full label under each card. Off by default — looks cluttered on dense spreads. */
    showLabels: Boolean = false,
    /** Show the position number badge in the corner of each card. */
    showPositionNumbers: Boolean = true,
    /** Card width as a fraction of the smaller of board width/height — keeps cards consistent on any aspect. */
    cardSizeFraction: Float = 0.20f,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        val parentW = maxWidth
        val parentH = maxHeight
        // Size cards based on the smaller dimension so they always fit.
        val baseDp = min(parentW.value, parentH.value).dp
        val cardWidth = baseDp * cardSizeFraction
        val cardHeight = cardWidth / CARD_ASPECT
        spread.positions.forEach { position ->
            val drawn = drawnCards?.firstOrNull { it.positionIndex == position.index }
            PositionedCard(
                position = position,
                drawn = drawn,
                deck = deck,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                parentWidth = parentW,
                parentHeight = parentH,
                onClick = if (drawn != null && onCardClick != null) {
                    { onCardClick(drawn.card, position.index) }
                } else null,
                showLabel = showLabels,
                showNumber = showPositionNumbers,
            )
        }
    }
}

@Composable
private fun PositionedCard(
    position: Position,
    drawn: DrawnCard?,
    deck: DeckArt,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    parentWidth: androidx.compose.ui.unit.Dp,
    parentHeight: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?,
    showLabel: Boolean,
    showNumber: Boolean,
) {
    val centerX = parentWidth * position.coords.x
    val centerY = parentHeight * position.coords.y
    val offsetX = centerX - cardWidth / 2
    val offsetY = centerY - cardHeight / 2

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .width(cardWidth)
            .zIndex(position.index.toFloat()),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Stack the rotated card art with an UNROTATED badge layer so
            // the position number stays readable when the card is at 90°/180°
            // and doesn't sit on top of the corner reversed-arrow badge
            // (which lives at TopStart inside TarotCardView).
            Box(modifier = Modifier.width(cardWidth)) {
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .graphicsLayer { rotationZ = position.coords.rotationDegrees },
                ) {
                    if (drawn != null) {
                        TarotCardView(
                            card = drawn.card,
                            deck = deck,
                            orientation = drawn.orientation,
                            onClick = onClick,
                        )
                    } else {
                        CardBackView()
                    }
                }
                if (showNumber) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        PositionNumberBadge(position.index)
                    }
                }
            }
            if (showLabel) {
                Text(
                    text = position.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PositionNumberBadge(index: Int) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(22.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
