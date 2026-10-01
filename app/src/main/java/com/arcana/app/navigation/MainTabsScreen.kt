package com.arcana.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.arcana.app.R
import com.arcana.app.WhatsNew
import com.arcana.app.WhatsNewSheet
import com.arcana.feature.journal.JournalScreen
import com.arcana.feature.library.LibraryScreen
import com.arcana.feature.settings.SettingsScreen
import com.arcana.feature.spreads.SpreadPickerScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

@Composable
fun MainTabsScreen(
    onCardClick: (cardId: String) -> Unit,
    onSpreadPicked: (spreadId: String) -> Unit,
    onJournalEntryClick: (readingId: String) -> Unit,
    onLogPhysicalReading: () -> Unit,
    onCreateCustomSpread: () -> Unit,
    onEditCustomSpread: (spreadId: String) -> Unit,
    onManageDecks: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    val tabs = listOf(
        Tab(Routes.LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.MenuBook),
        Tab(Routes.SPREADS, R.string.tab_spreads, Icons.Default.AutoAwesome),
        Tab(Routes.JOURNAL, R.string.tab_journal, Icons.Default.Book),
        Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Default.Settings),
    )

    val tabsNav = rememberNavController()
    val currentBackStack by tabsNav.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Shown by itself once after an update, and from Settings whenever it is asked for.
    val context = LocalContext.current
    val notes = remember { WhatsNew.notes(context) }
    var showNotes by rememberSaveable { mutableStateOf(WhatsNew.shouldShow(context)) }
    if (showNotes && notes != null) WhatsNewSheet(notes) { showNotes = false }

    Scaffold(
        // Each tab has a top bar of its own that clears the status bar. Taking the insets here
        // as well left a blank band above every title.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            tabsNav.navigate(tab.route) {
                                popUpTo(tabsNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabsNav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier
                .padding(padding)
                // The bar below already sits above the system navigation; tell the tabs so.
                .consumeWindowInsets(padding)
                .fillMaxSize(),
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
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManageDecks = onManageDecks,
                    onBackupRestore = onBackupRestore,
                    onWhatsNew = if (notes != null) ({ showNotes = true }) else null,
                )
            }
        }
    }
}
