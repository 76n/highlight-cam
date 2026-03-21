package com.highlightcam.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricGreen,
    onPrimary = DeepBlack,
    primaryContainer = ElectricGreen.copy(alpha = 0.15f),
    onPrimaryContainer = ElectricGreen,
    secondary = MutedAmber,
    onSecondary = DeepBlack,
    secondaryContainer = MutedAmber.copy(alpha = 0.15f),
    onSecondaryContainer = MutedAmber,
    error = VividRed,
    onError = PureWhite,
    errorContainer = VividRed.copy(alpha = 0.15f),
    onErrorContainer = VividRed,
    background = DeepBlack,
    onBackground = PureWhite,
    surface = SurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineDark,
)

@Composable
fun HighlightCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
