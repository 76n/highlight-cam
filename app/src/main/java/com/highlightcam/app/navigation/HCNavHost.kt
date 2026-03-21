package com.highlightcam.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.highlightcam.app.ui.recording.RecordingScreen
import com.highlightcam.app.ui.setup.SetupScreen

object Routes {
    const val SETUP = "setup"
    const val RECORDING = "recording"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

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
    ) {
        composable(Routes.SETUP) { SetupScreen(navController) }
        composable(Routes.RECORDING) { RecordingScreen(navController) }
        composable(Routes.LIBRARY) { LibraryStub(navController) }
        composable(Routes.SETTINGS) { SettingsStub(navController) }
    }
}

@Composable
private fun LibraryStub(navController: NavController) {
    StubScreen(name = "Library") {
        NavLink("← Back") {
            navController.popBackStack()
        }
    }
}

@Composable
private fun SettingsStub(navController: NavController) {
    StubScreen(name = "Settings") {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            NavLink("← Back") {
                navController.popBackStack()
            }
            NavLink("Reconfigure Zone") {
                navController.navigate(Routes.SETUP) {
                    popUpTo(Routes.RECORDING) { inclusive = false }
                }
            }
        }
    }
}

@Composable
private fun StubScreen(
    name: String,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(32.dp))
            actions()
        }
    }
}

@Composable
private fun NavLink(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
    )
}
