package de.schach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF7A5230),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF4E6A3A),
    secondaryContainer = Color(0xFFD0EDB9),
    tertiary = Color(0xFF3D6373),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0BC8C),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF603C1C),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFB4D19F),
    secondaryContainer = Color(0xFF374F25),
    tertiary = Color(0xFFA5CCDF),
)

@Composable
fun SchachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Brettfarben (klassisch Holz) */
object BoardColors {
    val light = Color(0xFFF0D9B5)
    val dark = Color(0xFFB58863)
    val lastMove = Color(0x66CDD26A)
    val selected = Color(0x8814551E)
    val hint = Color(0x883C8CE7)
    val check = Color(0xCCE53935)
    val target = Color(0x55000000)
}
