package com.example.royalcarromclassic.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RoyalClassicColors = darkColorScheme(
    primary = CarromPalette.Gold,
    onPrimary = CarromPalette.Ink,
    primaryContainer = CarromPalette.GoldDeep,
    onPrimaryContainer = CarromPalette.Ivory,
    secondary = CarromPalette.Parchment,
    onSecondary = CarromPalette.Ink,
    tertiary = CarromPalette.Crimson,
    onTertiary = CarromPalette.Ivory,
    background = CarromPalette.Night,
    onBackground = CarromPalette.Ivory,
    surface = CarromPalette.Mahogany,
    onSurface = CarromPalette.Ivory,
    surfaceVariant = CarromPalette.MahoganyRaised,
    onSurfaceVariant = CarromPalette.Parchment,
    outline = CarromPalette.Seam,
    error = CarromPalette.Crimson,
)

/**
 * Royal Carrom Classic theme. The game is always dark (a walnut room around a lit board);
 * system bar styling is configured once in MainActivity via edge-to-edge.
 */
@Composable
fun RoyalCarromClassicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RoyalClassicColors,
        typography = Typography,
        content = content,
    )
}
