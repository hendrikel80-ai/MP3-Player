package de.mp3player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mp3player.R

/**
 * Design „Pocket Studio – Sunburst Stage“:
 * fast schwarzer Grund, warme braune Panels, Gold und Sunburst-Orange,
 * Bebas Neue für Überschriften, Source Sans 3 für Text, kantige 4px-Ecken.
 */
object Studio {
    val Bg = Color(0xFF0D0B09)
    val Panel = Color(0xFF1C150F)
    val PanelAlt = Color(0xFF251A10)
    val Border = Color(0xFF40311F)
    val Gold = Color(0xFFC9A227)
    val GoldTint = Color(0xFF342912) // Gold 14 % auf Panel – für Chips/Tonal-Buttons
    val Sunburst = Color(0xFFD35B2C)
    val SunburstHi = Color(0xFFE97C46)
    val Cream = Color(0xFFF5E9CC)
    val Text = Color(0xFFECE1C9)
    val TextDim = Color(0xFFAB9670)
    val OnAccent = Color(0xFF1A0D05)
    val Red = Color(0xFFE0483A)
}

private val StudioColors = darkColorScheme(
    primary = Studio.Sunburst,
    onPrimary = Studio.OnAccent,
    primaryContainer = Studio.PanelAlt,
    onPrimaryContainer = Studio.SunburstHi,
    inversePrimary = Studio.SunburstHi,
    secondary = Studio.Gold,
    onSecondary = Studio.OnAccent,
    secondaryContainer = Studio.GoldTint,
    onSecondaryContainer = Studio.Gold,
    tertiary = Studio.SunburstHi,
    onTertiary = Studio.OnAccent,
    tertiaryContainer = Studio.PanelAlt,
    onTertiaryContainer = Studio.Cream,
    background = Studio.Bg,
    onBackground = Studio.Text,
    surface = Studio.Bg,
    onSurface = Studio.Text,
    surfaceVariant = Studio.PanelAlt,
    onSurfaceVariant = Studio.TextDim,
    surfaceTint = Color.Transparent,
    surfaceBright = Studio.PanelAlt,
    surfaceDim = Studio.Bg,
    surfaceContainerLowest = Studio.Bg,
    surfaceContainerLow = Studio.Panel,
    surfaceContainer = Studio.Panel,
    surfaceContainerHigh = Studio.Panel,
    surfaceContainerHighest = Studio.PanelAlt,
    inverseSurface = Studio.Cream,
    inverseOnSurface = Studio.Bg,
    outline = Studio.Border,
    outlineVariant = Studio.Border,
    scrim = Color(0xCC000000),
    error = Studio.Red,
    onError = Studio.OnAccent,
)

val BebasNeue = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

val SourceSans = FontFamily(
    Font(R.font.source_sans_regular, FontWeight.Normal),
    Font(R.font.source_sans_semibold, FontWeight.SemiBold),
    Font(R.font.source_sans_bold, FontWeight.Bold),
)

private fun display(size: Int, spacing: Double = 0.03) = TextStyle(
    fontFamily = BebasNeue,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * 1.1).sp,
    letterSpacing = (size * spacing).sp,
    color = Studio.Cream,
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Double = 1.4) = TextStyle(
    fontFamily = SourceSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
)

/** Kleine Überschrift über Bereichen, z. B. „LÄUFT GERADE“. */
val EyebrowStyle = TextStyle(
    fontFamily = BebasNeue,
    fontSize = 18.sp,
    letterSpacing = 1.8.sp,
    color = Studio.Gold,
)

private val StudioTypography = Typography(
    displayLarge = display(52),
    displayMedium = display(44),
    displaySmall = display(36),
    headlineLarge = display(32),
    headlineMedium = display(28),
    headlineSmall = display(26),
    titleLarge = display(24),
    titleMedium = body(16, FontWeight.Bold),
    titleSmall = body(14, FontWeight.Bold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(12),
    labelLarge = body(14, FontWeight.Bold),
    labelMedium = body(12, FontWeight.SemiBold),
    labelSmall = body(11, FontWeight.SemiBold),
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun Mp3PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
