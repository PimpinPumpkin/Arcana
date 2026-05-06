package com.arcana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.app.navigation.ArcanaNavHost
import com.arcana.core.data.repository.DeckArtCatalog
import com.arcana.core.ui.theme.ArcanaTheme
import com.arcana.core.ui.util.DeckAssetAvailability
import com.arcana.core.ui.util.LocalDeckHasArt
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deckAvailability = DeckAssetAvailability(applicationContext)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            // Pre-resolve and cache art availability for every known deck once,
            // so card composables can do an O(1) lookup without re-scanning assets.
            val hasArtLookup: (String) -> Boolean = remember(deckAvailability) {
                val byId = DeckArtCatalog.ALL.associate { it.id to deckAvailability.hasArt(it) }
                val fn: (String) -> Boolean = { id -> byId[id] == true }
                fn
            }
            ArcanaTheme(
                preset = appearance.preset,
                themeMode = appearance.themeMode,
                useDynamicColor = appearance.useDynamicColor,
            ) {
                CompositionLocalProvider(LocalDeckHasArt provides hasArtLookup) {
                    ArcanaNavHost()
                }
            }
        }
    }
}
