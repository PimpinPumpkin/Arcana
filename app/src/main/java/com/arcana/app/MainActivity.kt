package com.arcana.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcana.app.navigation.ArcanaNavHost
import com.arcana.core.domain.model.ThemeMode
import com.arcana.core.ui.theme.ArcanaTheme
import com.arcana.core.ui.util.DeckArtIndex
import com.arcana.core.ui.util.LocalDeckArtIndex
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            val deckArt = remember { DeckArtIndex(applicationContext) }
            val deckVersion by appViewModel.deckVersion.collectAsStateWithLifecycle()
            LaunchedEffect(deckVersion) { if (deckVersion > 0) deckArt.invalidate() }

            // The status and navigation bar icons follow the app's theme, which can be dark on a
            // phone set to light, or the other way round.
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            ArcanaTheme(
                preset = appearance.preset,
                themeMode = appearance.themeMode,
            ) {
                CompositionLocalProvider(LocalDeckArtIndex provides deckArt) {
                    ArcanaNavHost()
                }
            }
        }
    }
}
