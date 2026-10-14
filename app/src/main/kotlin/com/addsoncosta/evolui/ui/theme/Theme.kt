package com.addsoncosta.evolui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta do visual escuro esportivo. */
object Evolui {
    val Background = Color(0xFF0B0F0E)
    val Surface = Color(0xFF141A18)
    val SurfaceHigh = Color(0xFF1C2421)
    val Outline = Color(0xFF2A3430)
    val Neon = Color(0xFF39FF88)
    val NeonDim = Color(0xFF1F7A4A)
    val Cyan = Color(0xFF22D3EE)
    val Orange = Color(0xFFFF8A3D)
    val Purple = Color(0xFFB98CFF)
    val Red = Color(0xFFFF5C6C)
    val Text = Color(0xFFF2F5F3)
    val TextMuted = Color(0xFF9AA8A2)
}

private val Colors = darkColorScheme(
    primary = Evolui.Neon,
    onPrimary = Color(0xFF00210F),
    primaryContainer = Evolui.NeonDim,
    onPrimaryContainer = Evolui.Text,
    secondary = Evolui.Cyan,
    onSecondary = Color(0xFF00222A),
    secondaryContainer = Color(0xFF123A44),
    onSecondaryContainer = Evolui.Text,
    tertiary = Evolui.Orange,
    onTertiary = Color(0xFF2E1300),
    background = Evolui.Background,
    onBackground = Evolui.Text,
    surface = Evolui.Background,
    onSurface = Evolui.Text,
    surfaceVariant = Evolui.SurfaceHigh,
    onSurfaceVariant = Evolui.TextMuted,
    surfaceContainerLowest = Evolui.Background,
    surfaceContainerLow = Evolui.Surface,
    surfaceContainer = Evolui.Surface,
    surfaceContainerHigh = Evolui.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF26302C),
    outline = Evolui.Outline,
    outlineVariant = Evolui.Outline,
    error = Evolui.Red,
    onError = Color(0xFF2B0006),
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Base = Typography()
private val Type = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold),
)

@Composable
fun EvoluiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = Shapes, typography = Type, content = content)
}
