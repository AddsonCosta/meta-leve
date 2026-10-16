package com.addsoncosta.evolui.ui.badges

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.addsoncosta.evolui.data.Badge
import com.addsoncosta.evolui.data.BadgeColor
import com.addsoncosta.evolui.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Metal da medalha, do mais simples ao mais raro. Cores: sombra, meio, claro e brilho. */
enum class BadgeTier(val label: String, val dark: Int, val mid: Int, val light: Int, val shine: Int) {
    BRONZE("Bronze", 0xFF5E3017.toInt(), 0xFFA9652F.toInt(), 0xFFE2A267.toInt(), 0xFFFFE2C2.toInt()),
    SILVER("Prata", 0xFF555D68.toInt(), 0xFFA3ADB8.toInt(), 0xFFE4E9EF.toInt(), 0xFFFFFFFF.toInt()),
    GOLD("Ouro", 0xFF6E4504.toInt(), 0xFFCF9418.toInt(), 0xFFFFD45E.toInt(), 0xFFFFF5CC.toInt()),
    PLATINUM("Platina", 0xFF3F4F5C.toInt(), 0xFF97AEBE.toInt(), 0xFFDCEAF3.toInt(), 0xFFFFFFFF.toInt()),
    DIAMOND("Diamante", 0xFF154766.toInt(), 0xFF4FC3EE.toInt(), 0xFFC6F3FF.toInt(), 0xFFFFFFFF.toInt()),
}

/** Símbolo desenhado no centro da medalha. */
enum class BadgeEmblem { PLATE, FLAME, CALENDAR, CROWN, SHOE, DUMBBELL, BOLT, CLOCK, MOON, SCALE, TARGET, TROPHY, FLAG, DROP, CAMERA, COMPARE, FLASK, CROSS, CLIPBOARD, HEART, STAR }

private class Art(val tier: BadgeTier, val emblem: BadgeEmblem)

private val artByKey: Map<String, Art> = mapOf(
    "primeira_refeicao" to Art(BadgeTier.BRONZE, BadgeEmblem.PLATE),
    "sequencia_3" to Art(BadgeTier.BRONZE, BadgeEmblem.FLAME),
    "sequencia_7" to Art(BadgeTier.SILVER, BadgeEmblem.FLAME),
    "sequencia_30" to Art(BadgeTier.GOLD, BadgeEmblem.CALENDAR),
    "sequencia_100" to Art(BadgeTier.DIAMOND, BadgeEmblem.CROWN),
    "primeiro_treino" to Art(BadgeTier.BRONZE, BadgeEmblem.SHOE),
    "treinos_10" to Art(BadgeTier.SILVER, BadgeEmblem.DUMBBELL),
    "treinos_50" to Art(BadgeTier.GOLD, BadgeEmblem.DUMBBELL),
    "treinos_100" to Art(BadgeTier.PLATINUM, BadgeEmblem.DUMBBELL),
    "semana_150" to Art(BadgeTier.SILVER, BadgeEmblem.BOLT),
    "primeiro_jejum" to Art(BadgeTier.BRONZE, BadgeEmblem.CLOCK),
    "jejum_16" to Art(BadgeTier.SILVER, BadgeEmblem.CLOCK),
    "jejuns_10" to Art(BadgeTier.GOLD, BadgeEmblem.MOON),
    "jejuns_30" to Art(BadgeTier.PLATINUM, BadgeEmblem.MOON),
    "primeira_pesagem" to Art(BadgeTier.BRONZE, BadgeEmblem.SCALE),
    "kg_1" to Art(BadgeTier.BRONZE, BadgeEmblem.TARGET),
    "kg_5" to Art(BadgeTier.GOLD, BadgeEmblem.TROPHY),
    "kg_10" to Art(BadgeTier.PLATINUM, BadgeEmblem.TROPHY),
    "meta_peso" to Art(BadgeTier.DIAMOND, BadgeEmblem.FLAG),
    "agua_1" to Art(BadgeTier.BRONZE, BadgeEmblem.DROP),
    "agua_7" to Art(BadgeTier.SILVER, BadgeEmblem.DROP),
    "agua_30" to Art(BadgeTier.GOLD, BadgeEmblem.DROP),
    "primeira_foto" to Art(BadgeTier.BRONZE, BadgeEmblem.CAMERA),
    "antes_depois" to Art(BadgeTier.SILVER, BadgeEmblem.COMPARE),
    "primeiro_exame" to Art(BadgeTier.BRONZE, BadgeEmblem.FLASK),
    "primeira_consulta" to Art(BadgeTier.BRONZE, BadgeEmblem.CROSS),
    "anamnese" to Art(BadgeTier.SILVER, BadgeEmblem.CLIPBOARD),
    "checkup" to Art(BadgeTier.GOLD, BadgeEmblem.HEART),
    "colecionador" to Art(BadgeTier.DIAMOND, BadgeEmblem.STAR),
)

