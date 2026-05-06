package com.arcana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Card
import com.arcana.core.ui.components.TarotCardView
import com.arcana.core.ui.theme.ArcanaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardId: String,
    onBack: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(cardId) { viewModel.load(cardId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.card?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val card = state.card
        if (card == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.isLoading) "Loading…" else "Card not found")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.width(220.dp),
            ) {
                state.deck?.let { TarotCardView(card = card, deck = it) }
            }
            Text(
                text = card.arcana.displayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = card.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            FactsRow(card = card)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionHeader(text = "Description")
            Text(text = card.description, style = MaterialTheme.typography.bodyLarge)

            SectionHeader(text = "Upright", color = ArcanaColors.UprightRibbon)
            KeywordRow(card.keywordsUpright)
            Text(text = card.uprightMeaning, style = MaterialTheme.typography.bodyLarge)

            SectionHeader(text = "Reversed", color = ArcanaColors.ReversedRibbon)
            KeywordRow(card.keywordsReversed)
            Text(text = card.reversedMeaning, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun FactsRow(card: Card) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        card.element?.let {
            AssistChip(onClick = {}, label = { Text(it.displayName) })
        }
        card.astrology?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
        card.numerology?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordRow(keywords: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keywords.forEach { keyword ->
            AssistChip(onClick = {}, label = { Text(keyword, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
