package com.highlightcam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = HC.green,
        onPrimary = HC.onGreen,
        primaryContainer = HC.greenDim,
        onPrimaryContainer = HC.green,
        secondary = HC.amber,
        onSecondary = HC.bg,
        secondaryContainer = HC.amberDim,
        onSecondaryContainer = HC.amber,
        error = HC.red,
        onError = HC.white,
        errorContainer = HC.redDim,
        onErrorContainer = HC.red,
        background = HC.bg,
        onBackground = HC.white,
        surface = HC.surface,
        onSurface = HC.white,
        surfaceVariant = HC.surfaceRaised,
        onSurfaceVariant = HC.white60,
        outline = HC.white20,
    )

@Composable
fun HighlightCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