/** Metal da medalha (bronze nas primeiras vezes, diamante nas mais difíceis). */
val Badge.tier: BadgeTier get() = artByKey[key]?.tier ?: BadgeTier.SILVER

val Badge.emblem: BadgeEmblem get() = artByKey[key]?.emblem ?: BadgeEmblem.STAR

/** Cor do badge num tema qualquer (para desenhar fora da tela, em imagens). */
fun BadgeColor.argbIn(p: Palette): Int = when (this) {
    BadgeColor.NEON -> p.accent.toArgb()
    BadgeColor.CYAN -> p.accent2.toArgb()
    BadgeColor.ORANGE -> p.accent3.toArgb()
    BadgeColor.PURPLE -> p.accent4.toArgb()
    BadgeColor.RED -> p.danger.toArgb()
}

// ---------- Cores ----------

internal fun mix(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    fun ch(x: Int, y: Int) = (x + (y - x) * u).roundToInt().coerceIn(0, 255)
    return Color.argb(
        ch(Color.alpha(a), Color.alpha(b)),
        ch(Color.red(a), Color.red(b)),
        ch(Color.green(a), Color.green(b)),
        ch(Color.blue(a), Color.blue(b)),
    )
}

internal fun withAlpha(c: Int, alpha: Int): Int = (c and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

// ---------- Medalha ----------

/**
 * Desenha a medalha do badge, em vetor, num quadrado de lado 2 x [radius] centrado em ([cx], [cy]):
 * fitas, sombra, coroa de louros (ouro em diante), borda serrilhada de metal, bisel, esmalte na cor
 * do badge, o símbolo em relevo, brilho e estrelinhas. Bloqueada: em cinza, com um cadeado.
 */
fun drawBadgeMedal(canvas: Canvas, cx: Float, cy: Float, radius: Float, badge: Badge, earned: Boolean, palette: Palette) {
    val tier = badge.tier
    val accent = withAlpha(badge.color.argbIn(palette), 255)
    val r = radius
    val mcy = cy - r * 0.10f
    val disc = r * 0.70f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    if (!earned) {
        val gray = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            alpha = 165
        }
        canvas.saveLayer(RectF(cx - r, cy - r, cx + r, cy + r), gray)
    }

    drawRibbons(canvas, cx, mcy, cy + r * 0.98f, disc, accent, tier, fill)

    // Sombra macia sob a medalha.
    fill.shader = RadialGradient(
        cx, mcy + r * 0.05f, disc * 1.14f,
        intArrayOf(0x70000000, 0x30000000, 0x00000000), floatArrayOf(0.72f, 0.88f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(cx, mcy + r * 0.05f, disc * 1.14f, fill)

    if (tier >= BadgeTier.GOLD) drawLaurel(canvas, cx, mcy, disc, tier, fill, stroke)

    // Borda serrilhada (como moeda), com metal em varredura para parecer polido.
    val teeth = if (tier >= BadgeTier.GOLD) 48 else 36
    val edge = Path()
    for (i in 0 until teeth * 2) {
        val a = Math.PI * i / teeth
        val rr = if (i % 2 == 0) disc else disc * 0.955f
        val x = cx + rr * cos(a).toFloat()
        val y = mcy + rr * sin(a).toFloat()
        if (i == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
    }
    edge.close()
    val sweepColors = if (tier == BadgeTier.DIAMOND) {
        intArrayOf(tier.shine, 0xFFB8F0FF.toInt(), 0xFFFFC8F0.toInt(), tier.mid, 0xFFE8D8FF.toInt(), tier.light, 0xFFB8FFE8.toInt(), tier.mid, tier.shine)
    } else {
        intArrayOf(tier.light, tier.mid, tier.dark, tier.mid, tier.shine, tier.mid, tier.dark, tier.mid, tier.light)
    }
    fill.shader = SweepGradient(cx, mcy, sweepColors, null)
    canvas.drawPath(edge, fill)

    // Anel de metal (claro em cima) e bisel invertido por dentro.
    fill.shader = LinearGradient(cx, mcy - disc, cx, mcy + disc, intArrayOf(tier.shine, tier.light, tier.mid, tier.dark), floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, mcy, disc * 0.92f, fill)
    fill.shader = LinearGradient(cx, mcy - disc, cx, mcy + disc, intArrayOf(tier.dark, tier.mid, tier.light, tier.shine), null, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, mcy, disc * 0.80f, fill)

    // Contas em volta (prata em diante).
    if (tier >= BadgeTier.SILVER) {
        fill.shader = null
        fill.color = withAlpha(tier.shine, 0xD0)
        val beads = 30
        for (i in 0 until beads) {
            val a = 2 * Math.PI * i / beads
            canvas.drawCircle(cx + disc * 0.86f * cos(a).toFloat(), mcy + disc * 0.86f * sin(a).toFloat(), disc * 0.022f, fill)
        }
    }

    // Esmalte na cor do badge, com luz vindo de cima à esquerda.
    val face = disc * 0.76f
    fill.shader = RadialGradient(
        cx - face * 0.3f, mcy - face * 0.35f, face * 1.5f,
        intArrayOf(mix(accent, WHITE, 0.25f), mix(accent, BLACK, 0.30f), mix(accent, BLACK, 0.72f)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawCircle(cx, mcy, face, fill)
    fill.shader = null
    stroke.shader = null
    stroke.color = 0x66000000
    stroke.strokeWidth = face * 0.07f
    canvas.drawCircle(cx, mcy, face - stroke.strokeWidth / 2f, stroke)

    // Raios finos no esmalte (guilhoché).
    stroke.color = withAlpha(WHITE, 0x1C)
    stroke.strokeWidth = face * 0.035f
    val guilloche = 24
    for (i in 0 until guilloche) {
        val a = 2 * Math.PI * i / guilloche
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        canvas.drawLine(cx + ca * face * 0.25f, mcy + sa * face * 0.25f, cx + ca * face * 0.9f, mcy + sa * face * 0.9f, stroke)
    }

    // Símbolo em relevo: sombra deslocada, metal e contorno escuro.
    val glyph = emblemPath(badge.emblem, cx, mcy, face * 0.56f)
    canvas.save()
    canvas.translate(face * 0.035f, face * 0.06f)
    fill.color = 0x80000000.toInt()
    canvas.drawPath(glyph, fill)
    canvas.restore()
    fill.shader = LinearGradient(cx, mcy - face * 0.6f, cx, mcy + face * 0.6f, intArrayOf(tier.shine, tier.light, tier.mid), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    canvas.drawPath(glyph, fill)
    fill.shader = null
    stroke.color = withAlpha(mix(tier.dark, BLACK, 0.4f), 0xA0)
    stroke.strokeWidth = face * 0.022f
    stroke.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(glyph, stroke)

    // Brilho de vidro no esmalte.
    canvas.save()
    canvas.clipPath(Path().apply { addCircle(cx, mcy, face, Path.Direction.CW) })
    fill.shader = LinearGradient(cx, mcy - face, cx, mcy - face * 0.05f, withAlpha(WHITE, 0x60), withAlpha(WHITE, 0x00), Shader.TileMode.CLAMP)
    canvas.drawOval(RectF(cx - face * 1.15f, mcy - face * 1.3f, cx + face * 0.85f, mcy - face * 0.05f), fill)
    canvas.restore()

    // Reflexo curvo no anel.
    fill.shader = null
    stroke.color = withAlpha(WHITE, 0xB0)
    stroke.strokeWidth = disc * 0.035f
    val ring = disc * 0.875f
    canvas.drawArc(RectF(cx - ring, mcy - ring, cx + ring, mcy + ring), 200f, 75f, false, stroke)
    stroke.color = withAlpha(WHITE, 0x60)
    stroke.strokeWidth = disc * 0.02f
    canvas.drawArc(RectF(cx - ring, mcy - ring, cx + ring, mcy + ring), 25f, 30f, false, stroke)

    // Enfeite no topo: estrela (platina) ou pedra lapidada (diamante).
    when (tier) {
        BadgeTier.PLATINUM -> {
            val sy = mcy - disc * 0.98f
            fill.shader = LinearGradient(cx, sy - disc * 0.2f, cx, sy + disc * 0.2f, tier.shine, tier.mid, Shader.TileMode.CLAMP)
            canvas.drawPath(starPath(cx, sy, disc * 0.2f, 0.45f), fill)
            fill.shader = null
            stroke.color = withAlpha(tier.dark, 0xC0)
            stroke.strokeWidth = disc * 0.015f
            canvas.drawPath(starPath(cx, sy, disc * 0.2f, 0.45f), stroke)
        }
        BadgeTier.DIAMOND -> drawGem(canvas, cx, mcy - disc * 0.98f, disc * 0.22f, fill, stroke)
        else -> Unit
    }

    if (earned) drawSparkles(canvas, cx, mcy, disc, tier, fill)

    if (!earned) {
        canvas.restore()
        drawPadlock(canvas, cx + disc * 0.58f, mcy + disc * 0.58f, disc * 0.3f, palette, fill, stroke)
    }
}

/** Duas fitas que descem atrás da medalha, com faixa central e ponta em V. */
private fun drawRibbons(canvas: Canvas, cx: Float, mcy: Float, bottom: Float, disc: Float, accent: Int, tier: BadgeTier, fill: Paint) {
    for (side in intArrayOf(-1, 1)) {
        val s = side.toFloat()
        val ax = cx + s * disc * 0.62f; val ay = mcy + disc * 0.15f
        val bx = cx + s * disc * 0.02f; val by = mcy + disc * 0.45f
        val cxp = cx + s * disc * 0.22f; val cyp = bottom
        val nx = cx + s * disc * 0.48f; val ny = bottom - disc * 0.16f
        val dx = cx + s * disc * 0.74f; val dy = bottom - disc * 0.04f
        val tail = Path().apply {
            moveTo(ax, ay); lineTo(bx, by); lineTo(cxp, cyp); lineTo(nx, ny); lineTo(dx, dy); close()
        }
        fill.shader = LinearGradient(dx, dy, cxp, cyp, intArrayOf(mix(accent, BLACK, 0.55f), mix(accent, BLACK, 0.1f), mix(accent, BLACK, 0.45f)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(tail, fill)
        // Faixa central no metal da medalha.
        fun lx(x0: Float, x1: Float, t: Float) = x0 + (x1 - x0) * t
        val stripe = Path().apply {
            moveTo(lx(ax, bx, 0.38f), lx(ay, by, 0.38f))
            lineTo(lx(ax, bx, 0.62f), lx(ay, by, 0.62f))
            lineTo(lx(dx, cxp, 0.62f), lx(dy, cyp, 0.62f) - disc * 0.06f)
            lineTo(lx(dx, cxp, 0.38f), lx(dy, cyp, 0.38f) - disc * 0.06f)
            close()
        }
        fill.shader = LinearGradient(ax, ay, cxp, cyp, tier.light, tier.mid, Shader.TileMode.CLAMP)
        canvas.drawPath(stripe, fill)
    }
    fill.shader = null
}

/** Coroa de louros em volta da medalha (ouro, platina e diamante). */
private fun drawLaurel(canvas: Canvas, cx: Float, cy: Float, disc: Float, tier: BadgeTier, fill: Paint, stroke: Paint) {
    val rl = disc * 1.07f
    val len = disc * 0.27f
    val wid = disc * 0.105f
    stroke.shader = null
    stroke.color = tier.mid
    stroke.strokeWidth = disc * 0.035f
    val oval = RectF(cx - rl, cy - rl, cx + rl, cy + rl)
    canvas.drawArc(oval, 100f, 140f, false, stroke)
    canvas.drawArc(oval, -60f, 140f, false, stroke)
    val leaf = RectF(-len / 2f, -wid / 2f, len / 2f, wid / 2f)
    fill.shader = LinearGradient(0f, -wid / 2f, 0f, wid / 2f, intArrayOf(tier.shine, tier.light, tier.dark), null, Shader.TileMode.CLAMP)
    val count = 8
    for (side in intArrayOf(-1, 1)) {
        for (i in 0 until count) {
            // Do pé (embaixo) até o alto, de cada lado.
            val deg = 100f + i * (135f / (count - 1))
            val a = Math.toRadians((if (side < 0) deg else 180f - deg).toDouble())
            for (k in intArrayOf(-1, 1)) {
                val rad = rl + k * disc * 0.07f
                val x = cx + rad * cos(a).toFloat()
                val y = cy + rad * sin(a).toFloat()
                // Tangente ao arco, apontando para cima, inclinada para fora/dentro.
                val tangent = Math.toDegrees(a).toFloat() + if (side < 0) 90f else -90f
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(tangent + k * 28f * side)
                canvas.drawOval(leaf, fill)
                canvas.restore()
            }
        }
    }
    fill.shader = null
}

/** Pedra lapidada no topo da medalha de diamante. */
private fun drawGem(canvas: Canvas, cx: Float, cy: Float, size: Float, fill: Paint, stroke: Paint) {
    val top = cy - size * 0.55f
    val mid = cy - size * 0.1f
    val bot = cy + size * 0.85f
    val outline = Path().apply {
        moveTo(cx - size * 0.55f, top); lineTo(cx + size * 0.55f, top); lineTo(cx + size, mid); lineTo(cx, bot); lineTo(cx - size, mid); close()
    }
    fill.shader = LinearGradient(cx - size, top, cx + size, bot, intArrayOf(0xFFE8FBFF.toInt(), 0xFF7FDBFF.toInt(), 0xFF2A8FD0.toInt()), null, Shader.TileMode.CLAMP)
    canvas.drawPath(outline, fill)
    fill.shader = null
    fill.color = 0x66FFFFFF
    canvas.drawPath(Path().apply { moveTo(cx - size * 0.55f, top); lineTo(cx, top); lineTo(cx - size * 0.3f, mid); lineTo(cx - size, mid); close() }, fill)
    fill.color = 0x40003050
    canvas.drawPath(Path().apply { moveTo(cx + size, mid); lineTo(cx, bot); lineTo(cx + size * 0.3f, mid); close() }, fill)
    stroke.shader = null
    stroke.color = 0xB0FFFFFF.toInt()
    stroke.strokeWidth = size * 0.06f
    stroke.strokeJoin = Paint.Join.ROUND
    canvas.drawPath(outline, stroke)
    canvas.drawLine(cx - size, mid, cx + size, mid, stroke)
    canvas.drawLine(cx - size * 0.3f, mid, cx, bot, stroke)
    canvas.drawLine(cx + size * 0.3f, mid, cx, bot, stroke)
}

/** Estrelinhas de brilho (mais estrelas nos metais mais raros). */
private fun drawSparkles(canvas: Canvas, cx: Float, cy: Float, disc: Float, tier: BadgeTier, fill: Paint) {
    val spots = listOf(
        floatArrayOf(0.66f, -0.66f, 0.17f),
        floatArrayOf(-0.74f, -0.22f, 0.10f),
        floatArrayOf(0.30f, 0.66f, 0.08f),
        floatArrayOf(-0.40f, -0.80f, 0.07f),
        floatArrayOf(0.86f, 0.20f, 0.09f),
    ).take(2 + tier.ordinal.coerceAtMost(3))
    for (s in spots) {
        val x = cx + s[0] * disc
        val y = cy + s[1] * disc
        val size = s[2] * disc
        fill.shader = RadialGradient(x, y, size * 1.4f, withAlpha(WHITE, 0x90), withAlpha(WHITE, 0x00), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, size * 1.4f, fill)
        fill.shader = null
        fill.color = WHITE
        canvas.drawPath(sparklePath(x, y, size), fill)
    }
}

/** Cadeado sobre a medalha bloqueada, nas cores do tema. */
private fun drawPadlock(canvas: Canvas, x: Float, y: Float, size: Float, palette: Palette, fill: Paint, stroke: Paint) {
    val bg = mix(withAlpha(palette.background.toArgb(), 255), withAlpha(palette.surfaceHigh.toArgb(), 255), 0.6f)
    fill.shader = null
    fill.color = bg
    canvas.drawCircle(x, y, size, fill)
    stroke.shader = null
    stroke.color = withAlpha(palette.outline.toArgb(), 255)
    stroke.strokeWidth = size * 0.1f
    canvas.drawCircle(x, y, size, stroke)
    val ink = withAlpha(palette.textMuted.toArgb(), 255)
    stroke.color = ink
    stroke.strokeWidth = size * 0.13f
    val sh = size * 0.28f
    canvas.drawArc(RectF(x - sh, y - size * 0.55f, x + sh, y), 180f, 180f, false, stroke)
    fill.color = ink
    canvas.drawRoundRect(RectF(x - size * 0.45f, y - size * 0.22f, x + size * 0.45f, y + size * 0.48f), size * 0.12f, size * 0.12f, fill)
    fill.color = bg
    canvas.drawCircle(x, y + size * 0.1f, size * 0.09f, fill)
}

/** Estrela de 5 pontas. [inner] é a razão entre o raio de dentro e o de fora. */
internal fun starPath(cx: Float, cy: Float, r: Float, inner: Float = 0.45f): Path = Path().apply {
    for (i in 0 until 10) {
        val a = -Math.PI / 2 + Math.PI * i / 5
        val rr = if (i % 2 == 0) r else r * inner
        val x = cx + rr * cos(a).toFloat()
        val y = cy + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Brilho de 4 pontas. */
internal fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val k = r * 0.16f
    moveTo(cx, cy - r)
    quadTo(cx + k, cy - k, cx + r, cy)
    quadTo(cx + k, cy + k, cx, cy + r)
    quadTo(cx - k, cy + k, cx - r, cy)
    quadTo(cx - k, cy - k, cx, cy - r)
    close()
}

// ---------- Símbolos ----------

/** Monta os símbolos em coordenadas de -1 a 1 (y para baixo), já levadas para o centro e a escala. */
private class Glyph(val cx: Float, val cy: Float, val s: Float) {
    fun fx(v: Double) = cx + v.toFloat() * s
    fun fy(v: Double) = cy + v.toFloat() * s

    fun Path.mv(x: Double, y: Double) = moveTo(fx(x), fy(y))
    fun Path.ln(x: Double, y: Double) = lineTo(fx(x), fy(y))
    fun Path.cu(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double) = cubicTo(fx(x1), fy(y1), fx(x2), fy(y2), fx(x3), fy(y3))

    fun shape(block: Path.() -> Unit): Path = Path().apply(block).apply { close() }
    fun circ(x: Double, y: Double, r: Double) = Path().apply { addCircle(fx(x), fy(y), r.toFloat() * s, Path.Direction.CW) }
    fun oval(l: Double, t: Double, r: Double, b: Double) = Path().apply { addOval(RectF(fx(l), fy(t), fx(r), fy(b)), Path.Direction.CW) }
    fun rr(l: Double, t: Double, r: Double, b: Double, rad: Double) = Path().apply {
        addRoundRect(RectF(fx(l), fy(t), fx(r), fy(b)), rad.toFloat() * s, rad.toFloat() * s, Path.Direction.CW)
    }
    fun poly(vararg p: Double) = Path().apply {
        for (i in p.indices step 2) if (i == 0) mv(p[0], p[1]) else ln(p[i], p[i + 1])
        close()
    }
    fun star(x: Double, y: Double, r: Double, inner: Float = 0.45f) = starPath(fx(x), fy(y), r.toFloat() * s, inner)

    /** Traço grosso transformado em área (para recortar linhas, como o pulso do coração). */
    fun line(width: Double, vararg p: Double): Path {
        val src = Path()
        for (i in p.indices step 2) if (i == 0) src.mv(p[0], p[1]) else src.ln(p[i], p[i + 1])
        val out = Path()
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = width.toFloat() * s
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }.getFillPath(src, out)
        return out
    }

    infix fun Path.join(o: Path): Path = apply { op(o, Path.Op.UNION) }
    infix fun Path.cut(o: Path): Path = apply { op(o, Path.Op.DIFFERENCE) }
}

private fun emblemPath(e: BadgeEmblem, cx: Float, cy: Float, s: Float): Path = with(Glyph(cx, cy, s)) {
    when (e) {
        BadgeEmblem.PLATE -> {
            val plate = (circ(0.0, 0.05, 0.62) cut circ(0.0, 0.05, 0.48)) join circ(0.0, 0.05, 0.34)
            val fork = rr(-0.99, -0.85, -0.93, -0.35, 0.03) join rr(-0.89, -0.85, -0.83, -0.35, 0.03) join
                rr(-0.79, -0.85, -0.73, -0.35, 0.03) join rr(-0.99, -0.42, -0.73, -0.15, 0.1) join rr(-0.91, -0.2, -0.81, 0.9, 0.05)
            val knife = shape { mv(0.78, -0.88); cu(0.98, -0.7, 1.0, -0.25, 0.92, -0.05); ln(0.92, 0.9); ln(0.8, 0.9) }
            plate join fork join knife
        }
        BadgeEmblem.FLAME -> {
            // Chama com três línguas (a do meio mais alta) e o miolo vazado.
            val outer = shape {
                mv(-0.05, -1.0); cu(0.35, -0.75, 0.3, -0.45, 0.33, -0.28); cu(0.45, -0.38, 0.55, -0.5, 0.58, -0.66)
                cu(0.88, -0.28, 0.86, 0.2, 0.76, 0.45); cu(0.6, 0.86, 0.3, 1.0, 0.0, 1.0)
                cu(-0.4, 1.0, -0.78, 0.75, -0.78, 0.3); cu(-0.78, -0.05, -0.62, -0.25, -0.52, -0.5)
                cu(-0.42, -0.28, -0.36, -0.2, -0.25, -0.14); cu(-0.32, -0.55, -0.25, -0.8, -0.05, -1.0)
            }
            val inner = shape { mv(0.02, 0.02); cu(0.1, 0.3, 0.42, 0.45, 0.36, 0.72); cu(0.3, 0.93, -0.3, 0.93, -0.36, 0.72); cu(-0.42, 0.5, -0.12, 0.38, 0.02, 0.02) }
            outer cut inner
        }
        BadgeEmblem.CALENDAR -> {
            var p = (rr(-0.85, -0.7, 0.85, 0.9, 0.15) cut rr(-0.68, -0.3, 0.68, 0.73, 0.06)) join
                rr(-0.52, -0.95, -0.34, -0.5, 0.08) join rr(0.34, -0.95, 0.52, -0.5, 0.08)
            for (row in 0..1) for (col in 0..2) {
                val l = -0.55 + col * 0.4
                val t = -0.15 + row * 0.42
                p = p join rr(l, t, l + 0.3, t + 0.3, 0.05)
            }
            p
        }
        BadgeEmblem.CROWN -> {
            val body = poly(-0.88, 0.5, -1.0, -0.45, -0.45, 0.02, 0.0, -0.72, 0.45, 0.02, 1.0, -0.45, 0.88, 0.5)
            (body join rr(-0.9, 0.6, 0.9, 0.85, 0.06) join circ(-1.0, -0.5, 0.12) join circ(0.0, -0.78, 0.13) join circ(1.0, -0.5, 0.12)) cut
                circ(0.0, 0.22, 0.14) cut circ(-0.5, 0.3, 0.09) cut circ(0.5, 0.3, 0.09)
        }
        BadgeEmblem.SHOE -> {
            val body = shape {
                mv(-0.95, 0.5); ln(-0.98, 0.0); cu(-0.98, -0.25, -0.85, -0.45, -0.65, -0.4); ln(-0.5, -0.2); ln(-0.25, -0.62); ln(0.0, -0.45)
                cu(0.25, -0.2, 0.55, -0.05, 0.8, 0.05); cu(1.0, 0.12, 1.02, 0.5, 0.85, 0.5)
            }
            body cut rr(-0.92, 0.26, 0.93, 0.34, 0.0) cut circ(-0.15, -0.35, 0.06) cut circ(0.05, -0.22, 0.06) cut circ(0.25, -0.1, 0.06)
        }
        BadgeEmblem.DUMBBELL -> {
            val p = rr(-0.62, -0.09, 0.62, 0.09, 0.04) join rr(-0.78, -0.55, -0.56, 0.55, 0.08) join rr(0.56, -0.55, 0.78, 0.55, 0.08) join
                rr(-0.96, -0.36, -0.78, 0.36, 0.07) join rr(0.78, -0.36, 0.96, 0.36, 0.07)
            p.transform(Matrix().apply { setRotate(-30f, cx, cy) })
            p
        }
        BadgeEmblem.BOLT -> poly(0.2, -1.0, -0.62, 0.12, -0.08, 0.12, -0.25, 1.0, 0.62, -0.18, 0.08, -0.18)
        BadgeEmblem.CLOCK -> {
            (circ(0.0, 0.1, 0.82) cut circ(0.0, 0.1, 0.65)) join rr(-0.17, -0.98, 0.17, -0.8, 0.05) join rr(-0.07, -0.84, 0.07, -0.68, 0.0) join
                rr(-0.06, -0.42, 0.06, 0.16, 0.05) join rr(-0.06, 0.04, 0.44, 0.16, 0.05) join circ(0.0, 0.1, 0.11) join
                rr(0.5, 0.06, 0.6, 0.14, 0.03) join rr(-0.6, 0.06, -0.5, 0.14, 0.03) join rr(-0.04, 0.58, 0.04, 0.68, 0.03)
        }
        BadgeEmblem.MOON -> (circ(-0.05, 0.05, 0.85) cut circ(0.35, -0.25, 0.7)) join star(0.42, -0.2, 0.22) join star(0.85, -0.7, 0.13)
        BadgeEmblem.SCALE -> {
            rr(-0.9, -0.75, 0.9, 0.9, 0.28) cut rr(-0.52, -0.52, 0.52, -0.05, 0.12) join poly(-0.05, -0.1, 0.05, -0.1, 0.25, -0.42) cut
                rr(-0.55, 0.12, -0.12, 0.68, 0.18) cut rr(0.12, 0.12, 0.55, 0.68, 0.18)
        }
        BadgeEmblem.TARGET -> (circ(0.0, 0.0, 0.95) cut circ(0.0, 0.0, 0.74)) join (circ(0.0, 0.0, 0.55) cut circ(0.0, 0.0, 0.35)) join circ(0.0, 0.0, 0.17)
        BadgeEmblem.TROPHY -> {
            val cup = shape {
                mv(-0.6, -0.85); ln(0.6, -0.85); ln(0.55, -0.2); cu(0.5, 0.15, 0.25, 0.3, 0.12, 0.32); ln(0.12, 0.55); ln(0.4, 0.62); ln(0.45, 0.85)
                ln(-0.45, 0.85); ln(-0.4, 0.62); ln(-0.12, 0.55); ln(-0.12, 0.32); cu(-0.25, 0.3, -0.5, 0.15, -0.55, -0.2)
            }
            (cup join (circ(-0.62, -0.45, 0.32) cut circ(-0.62, -0.45, 0.18)) join (circ(0.62, -0.45, 0.32) cut circ(0.62, -0.45, 0.18))) cut star(0.0, -0.38, 0.24)
        }
        BadgeEmblem.FLAG -> {
            val cloth = shape { mv(-0.64, -0.9); cu(-0.2, -1.1, 0.2, -0.6, 0.85, -0.85); ln(0.85, 0.05); cu(0.2, 0.3, -0.2, -0.2, -0.64, 0.0) }
            var p = rr(-0.78, -0.95, -0.64, 1.0, 0.05) join circ(-0.71, -0.95, 0.1) join cloth
            for ((l, t) in listOf(-0.5 to -0.62, -0.02 to -0.62, 0.46 to -0.62, -0.26 to -0.38, 0.22 to -0.38)) p = p cut rr(l, t, l + 0.24, t + 0.24, 0.0)
            p
        }
        BadgeEmblem.DROP -> {
            val drop = shape { mv(0.0, -1.0); cu(0.35, -0.5, 0.7, -0.1, 0.7, 0.3); cu(0.7, 0.72, 0.38, 1.0, 0.0, 1.0); cu(-0.38, 1.0, -0.7, 0.72, -0.7, 0.3); cu(-0.7, -0.1, -0.35, -0.5, 0.0, -1.0) }
            // Reflexo curvo do lado esquerdo.
            drop cut line(0.11, -0.42, 0.22, -0.44, 0.45, -0.32, 0.64, -0.12, 0.74)
        }
        BadgeEmblem.CAMERA -> {
            (rr(-0.95, -0.45, 0.95, 0.75, 0.18) join rr(-0.38, -0.72, 0.38, -0.3, 0.12)) cut circ(0.0, 0.15, 0.48) join circ(0.0, 0.15, 0.34) cut
                circ(-0.1, 0.05, 0.1) cut circ(0.68, -0.22, 0.08)
        }
        BadgeEmblem.COMPARE -> {
            val left = (rr(-0.95, -0.75, -0.08, 0.75, 0.12) cut rr(-0.82, -0.62, -0.21, 0.62, 0.06)) join circ(-0.515, -0.25, 0.15) join rr(-0.75, 0.0, -0.28, 0.62, 0.22)
            val right = rr(0.08, -0.75, 0.95, 0.75, 0.12) cut circ(0.515, -0.25, 0.17) cut rr(0.25, 0.0, 0.78, 0.62, 0.25)
            left join right
        }
        BadgeEmblem.FLASK -> {
            val flask = shape {
                mv(-0.32, -0.95); ln(0.32, -0.95); ln(0.32, -0.8); ln(0.2, -0.8); ln(0.2, -0.3); ln(0.85, 0.75)
                cu(0.92, 0.88, 0.85, 0.95, 0.72, 0.95); ln(-0.72, 0.95); cu(-0.85, 0.95, -0.92, 0.88, -0.85, 0.75); ln(-0.2, -0.3); ln(-0.2, -0.8); ln(-0.32, -0.8)
            }
            flask cut poly(-0.08, -0.74, 0.08, -0.74, 0.08, -0.25, 0.48, 0.3, -0.48, 0.3, -0.08, -0.25) cut
                circ(-0.22, 0.56, 0.08) cut circ(0.22, 0.66, 0.06) cut circ(0.05, 0.46, 0.05)
        }
        BadgeEmblem.CROSS -> (rr(-0.3, -0.9, 0.3, 0.9, 0.12) join rr(-0.9, -0.3, 0.9, 0.3, 0.12)) cut line(0.1, -0.55, 0.0, -0.2, 0.0, -0.05, -0.22, 0.1, 0.22, 0.25, 0.0, 0.55, 0.0)
        BadgeEmblem.CLIPBOARD -> {
            (rr(-0.7, -0.8, 0.7, 0.95, 0.12) cut rr(-0.55, -0.55, 0.55, 0.8, 0.05)) join rr(-0.3, -0.95, 0.3, -0.6, 0.08) join
                rr(-0.4, -0.3, 0.4, -0.18, 0.04) join rr(-0.4, 0.0, 0.4, 0.12, 0.04) join rr(-0.4, 0.3, 0.15, 0.42, 0.04)
        }
        BadgeEmblem.HEART -> {
            val heart = shape { mv(0.0, -0.45); cu(-0.2, -0.95, -1.0, -0.9, -1.0, -0.3); cu(-1.0, 0.2, -0.45, 0.55, 0.0, 0.92); cu(0.45, 0.55, 1.0, 0.2, 1.0, -0.3); cu(1.0, -0.9, 0.2, -0.95, 0.0, -0.45) }
            heart cut line(0.11, -0.7, 0.0, -0.3, 0.0, -0.15, -0.35, 0.05, 0.3, 0.2, -0.1, 0.7, -0.1)
        }
        BadgeEmblem.STAR -> (star(0.0, 0.08, 1.0) cut star(0.0, 0.08, 0.66)) join star(0.0, 0.08, 0.44)
    }
}

// ---------- Cache para a tela ----------

/** Medalhas já desenhadas, para a grade não redesenhar a cada rolagem (até ~8 MB). */
private val medalCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun medalKey(badge: Badge, earned: Boolean, palette: Palette, side: Int) =
    "${badge.key}|$earned|${palette.key}|${palette.accent.toArgb()}|${palette.background.toArgb()}|$side"

/** A medalha, se já estiver no cache (sem desenhar). */
fun cachedMedalImage(badge: Badge, earned: Boolean, palette: Palette, px: Int): ImageBitmap? =
    medalCache.get(medalKey(badge, earned, palette, px.coerceAtLeast(8)))

/** Medalha em [px] x [px] pixels, vinda do cache quando já foi desenhada com o mesmo tema. Pode rodar fora da thread principal. */
fun medalImage(badge: Badge, earned: Boolean, palette: Palette, px: Int): ImageBitmap {
    val side = px.coerceAtLeast(8)
    val key = medalKey(badge, earned, palette, side)
    medalCache.get(key)?.let { return it }
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    drawBadgeMedal(Canvas(bmp), side / 2f, side / 2f, side / 2f, badge, earned, palette)
    val img = bmp.asImageBitmap()
    medalCache.put(key, img)
    return img
}
