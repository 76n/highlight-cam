package com.highlightcam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

object Spacing {
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s20: Dp = 20.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s40: Dp = 40.dp
    val s48: Dp = 48.dp
    val s64: Dp = 64.dp
}

object Radii {
    val r8: Dp = 8.dp
    val r12: Dp = 12.dp
    val r16: Dp = 16.dp
    val r24: Dp = 24.dp
    val r100: Dp = 100.dp
}

@Composable
fun HighlightCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
