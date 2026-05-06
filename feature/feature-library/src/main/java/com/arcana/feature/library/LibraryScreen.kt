package com.arcana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.core.domain.model.Card
import com.arcana.feature.library.components.CardGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onCardClick: (cardId: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.library_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
            )
            FilterRow(
                selected = ui.filter,
                onSelect = viewModel::onFilterChanged,
            )
            if (ui.cards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (ui.isLoading) "Loading deck…" else "No cards match",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.cards, key = { it.id }) { card: Card ->
                        CardGridItem(
                            card = card,
                            deck = ui.deck,
                            onClick = { onCardClick(card.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: CardFilter, onSelect: (CardFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterEntry(stringResource(R.string.library_filter_all), selected == CardFilter.ALL) { onSelect(CardFilter.ALL) }
        FilterEntry(stringResource(R.string.library_filter_major), selected == CardFilter.MAJOR) { onSelect(CardFilter.MAJOR) }
        FilterEntry(stringResource(R.string.library_filter_wands), selected == CardFilter.WANDS) { onSelect(CardFilter.WANDS) }
        FilterEntry(stringResource(R.string.library_filter_cups), selected == CardFilter.CUPS) { onSelect(CardFilter.CUPS) }
        FilterEntry(stringResource(R.string.library_filter_swords), selected == CardFilter.SWORDS) { onSelect(CardFilter.SWORDS) }
        FilterEntry(stringResource(R.string.library_filter_pentacles), selected == CardFilter.PENTACLES) { onSelect(CardFilter.PENTACLES) }
    }
}

@Composable
private fun FilterEntry(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
