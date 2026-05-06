package com.arcana.feature.spreads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Position
import com.arcana.core.domain.model.Spread
import com.arcana.core.ui.components.SpreadBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadOverviewScreen(
    onBack: () -> Unit,
    onPullDigital: (spreadId: String) -> Unit,
    onLogPhysical: (spreadId: String) -> Unit,
    viewModel: SpreadOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spread = state.spread
    val deck = state.deck

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spread?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (spread == null || deck == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.isLoading) "Loading…" else "Spread not found")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(spread.description, style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${spread.cardCount} cards") },
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(spread.difficulty.displayName) },
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(boardHeightFor(spread))
                        .padding(horizontal = 8.dp),
                ) {
                    SpreadBoard(
                        spread = spread,
                        deck = deck,
                        drawnCards = null, // face-down, just showing the layout
                        showLabels = false,
                        showPositionNumbers = true,
                    )
                }
            }

            item {
                Text(
                    text = "How to lay out this spread",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(spread.positions, key = { it.index }) { pos ->
                PositionRow(pos)
            }

            item {
                Text(
                    text = "Practice tip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = practiceTipFor(spread),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onLogPhysical(spread.id) },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .fillMaxWidth(0.5f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Log physical")
                    }
                    Button(
                        onClick = { onPullDigital(spread.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Pull digitally")
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionRow(position: Position) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = position.index.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(position.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                position.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun boardHeightFor(spread: Spread): androidx.compose.ui.unit.Dp = when (spread.id) {
    "celtic_cross", "year_ahead" -> 480.dp
    "horseshoe" -> 380.dp
    "relationship_5" -> 360.dp
    else -> 280.dp
}

private fun practiceTipFor(spread: Spread): String = when (spread.id) {
    "celtic_cross" -> "With your physical deck: shuffle thoroughly, cut the deck three times, then lay out the first six cards in a cross pattern (1 over 2, 3 below, 4 left, 5 above, 6 right). The remaining four go in a vertical column to the right, bottom to top."
    "horseshoe" -> "Shuffle, cut three times, then lay seven cards in an arc from lower-left to lower-right, with the apex at the top center."
    "year_ahead" -> "Shuffle thoroughly. Lay 12 cards clockwise around an imaginary circle, starting at the top (Month 1 / January if doing a calendar reading)."
    "relationship_5" -> "Shuffle while focusing on the connection. Lay cards 1–3 in a horizontal row (you, the bond, them), then 4 and 5 above as Strengths and Tensions."
    "three_card_ppf", "three_card_sao", "three_card_mbs" -> "Shuffle and cut. Lay three cards left-to-right in the order shown above."
    "one_card" -> "Shuffle slowly while holding your question. Cut the deck and draw the top card."
    else -> "Shuffle while focusing on your question, cut the deck, then lay each card in the position shown above."
}
