package com.addsoncosta.evolui.ui.badges

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.addsoncosta.evolui.R
import com.addsoncosta.evolui.data.Badge
import com.addsoncosta.evolui.ui.theme.Palette

/** Fonte de onde vem o símbolo da medalha (aprovado na prévia de 27/10). */
enum class GlyphFont { SYMBOLS, NOTO_SYMBOLS_2, NOTO_MATH }

/** Símbolo de uma medalha: um caractere de uma das fontes. */
data class BadgeGlyph(val font: GlyphFont, val text: String)

/**
 * Símbolos das medalhas, sem emoji: Material Symbols (preenchido, peso 500), Noto Sans Symbols 2
 * e Noto Sans Math. As fontes vão no app só com estes caracteres (res/font/badge_*.ttf).
 */
object BadgeGlyphs {
    private var symbols: Typeface? = null
    private var notoSymbols2: Typeface? = null
    private var notoMath: Typeface? = null

    /** Carrega as fontes uma vez (chamado ao abrir o app). */
    fun init(context: Context) {
        if (symbols != null) return
        symbols = runCatching { ResourcesCompat.getFont(context, R.font.badge_symbols) }.getOrNull()
        notoSymbols2 = runCatching { ResourcesCompat.getFont(context, R.font.badge_noto_symbols2) }.getOrNull()
        notoMath = runCatching { ResourcesCompat.getFont(context, R.font.badge_noto_math) }.getOrNull()
    }

    fun typeface(font: GlyphFont): Typeface = when (font) {
        GlyphFont.SYMBOLS -> symbols
        GlyphFont.NOTO_SYMBOLS_2 -> notoSymbols2
        GlyphFont.NOTO_MATH -> notoMath
    } ?: Typeface.DEFAULT

    private fun ms(codepoint: Int) = BadgeGlyph(GlyphFont.SYMBOLS, String(Character.toChars(codepoint)))
    private fun s2(text: String) = BadgeGlyph(GlyphFont.NOTO_SYMBOLS_2, text)
    private fun math(text: String) = BadgeGlyph(GlyphFont.NOTO_MATH, text)

    private val byKey: Map<String, BadgeGlyph> = mapOf(
        // Alimentação
        "primeira_refeicao" to ms(0xe56c), // restaurant
        "sequencia_3" to ms(0xe80e), // whatshot
        "sequencia_7" to ms(0xef55), // local_fire_department
        "sequencia_30" to ms(0xebcc), // calendar_month
        "sequencia_100" to s2("✪"), // estrela em círculo
        // Treino
        "primeiro_treino" to ms(0xe566), // directions_run
        "treinos_10" to ms(0xeb43), // fitness_center
        "treinos_50" to ms(0xf6e6), // exercise
        "treinos_100" to ms(0xea3f), // military_tech
        "semana_150" to s2("☇"), // raio
        // Jejum
        "primeiro_jejum" to ms(0xe425), // timer
        "jejum_16" to math("⧗"), // ampulheta
        "jejuns_10" to math("☾"), // lua (Noto Sans Math; a Symbols 2 não tem)
        "jejuns_30" to s2("✷"), // estrela de 8 pontas
        // Peso
        "primeira_pesagem" to ms(0xf039), // monitor_weight
        "kg_1" to s2("◎"), // alvo
        "kg_5" to s2("❖"), // losango
        "kg_10" to ms(0xea23), // trophy
        "meta_peso" to ms(0xf06e), // sports_score
        // Água
        "agua_1" to ms(0xe798), // water_drop
        "agua_7" to ms(0xf6d6), // water_full
        "agua_30" to ms(0xe176), // waves
        // Fotos
        "primeira_foto" to ms(0xe412), // photo_camera
        "antes_depois" to s2("◑"), // círculo meio cheio
        // Saúde
        "primeiro_exame" to ms(0xea4b), // science
        "primeira_consulta" to ms(0xf805), // stethoscope
        "anamnese" to ms(0xe09e), // clinical_notes
        "checkup" to s2("✚"), // cruz
        // Geral
        "colecionador" to s2("❂"), // flor de 8 pétalas
    )

    fun of(badge: Badge): BadgeGlyph = byKey[badge.key] ?: s2("✪")
}

private const val WHITE = 0xFFFFFFFF.toInt()

/**
 * Medalha de vidro com o símbolo da fonte: disco translúcido na cor do badge, aro colorido, brilho no alto
 * e o símbolo branco com um halo da cor. Bloqueada: em cinza e mais escura.
 * Desenha num quadrado de lado 2 x [radius] centrado em ([cx], [cy]).
 */
fun drawBadgeMedal(canvas: Canvas, cx: Float, cy: Float, radius: Float, badge: Badge, earned: Boolean, palette: Palette) {
    val accent = withAlpha(badge.color.argbIn(palette), 255)
    val r = radius * 0.94f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    if (!earned) {
        val gray = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    setSaturation(0f)
                    postConcat(ColorMatrix().apply { setScale(0.7f, 0.7f, 0.7f, 1f) })
                }
            )
        }
        canvas.saveLayer(RectF(cx - radius, cy - radius, cx + radius, cy + radius), gray)
    }

    // Halo da cor em volta, como a sombra colorida da prévia.
    fill.shader = RadialGradient(cx, cy + r * 0.08f, radius, intArrayOf(withAlpha(accent, 0x4D), withAlpha(accent, 0)), floatArrayOf(0.78f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy + r * 0.08f, radius, fill)

    // Base escura (para a medalha aparecer em qualquer fundo) e o vidro na cor do badge.
    fill.shader = null
    fill.color = withAlpha(palette.background.toArgb(), 0xD9)
    canvas.drawCircle(cx, cy, r, fill)
    fill.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(accent, 0x4D), withAlpha(accent, 0x14)), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r, fill)
    // Sombra interna embaixo.
    fill.shader = RadialGradient(cx, cy - r * 0.2f, r * 1.25f, intArrayOf(0x00000000, 0x00000000, 0x40000000), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r, fill)
    // Brilho do vidro no alto, à esquerda.
    fill.shader = RadialGradient(
        cx - r * 0.36f, cy - r * 0.48f, r * 1.24f,
        intArrayOf(withAlpha(WHITE, 0x52), withAlpha(WHITE, 0x0A), withAlpha(WHITE, 0)),
        floatArrayOf(0f, 0.46f, 0.62f), Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(cx, cy, r, fill)
    fill.shader = null

    // Aro colorido e um anel fino de luz por dentro.
    stroke.strokeWidth = r * 0.056f
    stroke.color = withAlpha(accent, 0xBF)
    canvas.drawCircle(cx, cy, r - stroke.strokeWidth / 2f, stroke)
    stroke.strokeWidth = r * 0.028f
    stroke.color = withAlpha(WHITE, 0x2E)
    canvas.drawCircle(cx, cy, r * 0.83f, stroke)

    // O símbolo, branco, com halo da cor.
    val glyph = BadgeGlyphs.of(badge)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = BadgeGlyphs.typeface(glyph.font)
        color = WHITE
        textAlign = Paint.Align.CENTER
        textSize = r * if (glyph.font == GlyphFont.SYMBOLS) 0.95f else 0.90f
        setShadowLayer(r * 0.39f, 0f, 0f, withAlpha(accent, 0xCC))
    }
    val bounds = Rect()
    text.getTextBounds(glyph.text, 0, glyph.text.length, bounds)
    canvas.drawText(glyph.text, cx, cy - bounds.exactCenterY(), text)

    if (!earned) canvas.restore()
}
