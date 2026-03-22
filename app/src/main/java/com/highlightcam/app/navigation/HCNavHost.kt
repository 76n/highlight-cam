package com.highlightcam.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.highlightcam.app.ui.library.LibraryScreen
import com.highlightcam.app.ui.recording.RecordingScreen
import com.highlightcam.app.ui.settings.SettingsScreen
import com.highlightcam.app.ui.setup.SetupScreen

object Routes {
    const val SETUP = "setup"
    const val RECORDING = "recording"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

private const val TRANSITION_MS = 300

@Composable
fun HCNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val offsetPx = with(density) { 60.dp.roundToPx() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { offsetPx } +
                fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { -offsetPx } +
                fadeOut(tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { -offsetPx } +
                fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { offsetPx } +
                fadeOut(tween(TRANSITION_MS))
        },
    ) {
        composable(Routes.SETUP) { SetupScreen(navController) }
        composable(Routes.RECORDING) { RecordingScreen(navController) }
        composable(Routes.LIBRARY) { LibraryScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}
