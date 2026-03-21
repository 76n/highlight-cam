package com.highlightcam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.compose.rememberNavController
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.navigation.HCNavHost
import com.highlightcam.app.navigation.Routes
import com.highlightcam.app.ui.theme.HighlightCamTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HighlightCamTheme {
                val startRoute by produceState<String?>(initialValue = null) {
                    val zone = userPreferencesRepository.goalZone.first()
                    value = if (zone == null) Routes.SETUP else Routes.RECORDING
                }
                startRoute?.let { route ->
                    val navController = rememberNavController()
                    HCNavHost(
                        navController = navController,
                        startDestination = route,
                    )
                }
            }
        }
    }
}
