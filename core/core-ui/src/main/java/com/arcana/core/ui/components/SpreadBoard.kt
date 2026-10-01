package com.arcana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.arcana.core.domain.model.Card
import com.arcana.core.domain.model.DeckArt
import com.arcana.core.domain.model.DrawnCard
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.Spread
import com.arcana.core.ui.layout.SpreadFit
import com.arcana.core.ui.util.scaleToFitWords
import com.arcana.core.ui.util.scaledBy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A spread laid out on the screen. Cards are drawn as large as the width allows without touching
 * each other, with each position's name underneath when there is room for it. The board takes the
 * full width it is given and only the height it needs, up to [maxHeight].
 *
 * Pass [drawnCards] as null to show the layout face down.
 */
@Composable
fun SpreadBoard(
    spread: Spread,
    deck: DeckArt,
    drawnCards: List<DrawnCard>?,
    modifier: Modifier = Modifier,
    onCardClick: ((Card, Int) -> Unit)? = null,
    /** Name each position under its card when that does not cost the cards much of their size. */
    showLabels: Boolean = true,
    showPositionNumbers: Boolean = true,
    /** The tallest the board may grow. Unset, it uses the height it is offered, or 1.5 times its width. */
    maxHeight: Dp = Dp.Unspecified,
    /**
     * A height the board keeps within when that costs the cards little of their size: usually
     * the screen, so that a spread only just too tall for it does not need scrolling.
     */
    preferredHeight: Dp = Dp.Unspecified,
    maxCardWidth: Dp = 240.dp,
) {
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val ceiling = when {
            maxHeight != Dp.Unspecified -> with(density) { maxHeight.toPx() }
            constraints.hasBoundedHeight -> constraints.maxHeight.toFloat()
            else -> widthPx * 1.5f
        }
        val preferred = if (preferredHeight != Dp.Unspecified) with(density) { preferredHeight.toPx() } else ceiling
        val plan = remember(spread.positions, widthPx, ceiling, preferred, showLabels, maxCardWidth, density.density, density.fontScale, labelStyle) {
            val stacks = SpreadFit.stacks(spread.positions.map { it.coords.x to it.coords.y })
                .map { indexes -> indexes.map { spread.positions[it] } }
            val labels = stacks.map { stack -> stack.joinToString(" / ") { it.label } }
            val padding = with(density) { LABEL_PADDING.toPx() }
            fun solveWithin(height: Float, style: TextStyle?): SpreadFit.Result = with(density) {
                SpreadFit.fit(
                    slots = stacks.mapIndexed { i, stack ->
                        SpreadFit.Slot(
                            x = stack[0].coords.x,
                            y = stack[0].coords.y,
                            rotations = stack.map { it.coords.rotationDegrees },
                            labelWidth = if (style != null) measurer.measure(labels[i], style, maxLines = 1).size.width + padding * 2 else 0f,
                        )
                    },
                    spec = SpreadFit.Spec(
                        width = widthPx,
                        maxHeight = height,
                        aspect = CARD_ASPECT,
                        minCard = 40.dp.toPx(),
                        maxCard = maxCardWidth.toPx(),
                        gap = 8.dp.toPx(),
                        padding = 8.dp.toPx(),
                        labelLineHeight = if (style != null) measurer.measure("Ag", style).size.height.toFloat() else 0f,
                        labelGap = 4.dp.toPx(),
                    ),
                )
            }
            fun solve(style: TextStyle?): SpreadFit.Result {
                val tall = solveWithin(ceiling, style)
                if (preferred >= ceiling || tall.height <= preferred) return tall
                val short = solveWithin(preferred, style)
                return if (short.fits && short.cardWidth >= tall.cardWidth * PREFERRED_WORTH) short else tall
            }
            val bare = solve(null)
            var style = labelStyle
            var named = if (showLabels) solve(style) else null
            if (named != null) {
                // Small cards get smaller lettering, down to a point, so that a name like
                // "Environment" is not split across two lines.
                val fit = labels.indices.minOf { i -> measurer.scaleToFitWords(labels[i], style, named!!.slots[i].boxWidth - padding * 2) }
                if (fit < 1f) {
                    style = style.scaledBy((fit * 0.98f).coerceAtLeast(SMALLEST_LABEL))
                    named = solve(style)
                }
            }
            // Labels are worth a little card size, not a lot of it.
            val result = if (named != null && named.fits && named.cardWidth >= bare.cardWidth * LABEL_WORTH) named else bare
            BoardPlan(stacks, labels, result, named = result === named, labelStyle = style)
        }

        val result = plan.result
        Box(modifier = Modifier.fillMaxWidth().height(with(density) { result.height.toDp() })) {
            val cardW = with(density) { result.cardWidth.toDp() }
            val cardH = with(density) { result.cardHeight.toDp() }
            val badge = (cardW * 0.26f).coerceIn(16.dp, 24.dp)
            plan.stacks.forEachIndexed { i, stack ->
                val placed = result.slots[i]
                val cx = with(density) { placed.centerX.toDp() }
                val cy = with(density) { placed.centerY.toDp() }
                stack.forEach { position ->
                    val drawn = drawnCards?.firstOrNull { it.positionIndex == position.index }
                    Box(
                        modifier = Modifier
                            .offset(x = cx - cardW / 2, y = cy - cardH / 2)
                            .size(cardW, cardH)
                            .zIndex(position.index.toFloat())
                            .graphicsLayer { rotationZ = position.coords.rotationDegrees },
                    ) {
                        if (drawn != null) {
                            TarotCardView(
                                card = drawn.card,
                                deck = deck,
                                orientation = drawn.orientation,
                                onClick = onCardClick?.let { click -> { click(drawn.card, position.index) } },
                                reversedBadgeSize = badge,
                            )
                        } else {
                            CardBackView(deck = deck)
                        }
                    }
                    // One card needs no number.
                    if (showPositionNumbers && spread.positions.size > 1) {
                        // The number sits on the top right corner of the card as it lies, so it
                        // stays upright and readable on a card that is turned.
                        val r = Math.toRadians(position.coords.rotationDegrees.toDouble())
                        val reachX = cardW * abs(cos(r)).toFloat() / 2 + cardH * abs(sin(r)).toFloat() / 2
                        val reachY = cardW * abs(sin(r)).toFloat() / 2 + cardH * abs(cos(r)).toFloat() / 2
                        PositionNumberBadge(
                            index = position.index,
                            size = badge,
                            modifier = Modifier
                                .offset(x = cx + reachX - badge - 3.dp, y = cy - reachY + 3.dp)
                                .zIndex(NUMBER_Z + position.index),
                        )
                    }
                }
                if (plan.named && placed.labelLines > 0) {
                    val boxW = with(density) { placed.boxWidth.toDp() }
                    Text(
                        text = plan.labels[i],
                        style = plan.labelStyle,
                        textAlign = TextAlign.Center,
                        maxLines = placed.labelLines,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .offset(x = cx - boxW / 2, y = with(density) { placed.labelTop.toDp() })
                            .width(boxW)
                            .padding(horizontal = LABEL_PADDING),
                    )
                }
            }
        }
    }
}

private class BoardPlan(
    val stacks: List<List<Position>>,
    val labels: List<String>,
    val result: SpreadFit.Result,
    val named: Boolean,
    val labelStyle: TextStyle,
)

@Composable
private fun PositionNumberBadge(index: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                // Sized from the badge, not the user's font scale: it has to fit the circle.
                fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() },
                lineHeight = with(LocalDensity.current) { (size * 0.6f).toSp() },
                letterSpacing = 0.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private val LABEL_PADDING = 2.dp
private const val LABEL_WORTH = 0.8f
private const val SMALLEST_LABEL = 0.75f
private const val PREFERRED_WORTH = 0.85f
private const val NUMBER_Z = 5_000f
