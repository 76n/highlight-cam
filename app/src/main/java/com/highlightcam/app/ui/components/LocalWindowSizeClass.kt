package com.highlightcam.app.ui.components

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWindowSizeClass =
    staticCompositionLocalOf<WindowSizeClass> {
        error("No WindowSizeClass provided")
    }
