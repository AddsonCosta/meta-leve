package com.addsoncosta.evolui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uma combinação de cores do app. [accent] é a cor principal (o "neon" do tema esportivo);
 * [accent2], [accent3] e [accent4] são usadas em gráficos e destaques.
 */
data class Palette(
    val key: String,
    val label: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val accent2: Color,
    val accent3: Color,
    val accent4: Color,
    val danger: Color,
    val text: Color,
    val textMuted: Color,
)

/** Temas que o usuário pode escolher em Configurações. */
object Palettes {
    val Sport = Palette(
        key = "esportivo", label = "Escuro esportivo", dark = true,
        background = Color(0xFF0B0F0E), surface = Color(0xFF141A18), surfaceHigh = Color(0xFF1C2421), outline = Color(0xFF2A3430),
        accent = Color(0xFF39FF88), accentDim = Color(0xFF1F7A4A), onAccent = Color(0xFF00210F),
        accent2 = Color(0xFF22D3EE), accent3 = Color(0xFFFF8A3D), accent4 = Color(0xFFB98CFF),
        danger = Color(0xFFFF5C6C), text = Color(0xFFF2F5F3), textMuted = Color(0xFF9AA8A2),
    )
    val Ocean = Palette(
        key = "oceano", label = "Oceano", dark = true,
        background = Color(0xFF07111A), surface = Color(0xFF0F1C28), surfaceHigh = Color(0xFF162636), outline = Color(0xFF243647),
        accent = Color(0xFF38BDF8), accentDim = Color(0xFF0E5A80), onAccent = Color(0xFF00202F),
        accent2 = Color(0xFF34D399), accent3 = Color(0xFFFBBF24), accent4 = Color(0xFFA78BFA),
        danger = Color(0xFFF87171), text = Color(0xFFEFF6FB), textMuted = Color(0xFF94A7B8),
    )
    val Sunset = Palette(
        key = "por_do_sol", label = "Pôr do sol", dark = true,
        background = Color(0xFF140C0A), surface = Color(0xFF201512), surfaceHigh = Color(0xFF2B1D19), outline = Color(0xFF3B2A24),
        accent = Color(0xFFFF8A3D), accentDim = Color(0xFF8A3D12), onAccent = Color(0xFF2E1300),
        accent2 = Color(0xFFFFD166), accent3 = Color(0xFFFF5D8F), accent4 = Color(0xFF7DD3FC),
        danger = Color(0xFFFF5C6C), text = Color(0xFFFBF1EC), textMuted = Color(0xFFB8A39A),
    )
    val Violet = Palette(
        key = "violeta", label = "Violeta", dark = true,
        background = Color(0xFF0E0B16), surface = Color(0xFF181325), surfaceHigh = Color(0xFF211A33), outline = Color(0xFF322845),
        accent = Color(0xFFB98CFF), accentDim = Color(0xFF5B3A99), onAccent = Color(0xFF1C0838),
        accent2 = Color(0xFF22D3EE), accent3 = Color(0xFFFF8AD8), accent4 = Color(0xFF39FF88),
        danger = Color(0xFFFF5C6C), text = Color(0xFFF4F0FB), textMuted = Color(0xFFA79DB8),
    )
    val Rose = Palette(
        key = "rosa", label = "Rosa", dark = true,
        background = Color(0xFF150A10), surface = Color(0xFF21121A), surfaceHigh = Color(0xFF2C1923), outline = Color(0xFF3E2632),
        accent = Color(0xFFFF6FAE), accentDim = Color(0xFF8C2A57), onAccent = Color(0xFF33001A),
        accent2 = Color(0xFFFFC2DD), accent3 = Color(0xFFFFB86B), accent4 = Color(0xFF9AD9FF),
        danger = Color(0xFFFF5C6C), text = Color(0xFFFCF0F5), textMuted = Color(0xFFBFA2AF),
    )
    val Graphite = Palette(
        key = "grafite", label = "Grafite", dark = true,
        background = Color(0xFF0F0F10), surface = Color(0xFF19191B), surfaceHigh = Color(0xFF232326), outline = Color(0xFF333338),
        accent = Color(0xFFE5E7EB), accentDim = Color(0xFF52525B), onAccent = Color(0xFF111113),
        accent2 = Color(0xFF60A5FA), accent3 = Color(0xFFFBBF24), accent4 = Color(0xFF34D399),
        danger = Color(0xFFF87171), text = Color(0xFFF4F4F5), textMuted = Color(0xFFA1A1AA),
    )
    val Light = Palette(
        key = "claro", label = "Claro", dark = false,
        background = Color(0xFFF6F8F7), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDF1EF), outline = Color(0xFFD5DDD9),
        accent = Color(0xFF0E9F5A), accentDim = Color(0xFFBDEBD3), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF0891B2), accent3 = Color(0xFFE8681A), accent4 = Color(0xFF7C3AED),
        danger = Color(0xFFDC2626), text = Color(0xFF111816), textMuted = Color(0xFF5B6964),
    )
    val LightBlue = Palette(
        key = "claro_azul", label = "Claro azul", dark = false,
        background = Color(0xFFF5F8FC), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEAF0F7), outline = Color(0xFFD3DDE8),
        accent = Color(0xFF2563EB), accentDim = Color(0xFFC7D7FB), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF0D9488), accent3 = Color(0xFFEA580C), accent4 = Color(0xFFDB2777),
        danger = Color(0xFFDC2626), text = Color(0xFF0F172A), textMuted = Color(0xFF5B677A),
    )

    val all = listOf(Sport, Ocean, Sunset, Violet, Rose, Graphite, Light, LightBlue)

    fun byKey(key: String?): Palette = all.firstOrNull { it.key == key } ?: Sport
}

/**
 * Cores do tema atual. Os nomes vêm do tema esportivo original (Neon, Cyan...), mas cada um
 * segue o tema escolhido. Ler estes valores dentro de uma tela faz ela se redesenhar quando o tema muda.
 */
object Evolui {
    var palette by mutableStateOf(Palettes.Sport)

    val Background get() = palette.background
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Outline get() = palette.outline
    val Neon get() = palette.accent
    val NeonDim get() = palette.accentDim
    val OnNeon get() = palette.onAccent
    val Cyan get() = palette.accent2
    val Orange get() = palette.accent3
    val Purple get() = palette.accent4
    val Red get() = palette.danger
    val Text get() = palette.text
    val TextMuted get() = palette.textMuted
}

private fun colorsFor(p: Palette): ColorScheme {
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentDim,
        onPrimaryContainer = p.text,
        secondary = p.accent2,
        onSecondary = p.background,
        secondaryContainer = p.surfaceHigh,
        onSecondaryContainer = p.text,
        tertiary = p.accent3,
        onTertiary = p.background,
        tertiaryContainer = p.surfaceHigh,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textMuted,
        surfaceTint = p.accent,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        surfaceBright = p.surfaceHigh,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.outline,
        outline = p.outline,
        outlineVariant = p.outline,
        error = p.danger,
        onError = p.background,
    )
}

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
    MaterialTheme(colorScheme = colorsFor(Evolui.palette), shapes = Shapes, typography = Type, content = content)
}
