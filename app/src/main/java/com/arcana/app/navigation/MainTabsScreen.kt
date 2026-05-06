package com.arcana.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arcana.feature.journal.JournalScreen
import com.arcana.feature.library.LibraryScreen
import com.arcana.feature.settings.SettingsScreen
import com.arcana.feature.spreads.SpreadPickerScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun MainTabsScreen(
    onCardClick: (cardId: String) -> Unit,
    onSpreadPicked: (spreadId: String) -> Unit,
    onJournalEntryClick: (readingId: String) -> Unit,
    onLogPhysicalReading: () -> Unit,
    onCreateCustomSpread: () -> Unit,
    onEditCustomSpread: (spreadId: String) -> Unit,
) {
    val tabs = listOf(
        Tab(Routes.LIBRARY, "Library", Icons.Default.MenuBook),
        Tab(Routes.SPREADS, "Spreads", Icons.Default.AutoAwesome),
        Tab(Routes.JOURNAL, "Journal", Icons.Default.Book),
        Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )

    val tabsNav = rememberNavController()
    val currentBackStack by tabsNav.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentRoute?.let { it == tab.route || it.startsWith(tab.route) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabsNav.navigate(tab.route) {
                                popUpTo(tabsNav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabsNav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            composable(Routes.LIBRARY) { LibraryScreen(onCardClick = onCardClick) }
            composable(Routes.SPREADS) {
                SpreadPickerScreen(
                    onPickSpread = onSpreadPicked,
                    onCreateCustom = onCreateCustomSpread,
                    onEditCustom = onEditCustomSpread,
                )
            }
            composable(Routes.JOURNAL) {
                JournalScreen(
                    onReadingClick = onJournalEntryClick,
                    onLogPhysicalReading = onLogPhysicalReading,
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
