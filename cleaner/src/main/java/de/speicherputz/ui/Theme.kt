package de.speicherputz.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0F1416)
    val Panel = Color(0xFF172024)
    val PanelHi = Color(0xFF1F2B30)
    val Mint = Color(0xFF5EE0B5)
    val MintDim = Color(0xFF1D3F37)
    val Amber = Color(0xFFF2B84B)
    val AmberDim = Color(0xFF433419)
    val Red = Color(0xFFFF6B6B)
    val RedDim = Color(0xFF4A2224)
    val Text = Color(0xFFE6EEF0)
    val TextDim = Color(0xFF9DB0B5)
}

private val Colors = darkColorScheme(
    primary = Palette.Mint,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Palette.MintDim,
    onPrimaryContainer = Palette.Mint,
    secondary = Palette.Amber,
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Palette.AmberDim,
    onSecondaryContainer = Palette.Amber,
    error = Palette.Red,
    onError = Color(0xFF410002),
    errorContainer = Palette.RedDim,
    onErrorContainer = Palette.Red,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Panel,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHi,
    surfaceContainerHighest = Palette.PanelHi,
    outline = Color(0xFF3A4A50),
    outlineVariant = Color(0xFF2A373C),
)

@Composable
fun SpeicherputzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
