package com.highlightcam.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.LifecycleOwner

val LocalActivityLifecycleOwner =
    staticCompositionLocalOf<LifecycleOwner> {
        error("No ActivityLifecycleOwner provided")
    }
