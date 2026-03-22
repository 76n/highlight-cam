package com.highlightcam.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

val WindowInsets.Companion.safeBottomPadding: Dp
    @Composable
    get() =
        with(LocalDensity.current) {
            navigationBars.getBottom(this).toDp()
        }
