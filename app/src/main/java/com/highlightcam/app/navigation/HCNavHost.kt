package com.highlightcam.app.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

private const val TRANSITION_MS = 350

@Composable
fun HCNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(animationSpec = tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { it } +
                fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 3 } +
                fadeOut(animationSpec = tween(TRANSITION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / 3 } +
                fadeIn(animationSpec = tween(TRANSITION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(TRANSITION_MS, easing = FastOutSlowInEasing)) { it } +
                fadeOut(animationSpec = tween(TRANSITION_MS))
        },
    ) {
        composable(Routes.SETUP) { SetupScreen(navController) }
        composable(Routes.RECORDING) { RecordingScreen(navController) }
        composable(Routes.LIBRARY) { LibraryScreen(navController) }
        composable(Routes.SETTINGS) { SettingsScreen(navController) }
    }
}
