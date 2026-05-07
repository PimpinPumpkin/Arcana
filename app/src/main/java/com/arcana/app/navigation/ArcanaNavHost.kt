package com.arcana.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arcana.feature.journal.JournalDetailScreen
import com.arcana.feature.journal.LogPhysicalReadingScreen
import com.arcana.feature.library.CardDetailScreen
import com.arcana.feature.settings.BackupScreen
import com.arcana.feature.settings.EditDeckScreen
import com.arcana.feature.settings.ManageDecksScreen
import com.arcana.feature.spreads.CustomSpreadEditorScreen
import com.arcana.feature.spreads.ReadingFlowScreen
import com.arcana.feature.spreads.SpreadOverviewScreen
import com.arcana.feature.spreads.SpreadPickerScreen

@Composable
fun ArcanaNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = modifier,
    ) {
        composable(Routes.MAIN) {
            MainTabsScreen(
                onCardClick = { navController.navigate(Routes.cardDetail(it)) },
                onSpreadPicked = { navController.navigate(Routes.spreadOverview(it)) },
                onJournalEntryClick = { navController.navigate(Routes.journalDetail(it)) },
                onLogPhysicalReading = { navController.navigate(Routes.LOG_PHYSICAL_PICKER) },
                onCreateCustomSpread = { navController.navigate(Routes.CUSTOM_SPREAD_NEW) },
                onEditCustomSpread = { id -> navController.navigate(Routes.customSpreadEdit(id)) },
                onManageDecks = { navController.navigate(Routes.MANAGE_DECKS) },
                onBackupRestore = { navController.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MANAGE_DECKS) {
            ManageDecksScreen(
                onBack = { navController.popBackStack() },
                onEditDeck = { id -> navController.navigate(Routes.editDeck(id)) },
            )
        }
        composable(
            route = Routes.EDIT_DECK_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_DECK_ID) { type = NavType.StringType }),
        ) {
            EditDeckScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CUSTOM_SPREAD_NEW) {
            CustomSpreadEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CUSTOM_SPREAD_EDIT_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SPREAD_ID) { type = NavType.StringType }),
        ) {
            CustomSpreadEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CARD_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CARD_ID) { type = NavType.StringType }),
        ) { backStack ->
            val cardId = backStack.arguments?.getString(Routes.ARG_CARD_ID) ?: return@composable
            CardDetailScreen(
                cardId = cardId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SPREAD_OVERVIEW_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SPREAD_ID) { type = NavType.StringType }),
        ) {
            SpreadOverviewScreen(
                onBack = { navController.popBackStack() },
                onPullDigital = { spreadId ->
                    navController.navigate(Routes.readingFlow(spreadId))
                },
                onLogPhysical = { spreadId ->
                    navController.navigate(Routes.logPhysical(spreadId))
                },
            )
        }
        composable(
            route = Routes.READING_FLOW_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SPREAD_ID) { type = NavType.StringType }),
        ) {
            ReadingFlowScreen(
                onBack = { navController.popBackStack() },
                onCardClick = { navController.navigate(Routes.cardDetail(it)) },
                onSaved = { readingId ->
                    navController.navigate(Routes.journalDetail(readingId)) {
                        popUpTo(Routes.MAIN)
                    }
                },
            )
        }
        composable(Routes.LOG_PHYSICAL_PICKER) {
            // Reuse the spread picker, but tapping a spread goes straight to the
            // physical reading editor instead of the overview.
            SpreadPickerScreen(
                onPickSpread = { spreadId ->
                    navController.navigate(Routes.logPhysical(spreadId)) {
                        popUpTo(Routes.LOG_PHYSICAL_PICKER) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.LOG_PHYSICAL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SPREAD_ID) { type = NavType.StringType }),
        ) {
            LogPhysicalReadingScreen(
                onBack = { navController.popBackStack() },
                onSaved = { readingId ->
                    navController.navigate(Routes.journalDetail(readingId)) {
                        popUpTo(Routes.MAIN)
                    }
                },
            )
        }
        composable(
            route = Routes.JOURNAL_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_READING_ID) { type = NavType.StringType }),
        ) { backStack ->
            val readingId = backStack.arguments?.getString(Routes.ARG_READING_ID) ?: return@composable
            JournalDetailScreen(
                readingId = readingId,
                onBack = { navController.popBackStack() },
                onCardClick = { navController.navigate(Routes.cardDetail(it)) },
            )
        }
    }
}
