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

/** Padrão decorativo desenhado no fundo das telas (ver [drawThemeBackdrop]). */
enum class BackdropStyle {
    PLAIN, GLASS, SPORT, CLEAN, TOPO, GRID, RUGGED, CAMO, NAVAL, AIR, TACTICAL,
    POP, KANDINSKY, ROMANTIC, BAROQUE, ROCOCO, GREEK, MARBLE,
}

/**
 * Uma combinação de cores do app. [accent] é a cor principal (o "neon" do tema esportivo);
 * [accent2], [accent3] e [accent4] são usadas em gráficos e destaques.
 * [style] é o padrão do fundo; com [glass], os cartões ficam translúcidos sobre ele.
 * [group] agrupa os temas na tela de escolha.
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
    val style: BackdropStyle = BackdropStyle.PLAIN,
    val glass: Boolean = false,
    val group: String = Palettes.BASIC,
)

/** Temas que o usuário pode escolher em Configurações. */
object Palettes {
    const val BASIC = "Básicos"
    const val SPORT = "Esportivos"
    const val MILITARY = "Militares"
    const val ART = "Arte"
    const val CLASSIC = "Clássicos"

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

    // Só cores, texturas e estilo: nada de logos, marcas ou brasões.
    val Glass = Palette(
        key = "vidro", label = "Vidro", dark = true,
        background = Color(0xFF0E1324), surface = Color(0xFF242D4A), surfaceHigh = Color(0xFF2E3858), outline = Color(0xFF414C70),
        accent = Color(0xFF7DD3FC), accentDim = Color(0xFF1D4E6E), onAccent = Color(0xFF04202E),
        accent2 = Color(0xFFC4B5FD), accent3 = Color(0xFFF9A8D4), accent4 = Color(0xFF818CF8),
        danger = Color(0xFFFF7A88), text = Color(0xFFF5F7FF), textMuted = Color(0xFFB8C0D8),
        style = BackdropStyle.GLASS, glass = true, group = BASIC,
    )
    val Run = Palette(
        key = "corrida", label = "Corrida laranja", dark = true,
        background = Color(0xFF0D0D0F), surface = Color(0xFF18181B), surfaceHigh = Color(0xFF222226), outline = Color(0xFF34343A),
        accent = Color(0xFFFC5200), accentDim = Color(0xFF7A2A05), onAccent = Color(0xFF1A0800),
        accent2 = Color(0xFFFFB37A), accent3 = Color(0xFF3DDC97), accent4 = Color(0xFF60A5FA),
        danger = Color(0xFFFF5C6C), text = Color(0xFFF7F7F7), textMuted = Color(0xFFA6A6AE),
        style = BackdropStyle.SPORT, group = SPORT,
    )
    val HealthBlue = Palette(
        key = "saude_azul", label = "Saúde azul", dark = false,
        background = Color(0xFFF3F7FC), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFE8F0FA), outline = Color(0xFFD2DEEC),
        accent = Color(0xFF1967D2), accentDim = Color(0xFFCFE2FC), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF0E9384), accent3 = Color(0xFFE8710A), accent4 = Color(0xFF8E44AD),
        danger = Color(0xFFD93025), text = Color(0xFF1B2433), textMuted = Color(0xFF576274),
        style = BackdropStyle.CLEAN, group = SPORT,
    )
    val Adventure = Palette(
        key = "aventura", label = "Aventura", dark = true,
        background = Color(0xFF101418), surface = Color(0xFF1A2027), surfaceHigh = Color(0xFF232B33), outline = Color(0xFF34404B),
        accent = Color(0xFF2ED3E6), accentDim = Color(0xFF0F5862), onAccent = Color(0xFF00262B),
        accent2 = Color(0xFFF2C14E), accent3 = Color(0xFFFF7A45), accent4 = Color(0xFF9BE15D),
        danger = Color(0xFFFF5C6C), text = Color(0xFFEEF3F6), textMuted = Color(0xFF9DABB7),
        style = BackdropStyle.TOPO, group = SPORT,
    )
    val Navigator = Palette(
        key = "navegador", label = "Navegador", dark = true,
        background = Color(0xFF050608), surface = Color(0xFF111317), surfaceHigh = Color(0xFF1A1D23), outline = Color(0xFF2B3039),
        accent = Color(0xFF2E9BFF), accentDim = Color(0xFF0B4A80), onAccent = Color(0xFF001A33),
        accent2 = Color(0xFF34D399), accent3 = Color(0xFFFFB020), accent4 = Color(0xFFE5E7EB),
        danger = Color(0xFFFF4D5E), text = Color(0xFFF3F5F8), textMuted = Color(0xFF9CA3AF),
        style = BackdropStyle.GRID, group = SPORT,
    )
    val Rugged = Palette(
        key = "robusto", label = "Robusto", dark = true,
        background = Color(0xFF14150F), surface = Color(0xFF1F2118), surfaceHigh = Color(0xFF292C20), outline = Color(0xFF3D4130),
        accent = Color(0xFFFF7A1A), accentDim = Color(0xFF7A3A0A), onAccent = Color(0xFF240E00),
        accent2 = Color(0xFFC9B97A), accent3 = Color(0xFF9DB262), accent4 = Color(0xFF7DD3FC),
        danger = Color(0xFFFF5C5C), text = Color(0xFFF1EFE4), textMuted = Color(0xFFABA891),
        style = BackdropStyle.RUGGED, group = SPORT,
    )
    val Olive = Palette(
        key = "verde_oliva", label = "Verde-oliva", dark = true,
        background = Color(0xFF161A10), surface = Color(0xFF20261A), surfaceHigh = Color(0xFF2A3122), outline = Color(0xFF3E4732),
        accent = Color(0xFFA3B86C), accentDim = Color(0xFF4B5A2A), onAccent = Color(0xFF141A06),
        accent2 = Color(0xFFD6C08A), accent3 = Color(0xFFB08A5A), accent4 = Color(0xFF7FA06A),
        danger = Color(0xFFE5604F), text = Color(0xFFF0F0E4), textMuted = Color(0xFFADB29A),
        style = BackdropStyle.CAMO, group = MILITARY,
    )
    val Naval = Palette(
        key = "naval", label = "Naval", dark = true,
        background = Color(0xFF071428), surface = Color(0xFF0E1E38), surfaceHigh = Color(0xFF162945), outline = Color(0xFF253B5C),
        accent = Color(0xFFE2B65B), accentDim = Color(0xFF6B5320), onAccent = Color(0xFF1F1400),
        accent2 = Color(0xFFE6EEF8), accent3 = Color(0xFF5FA8E8), accent4 = Color(0xFFE5484D),
        danger = Color(0xFFFF6B6B), text = Color(0xFFF4F7FB), textMuted = Color(0xFFA3B4CC),
        style = BackdropStyle.NAVAL, group = MILITARY,
    )
    val Air = Palette(
        key = "aeronautico", label = "Azul aeronáutico", dark = false,
        background = Color(0xFFEEF3F8), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFE1E9F2), outline = Color(0xFFC6D3E1),
        accent = Color(0xFF1F5FA8), accentDim = Color(0xFFC9DAF0), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF5B6B7C), accent3 = Color(0xFFC77F00), accent4 = Color(0xFF1E8FB0),
        danger = Color(0xFFC62828), text = Color(0xFF14202E), textMuted = Color(0xFF526070),
        style = BackdropStyle.AIR, group = MILITARY,
    )
    val Tactical = Palette(
        key = "tatico", label = "Tático", dark = true,
        background = Color(0xFF0A0A0A), surface = Color(0xFF151515), surfaceHigh = Color(0xFF1F1F1F), outline = Color(0xFF333333),
        accent = Color(0xFFE0E0E0), accentDim = Color(0xFF4A4A4A), onAccent = Color(0xFF0A0A0A),
        accent2 = Color(0xFF9CA3AF), accent3 = Color(0xFFC2A878), accent4 = Color(0xFF8BA877),
        danger = Color(0xFFE5484D), text = Color(0xFFF2F2F2), textMuted = Color(0xFF9E9E9E),
        style = BackdropStyle.TACTICAL, group = MILITARY,
    )
    val Pop = Palette(
        key = "pop", label = "Pop colorido", dark = false,
        background = Color(0xFFFFFBF0), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFFFF1D6), outline = Color(0xFFDCCFB4),
        accent = Color(0xFFD6006F), accentDim = Color(0xFFFFC7E5), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF0089C4), accent3 = Color(0xFFF06400), accent4 = Color(0xFF00A651),
        danger = Color(0xFFC81E35), text = Color(0xFF141414), textMuted = Color(0xFF545454),
        style = BackdropStyle.POP, group = ART,
    )
    val Kandinsky = Palette(
        key = "kandinsky", label = "Kandinsky", dark = false,
        background = Color(0xFFF5EEDC), surface = Color(0xFFFFFBF2), surfaceHigh = Color(0xFFEFE5CF), outline = Color(0xFFD8CAAA),
        accent = Color(0xFF1F4E9C), accentDim = Color(0xFFC9D6EE), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFFC8102E), accent3 = Color(0xFFE8A317), accent4 = Color(0xFF3C8D5A),
        danger = Color(0xFFB3261E), text = Color(0xFF1C1A16), textMuted = Color(0xFF5B5447),
        style = BackdropStyle.KANDINSKY, group = ART,
    )
    val Romantic = Palette(
        key = "romantico", label = "Romântico", dark = false,
        background = Color(0xFFFBF3F5), surface = Color(0xFFFFFBFC), surfaceHigh = Color(0xFFF6E8EE), outline = Color(0xFFE6D0DA),
        accent = Color(0xFFB24C7A), accentDim = Color(0xFFF5D3E2), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF7E6BB5), accent3 = Color(0xFFD98E73), accent4 = Color(0xFF5E9E9A),
        danger = Color(0xFFC0392B), text = Color(0xFF2A1F25), textMuted = Color(0xFF6A5962),
        style = BackdropStyle.ROMANTIC, group = ART,
    )
    val Baroque = Palette(
        key = "barroco", label = "Barroco", dark = true,
        background = Color(0xFF2A0A12), surface = Color(0xFF3A121C), surfaceHigh = Color(0xFF4A1A26), outline = Color(0xFF6A2E3A),
        accent = Color(0xFFD4AF37), accentDim = Color(0xFF6E5414), onAccent = Color(0xFF2A1A00),
        accent2 = Color(0xFFF0D78C), accent3 = Color(0xFFF08A6F), accent4 = Color(0xFF9AB3D5),
        danger = Color(0xFFFF7B7B), text = Color(0xFFFAF1E4), textMuted = Color(0xFFD2B9B1),
        style = BackdropStyle.BAROQUE, group = CLASSIC,
    )
    val Rococo = Palette(
        key = "rococo", label = "Rococó", dark = false,
        background = Color(0xFFFBEFF1), surface = Color(0xFFFFFAFB), surfaceHigh = Color(0xFFF3E1E6), outline = Color(0xFFE2CCD3),
        accent = Color(0xFF2A7F6A), accentDim = Color(0xFFCDEDE3), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFFB0577A), accent3 = Color(0xFFB8902F), accent4 = Color(0xFF7A8CC2),
        danger = Color(0xFFC0392B), text = Color(0xFF2B2226), textMuted = Color(0xFF6A5B61),
        style = BackdropStyle.ROCOCO, group = CLASSIC,
    )
    val Greek = Palette(
        key = "grego", label = "Clássico grego", dark = false,
        background = Color(0xFFF7F4EE), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEFE9DE), outline = Color(0xFFDCD2C0),
        accent = Color(0xFFB04E2A), accentDim = Color(0xFFF3D6C8), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF2C5D8A), accent3 = Color(0xFFC99A2E), accent4 = Color(0xFF5B7F4E),
        danger = Color(0xFFB3261E), text = Color(0xFF221C17), textMuted = Color(0xFF615950),
        style = BackdropStyle.GREEK, group = CLASSIC,
    )
    val Marble = Palette(
        key = "marmore", label = "Mármore", dark = false,
        background = Color(0xFFF4F3F1), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFECEAE6), outline = Color(0xFFD6D3CD),
        accent = Color(0xFF86683A), accentDim = Color(0xFFEADFC8), onAccent = Color(0xFFFFFFFF),
        accent2 = Color(0xFF4A6378), accent3 = Color(0xFFA0522D), accent4 = Color(0xFF6B7F6A),
        danger = Color(0xFFB3261E), text = Color(0xFF1E1E1E), textMuted = Color(0xFF5E5E5E),
        style = BackdropStyle.MARBLE, group = CLASSIC,
    )
    val MarbleBlack = Palette(
        key = "marmore_negro", label = "Mármore negro", dark = true,
        background = Color(0xFF0E0E0F), surface = Color(0xFF19191B), surfaceHigh = Color(0xFF232326), outline = Color(0xFF36363A),
        accent = Color(0xFFD9B66A), accentDim = Color(0xFF5E4B22), onAccent = Color(0xFF1E1500),
        accent2 = Color(0xFFE8E8E8), accent3 = Color(0xFFB0B7C3), accent4 = Color(0xFF8FB39A),
        danger = Color(0xFFFF6B6B), text = Color(0xFFF5F5F5), textMuted = Color(0xFFA8A8AD),
        style = BackdropStyle.MARBLE, group = CLASSIC,
    )

    val all = listOf(
        Sport, Ocean, Sunset, Violet, Rose, Graphite, Light, LightBlue, Glass,
        Run, HealthBlue, Adventure, Navigator, Rugged,
        Olive, Naval, Air, Tactical,
        Pop, Kandinsky, Romantic,
        Baroque, Rococo, Greek, Marble, MarbleBlack,
    )

    /** Grupos na ordem em que aparecem na escolha de tema. */
    val groups = listOf(BASIC, SPORT, MILITARY, ART, CLASSIC)

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
    // Temas de vidro: cartões e barras translúcidos sobre o fundo. Folhas (Low) e diálogos (High) quase opacos,
    // para o que estiver atrás não atrapalhar a leitura; o fundo segue opaco.
    fun g(c: Color, alpha: Float) = if (p.glass) c.copy(alpha = alpha) else c
    return base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentDim,
        onPrimaryContainer = p.text,
        secondary = p.accent2,
        onSecondary = p.background,
        secondaryContainer = g(p.surfaceHigh, 0.70f),
        onSecondaryContainer = p.text,
        tertiary = p.accent3,
        onTertiary = p.background,
        tertiaryContainer = g(p.surfaceHigh, 0.70f),
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = g(p.surfaceHigh, 0.70f),
        onSurfaceVariant = p.textMuted,
        surfaceTint = p.accent,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        surfaceBright = g(p.surfaceHigh, 0.70f),
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = g(p.surface, 0.90f),
        surfaceContainer = g(p.surface, 0.70f),
        surfaceContainerHigh = g(p.surfaceHigh, 0.92f),
        surfaceContainerHighest = g(p.outline, 0.92f),
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
