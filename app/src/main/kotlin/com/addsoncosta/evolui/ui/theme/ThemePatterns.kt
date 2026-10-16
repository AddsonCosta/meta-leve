package com.addsoncosta.evolui.ui.theme

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// Padrões decorativos dos temas, desenhados à mão com android.graphics (sem imagens).
// Tudo é medido em "u" (1/360 do lado menor), então o desenho fica igual em qualquer tamanho.
// Transparências baixas de propósito: o fundo não pode atrapalhar a leitura.

private const val TAU = 6.2831855f

/** Cor com transparência [a] (0 a 1), no formato do Canvas Android. */
internal fun Color.argb(a: Float): Int = copy(alpha = a.coerceIn(0f, 1f)).toArgb()

internal fun fillPaint(c: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = c
}

internal fun strokePaint(c: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    color = c
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

internal fun Random.f(from: Float, to: Float): Float = from + nextFloat() * (to - from)

/** Curva fechada suave passando perto dos pontos. */
internal fun smoothClosed(xs: FloatArray, ys: FloatArray): Path {
    val n = xs.size
    val p = Path()
    p.moveTo((xs[n - 1] + xs[0]) / 2f, (ys[n - 1] + ys[0]) / 2f)
    for (i in 0 until n) {
        val j = (i + 1) % n
        p.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
    }
    p.close()
    return p
}

/** Curva aberta suave do primeiro ao último ponto. */
internal fun smoothOpen(xs: FloatArray, ys: FloatArray): Path {
    val n = xs.size
    val p = Path()
    p.moveTo(xs[0], ys[0])
    if (n == 2) {
        p.lineTo(xs[1], ys[1])
        return p
    }
    for (i in 1 until n - 1) p.quadTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2f, (ys[i] + ys[i + 1]) / 2f)
    p.lineTo(xs[n - 1], ys[n - 1])
    return p
}

/** Mancha irregular em volta de (cx, cy). */
internal fun blobPath(cx: Float, cy: Float, r: Float, rnd: Random, wobble: Float, points: Int = 9, stretch: Float = 1f): Path {
    val xs = FloatArray(points)
    val ys = FloatArray(points)
    val rot = rnd.f(0f, TAU)
    for (i in 0 until points) {
        val t = rot + i * TAU / points
        val rad = r * (1f + wobble * (rnd.nextFloat() * 2f - 1f))
        xs[i] = cx + cos(t) * rad * stretch
        ys[i] = cy + sin(t) * rad
    }
    return smoothClosed(xs, ys)
}

/** Coração centrado em (cx, cy), com largura [s]. */
internal fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.38f)
    cubicTo(cx - s * 0.62f, cy - s * 0.02f, cx - s * 0.36f, cy - s * 0.52f, cx, cy - s * 0.2f)
    cubicTo(cx + s * 0.36f, cy - s * 0.52f, cx + s * 0.62f, cy - s * 0.02f, cx, cy + s * 0.38f)
    close()
}

/** Estrela de [points] pontas. */
internal fun starPath(cx: Float, cy: Float, r: Float, points: Int = 5, inner: Float = 0.45f): Path {
    val p = Path()
    for (i in 0 until points * 2) {
        val rad = if (i % 2 == 0) r else r * inner
        val t = -PI.toFloat() / 2f + i * PI.toFloat() / points
        val x = cx + cos(t) * rad
        val y = cy + sin(t) * rad
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** Folha com a base em (0,0) apontando para +x, de comprimento [len]. */
internal fun leafPath(len: Float, width: Float = 0.36f): Path = Path().apply {
    moveTo(0f, 0f)
    quadTo(len * 0.5f, -len * width, len, 0f)
    quadTo(len * 0.5f, len * width, 0f, 0f)
    close()
}

/** Desenha uma folha em (x, y) girada [deg] graus. */
internal fun Canvas.leaf(x: Float, y: Float, len: Float, deg: Float, paint: Paint) {
    save()
    translate(x, y)
    rotate(deg)
    drawPath(leafPath(len), paint)
    restore()
}

/** Brilho redondo que some nas bordas. */
internal fun Canvas.orb(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
    if (r <= 0f) return
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(cx, cy, r, color.argb(a), color.argb(0f), Shader.TileMode.CLAMP)
    drawCircle(cx, cy, r, p)
}

/** Fundo liso com um brilho discreto da cor principal no alto. */
internal fun drawBase(c: Canvas, w: Float, h: Float, p: Palette) {
    c.drawColor(p.background.toArgb())
    c.orb(w * 0.15f, 0f, max(w, h) * 0.7f, p.accent, if (p.dark) 0.07f else 0.05f)
}

/** Escolhe o padrão do tema. */
internal fun drawPattern(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    val rnd = Random(p.key.hashCode().toLong() * 31 + p.style.ordinal)
    when (p.style) {
        BackdropStyle.PLAIN -> Unit
        BackdropStyle.GLASS -> glass(c, w, h, p)
        BackdropStyle.SPORT -> sport(c, w, h, p, u, rnd)
        BackdropStyle.CLEAN -> clean(c, w, h, p, u)
        BackdropStyle.TOPO -> topo(c, w, h, p, u, rnd)
        BackdropStyle.GRID -> grid(c, w, h, p, u, rnd)
        BackdropStyle.RUGGED -> rugged(c, w, h, p, u)
        BackdropStyle.CAMO -> camo(c, w, h, p, u, rnd)
        BackdropStyle.NAVAL -> naval(c, w, h, p, u)
        BackdropStyle.AIR -> air(c, w, h, p, u)
        BackdropStyle.TACTICAL -> tactical(c, w, h, p, u, rnd)
        BackdropStyle.POP -> pop(c, w, h, p, u, rnd)
        BackdropStyle.KANDINSKY -> kandinsky(c, w, h, p, u, rnd)
        BackdropStyle.ROMANTIC -> romantic(c, w, h, p, u, rnd)
        BackdropStyle.BAROQUE -> baroque(c, w, h, p, u)
        BackdropStyle.ROCOCO -> rococo(c, w, h, p, u, rnd)
        BackdropStyle.GREEK -> greek(c, w, h, p, u)
        BackdropStyle.MARBLE -> marble(c, w, h, p, u, rnd)
    }
}

/** Vidro fosco: degradê e bolas de cor bem suaves, que aparecem através dos cartões translúcidos. */
private fun glass(c: Canvas, w: Float, h: Float, p: Palette) {
    val g = Paint()
    g.shader = LinearGradient(
        0f, 0f, w, h,
        p.background.toArgb(), lerp(p.background, p.accent4, 0.22f).toArgb(), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, w, h, g)
    val m = max(w, h)
    c.orb(w * 0.88f, h * 0.10f, m * 0.42f, p.accent, 0.30f)
    c.orb(w * 0.05f, h * 0.42f, m * 0.45f, p.accent4, 0.32f)
    c.orb(w * 0.95f, h * 0.76f, m * 0.38f, p.accent3, 0.22f)
    c.orb(w * 0.30f, h * 0.98f, m * 0.34f, p.accent2, 0.20f)
    // Reflexo diagonal, como luz batendo no vidro.
    val sheen = Paint()
    sheen.shader = LinearGradient(
        0f, h * 0.15f, w, h * 0.45f,
        intArrayOf(Color.White.argb(0f), Color.White.argb(0.05f), Color.White.argb(0f)),
        floatArrayOf(0.3f, 0.5f, 0.7f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, w, h, sheen)
}

/** Linhas de velocidade em diagonal. */
private fun sport(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val diag = hypot(w, h)
    c.save()
    c.rotate(-22f, w / 2f, h / 2f)
    c.drawRect(w / 2f - diag, h * 0.16f, w / 2f + diag, h * 0.16f + 70f * u, fillPaint(p.accent.argb(0.05f)))
    c.drawRect(w / 2f - diag, h * 0.16f + 78f * u, w / 2f + diag, h * 0.16f + 84f * u, fillPaint(p.accent.argb(0.07f)))
    val line = strokePaint(0, u)
    val count = (diag / (14f * u)).toInt().coerceIn(20, 160)
    repeat(count) {
        val y = h / 2f - diag / 2f + rnd.nextFloat() * diag
        val x = w / 2f - diag / 2f + rnd.nextFloat() * diag
        val len = rnd.f(50f * u, 240f * u)
        line.strokeWidth = rnd.f(1.2f * u, 5f * u)
        line.color = (if (rnd.nextInt(5) == 0) p.accent2 else p.accent).argb(rnd.f(0.03f, 0.10f))
        c.drawLine(x, y, x + len, y, line)
    }
    c.restore()
}

/** Limpo e suave: círculos de luz e uma onda no pé da tela. */
private fun clean(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    c.orb(w * 0.95f, h * 0.06f, w * 0.65f, p.accent, 0.12f)
    c.orb(0f, h * 0.45f, w * 0.6f, p.accent2, 0.08f)
    c.orb(w * 0.6f, h, w * 0.75f, p.accent, 0.07f)
    val wave = Path()
    val y0 = h * 0.86f
    wave.moveTo(0f, y0)
    wave.cubicTo(w * 0.3f, y0 - 40f * u, w * 0.6f, y0 + 30f * u, w, y0 - 20f * u)
    wave.lineTo(w, h)
    wave.lineTo(0f, h)
    wave.close()
    c.drawPath(wave, fillPaint(p.accent.argb(0.04f)))
}

/** Curvas de nível de mapa topográfico. */
private fun topo(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val thin = strokePaint(p.accent.argb(0.08f), 1.1f * u)
    val bold = strokePaint(p.accent.argb(0.14f), 1.7f * u)
    val n = 64
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    repeat(3) {
        val cx = rnd.f(0f, w)
        val cy = rnd.f(0f, h)
        val a1 = rnd.f(0.05f, 0.14f)
        val a2 = rnd.f(0.04f, 0.10f)
        val a3 = rnd.f(0.02f, 0.06f)
        val f1 = rnd.f(0f, TAU)
        val f2 = rnd.f(0f, TAU)
        val f3 = rnd.f(0f, TAU)
        val rings = 9 + rnd.nextInt(5)
        for (k in 1..rings) {
            val rad = k * 15f * u
            val grow = k / rings.toFloat()
            for (i in 0 until n) {
                val t = i * TAU / n
                val m = 1f + a1 * sin(2 * t + f1) + a2 * sin(3 * t + f2) + a3 * grow * sin(5 * t + f3)
                xs[i] = cx + cos(t) * rad * m * 1.25f
                ys[i] = cy + sin(t) * rad * m
            }
            c.drawPath(smoothClosed(xs, ys), if (k % 4 == 0) bold else thin)
        }
    }
}

/** Grade de mapa com uma rota tracejada e pontos de passagem. */
private fun grid(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val line = strokePaint(p.text.argb(0.035f), 0.8f * u)
    val step = 28f * u
    var x = step / 2f
    while (x < w) {
        c.drawLine(x, 0f, x, h, line)
        x += step
    }
    var y = step / 2f
    while (y < h) {
        c.drawLine(0f, y, w, y, line)
        y += step
    }
    val n = 9
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (i in 0 until n) {
        val f = i / (n - 1f)
        xs[i] = w * (0.08f + 0.84f * f) + if (i in 1 until n - 1) rnd.f(-40f, 40f) * u else 0f
        ys[i] = h * (0.88f - 0.76f * f) + if (i in 1 until n - 1) rnd.f(-30f, 30f) * u else 0f
    }
    val route = strokePaint(p.accent.argb(0.16f), 3f * u)
    route.pathEffect = DashPathEffect(floatArrayOf(10f * u, 7f * u), 0f)
    c.drawPath(smoothOpen(xs, ys), route)
    val dot = fillPaint(p.accent2.argb(0.16f))
    val ring = strokePaint(p.accent.argb(0.18f), 1.4f * u)
    for (i in 0 until n step 2) {
        c.drawCircle(xs[i], ys[i], 4f * u, dot)
        c.drawCircle(xs[i], ys[i], 9f * u, ring)
    }
    val rings = strokePaint(p.accent.argb(0.06f), 1f * u)
    for (k in 1..3) c.drawCircle(xs[n - 1], ys[n - 1], k * 24f * u, rings)
}

/** Robusto: textura de banda de rodagem, parafusos nos cantos e uma faixa laranja. */
private fun rugged(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    val tread = strokePaint(p.text.argb(0.035f), 2.2f * u)
    val step = 22f * u
    var row = 0
    var y = 0f
    while (y < h + step) {
        var x = if (row % 2 == 0) 0f else step / 2f
        while (x < w + step) {
            c.drawLine(x, y, x + 7f * u, y + 6f * u, tread)
            c.drawLine(x + 7f * u, y + 6f * u, x, y + 12f * u, tread)
            x += step
        }
        y += step
        row++
    }
    val screw = strokePaint(p.accent2.argb(0.16f), 1.6f * u)
    val inset = 22f * u
    listOf(inset to inset, w - inset to inset, inset to h - inset, w - inset to h - inset).forEach { (sx, sy) ->
        c.drawCircle(sx, sy, 8f * u, screw)
        c.drawLine(sx - 4f * u, sy - 4f * u, sx + 4f * u, sy + 4f * u, screw)
    }
    c.save()
    c.rotate(-35f, 0f, h)
    c.drawRect(-w, h - 120f * u, w * 2f, h - 104f * u, fillPaint(p.accent.argb(0.10f)))
    c.drawRect(-w, h - 98f * u, w * 2f, h - 94f * u, fillPaint(p.accent.argb(0.08f)))
    c.restore()
}

/** Camuflado de mata: manchas irregulares em tons de verde e terra. */
private fun camo(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val colors = listOf(
        fillPaint(p.accentDim.argb(0.40f)),
        fillPaint(p.accent3.argb(0.16f)),
        fillPaint(p.outline.argb(0.55f)),
        fillPaint(Color.Black.argb(0.18f)),
    )
    val cell = 64f * u
    var y = -cell / 2f
    while (y < h + cell) {
        var x = -cell / 2f
        while (x < w + cell) {
            repeat(1 + rnd.nextInt(2)) {
                val paint = colors[rnd.nextInt(colors.size)]
                c.drawPath(
                    blobPath(x + rnd.f(0f, cell), y + rnd.f(0f, cell), rnd.f(22f, 48f) * u, rnd, 0.38f, 9, rnd.f(1f, 1.8f)),
                    paint,
                )
            }
            x += cell
        }
        y += cell
    }
}

/** Naval: ondas na parte de baixo e um par de frisos dourados. */
private fun naval(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    val wave = strokePaint(0, 1.3f * u)
    var k = 0
    var y = h * 0.48f
    while (y < h + 20f * u) {
        val f = (y - h * 0.48f) / (h * 0.52f)
        wave.color = p.accent2.argb(0.03f + 0.05f * f)
        val len = 120f * u
        val amp = 5f * u
        // Cada linha começa num ponto diferente da onda, para não ficarem todas alinhadas.
        var x = -(k % 3) * len / 3f
        val path = Path()
        path.moveTo(x, y)
        while (x < w) {
            path.cubicTo(x + len * 0.25f, y - amp, x + len * 0.25f, y - amp, x + len * 0.5f, y)
            path.cubicTo(x + len * 0.75f, y + amp, x + len * 0.75f, y + amp, x + len, y)
            x += len
        }
        c.drawPath(path, wave)
        y += 16f * u
        k++
    }
    val gold = p.accent
    c.drawRect(0f, h * 0.42f, w, h * 0.42f + 3f * u, fillPaint(gold.argb(0.12f)))
    c.drawRect(0f, h * 0.42f + 7f * u, w, h * 0.42f + 8.2f * u, fillPaint(gold.argb(0.12f)))
    val star = fillPaint(gold.argb(0.10f))
    for (i in 0 until 5) c.drawPath(starPath(w * (0.12f + i * 0.19f), h * 0.42f - 18f * u, 5f * u), star)
}

/** Céu: rastros de avião em diagonal e uma formação em "V". */
private fun air(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    c.orb(w * 0.2f, h * 0.25f, w * 0.5f, p.accent, 0.05f)
    c.orb(w * 0.8f, h * 0.55f, w * 0.45f, p.accent4, 0.05f)
    val trails = listOf(0.30f, 0.62f, 0.92f)
    trails.forEach { t ->
        val x0 = -w * 0.1f
        val y0 = h * t
        val x1 = w * 1.1f
        val y1 = y0 - w * 0.55f
        for (j in 0..1) {
            val off = j * 7f * u
            val paint = strokePaint(0, 2f * u)
            paint.shader = LinearGradient(
                x0, y0 + off, x1, y1 + off,
                p.accent2.argb(0f), p.accent2.argb(0.16f), Shader.TileMode.CLAMP,
            )
            c.drawLine(x0, y0 + off, x1, y1 + off, paint)
        }
    }
    val wing = strokePaint(p.accent.argb(0.12f), 2f * u)
    val baseX = w * 0.72f
    val baseY = h * 0.16f
    val spots = listOf(0f to 0f, -1f to 1f, 1f to 1f, -2f to 2f, 2f to 2f)
    spots.forEach { (dx, dy) ->
        val x = baseX + dx * 22f * u
        val y = baseY + dy * 18f * u
        val path = Path()
        path.moveTo(x - 9f * u, y + 6f * u)
        path.lineTo(x, y)
        path.lineTo(x + 9f * u, y + 6f * u)
        c.drawPath(path, wing)
    }
}

/** Tático: camuflado digital em blocos cinza-escuros, bem discreto. */
private fun tactical(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val g = 9f * u
    val cols = (w / g).toInt() + 1
    val rows = (h / g).toInt() + 1
    val paints = listOf(
        fillPaint(p.outline.argb(0.30f)),
        fillPaint(p.surfaceHigh.argb(0.65f)),
        fillPaint(p.accentDim.argb(0.18f)),
    )
    val count = (cols * rows / 9).coerceAtMost(900)
    repeat(count) {
        val x = rnd.nextInt(cols) * g
        val y = rnd.nextInt(rows) * g
        val cw = (1 + rnd.nextInt(4)) * g
        val ch = (1 + rnd.nextInt(3)) * g
        c.drawRect(x, y, x + cw, y + ch, paints[rnd.nextInt(paints.size)])
    }
    // Cantoneiras de mira nos cantos.
    val mark = strokePaint(p.text.argb(0.10f), 1.6f * u)
    val m = 18f * u
    val l = 16f * u
    c.drawLine(m, m, m + l, m, mark); c.drawLine(m, m, m, m + l, mark)
    c.drawLine(w - m, m, w - m - l, m, mark); c.drawLine(w - m, m, w - m, m + l, mark)
    c.drawLine(m, h - m, m + l, h - m, mark); c.drawLine(m, h - m, m, h - m - l, mark)
    c.drawLine(w - m, h - m, w - m - l, h - m, mark); c.drawLine(w - m, h - m, w - m, h - m - l, mark)
}

/** Pop colorido: corações, círculos e flores com contorno preto grosso e recheios listrados ou de bolinhas. */
private fun pop(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val colors = listOf(p.accent, p.accent2, p.accent3, p.accent4, Color(0xFFFFD400))
    val outline = strokePaint(Color.Black.argb(0.20f), 3f * u)
    val inner = strokePaint(Color.Black.argb(0.10f), 1.6f * u)
    val cell = 88f * u
    var row = 0
    var y = 0f
    while (y < h + cell) {
        var x = if (row % 2 == 0) 0f else cell / 2f
        while (x < w + cell) {
            if (rnd.nextFloat() < 0.6f) {
                val cx = x + rnd.f(-12f, 12f) * u
                val cy = y + rnd.f(-12f, 12f) * u
                val s = rnd.f(34f, 54f) * u
                val color = colors[rnd.nextInt(colors.size)]
                val shape = when (rnd.nextInt(4)) {
                    0, 1 -> heartPath(cx, cy, s)
                    2 -> Path().apply { addCircle(cx, cy, s * 0.4f, Path.Direction.CW) }
                    else -> starPath(cx, cy, s * 0.5f, 6, 0.6f)
                }
                c.save()
                c.rotate(rnd.f(-18f, 18f), cx, cy)
                c.drawPath(shape, fillPaint(color.argb(0.16f)))
                c.save()
                c.clipPath(shape)
                if (rnd.nextBoolean()) {
                    var sx = cx - s
                    while (sx < cx + s) {
                        c.drawLine(sx, cy - s, sx + s * 0.6f, cy + s, inner)
                        sx += 7f * u
                    }
                } else {
                    val dot = fillPaint(Color.Black.argb(0.09f))
                    var dy = cy - s
                    while (dy < cy + s) {
                        var dx = cx - s
                        while (dx < cx + s) {
                            c.drawCircle(dx, dy, 1.8f * u, dot)
                            dx += 8f * u
                        }
                        dy += 8f * u
                    }
                }
                c.restore()
                c.drawPath(shape, outline)
                c.restore()
            }
            x += cell
        }
        y += cell * 0.85f
        row++
    }
}

/** Abstrato geométrico: círculos concêntricos, linhas cruzadas, triângulo e xadrez. */
private fun kandinsky(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val ink = Color(0xFF1C1A16)
    val colors = listOf(p.accent, p.accent2, p.accent3, p.accent4)
    // Círculos concêntricos.
    val cx = w * 0.80f
    val cy = h * 0.22f
    listOf(70f, 52f, 34f, 16f).forEachIndexed { i, r ->
        c.drawCircle(cx, cy, r * u, fillPaint(colors[i % colors.size].argb(0.12f)))
    }
    c.drawCircle(cx, cy, 70f * u, strokePaint(ink.argb(0.16f), 1.6f * u))
    // Círculos soltos.
    repeat(9) {
        val x = rnd.f(0f, w)
        val y = rnd.f(0f, h)
        val r = rnd.f(8f, 46f) * u
        c.drawCircle(x, y, r, fillPaint(colors[rnd.nextInt(colors.size)].argb(rnd.f(0.08f, 0.15f))))
        if (rnd.nextBoolean()) c.drawCircle(x, y, r, strokePaint(ink.argb(0.14f), 1.2f * u))
    }
    // Linhas retas atravessando.
    repeat(7) {
        val line = strokePaint(ink.argb(rnd.f(0.10f, 0.18f)), rnd.f(1f, 4f) * u)
        line.strokeCap = Paint.Cap.BUTT
        c.drawLine(rnd.f(-0.2f, 1.2f) * w, rnd.f(0f, 1f) * h, rnd.f(-0.2f, 1.2f) * w, rnd.f(0f, 1f) * h, line)
    }
    // Triângulo.
    val tri = Path()
    val tx = w * 0.22f
    val ty = h * 0.62f
    tri.moveTo(tx, ty - 60f * u)
    tri.lineTo(tx + 52f * u, ty + 30f * u)
    tri.lineTo(tx - 52f * u, ty + 30f * u)
    tri.close()
    c.drawPath(tri, fillPaint(p.accent3.argb(0.13f)))
    c.drawPath(tri, strokePaint(ink.argb(0.14f), 1.4f * u))
    // Xadrez pequeno.
    val q = 9f * u
    val qx = w * 0.62f
    val qy = h * 0.48f
    for (i in 0 until 4) for (j in 0 until 4) {
        if ((i + j) % 2 == 0) c.drawRect(qx + i * q, qy + j * q, qx + (i + 1) * q, qy + (j + 1) * q, fillPaint(ink.argb(0.12f)))
    }
    // Meio círculo.
    c.drawArc(RectF(w * 0.05f, h * 0.08f, w * 0.05f + 90f * u, h * 0.08f + 90f * u), 180f, 180f, false, strokePaint(p.accent2.argb(0.18f), 3f * u))
}

/** Romântico: manchas de aquarela sobrepostas em rosa e lavanda. */
private fun romantic(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val colors = listOf(p.accent, p.accent2, p.accent3, p.accent2)
    repeat(14) {
        val x = rnd.f(-0.1f, 1.1f) * w
        val y = rnd.f(-0.05f, 1.05f) * h
        val r = rnd.f(40f, 110f) * u
        val color = colors[rnd.nextInt(colors.size)]
        // Três camadas levemente deslocadas imitam a borda mais escura da aquarela.
        for (layer in 0 until 3) {
            val k = 1f - layer * 0.18f
            c.drawPath(blobPath(x + rnd.f(-6f, 6f) * u, y + rnd.f(-6f, 6f) * u, r * k, rnd, 0.22f, 11), fillPaint(color.argb(0.035f)))
        }
    }
    val petal = fillPaint(p.accent.argb(0.08f))
    repeat(18) {
        c.leaf(rnd.f(0f, w), rnd.f(0f, h), rnd.f(6f, 12f) * u, rnd.f(0f, 360f), petal)
    }
}

/** Voluta (espiral com cauda): base dos ornamentos barroco e rococó. Espiral em (0,0), cauda para +x. */
private fun scrollPath(size: Float, turns: Float = 1.8f): Path {
    val path = Path()
    val rMax = size * 0.28f
    path.moveTo(size, -size * 0.08f)
    path.cubicTo(size * 0.7f, -size * 0.45f, size * 0.25f, -size * 0.42f, 0f, -rMax)
    val steps = 48
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        val ang = -PI.toFloat() / 2f - f * turns * TAU
        val rad = rMax * (1f - f * 0.85f)
        path.lineTo(cos(ang) * rad, sin(ang) * rad)
    }
    return path
}

/** Ornamento de canto: duas volutas espelhadas e folhas. (x, y) é o canto; sx/sy (±1) dizem para que lado cresce. */
private fun cornerOrnament(c: Canvas, x: Float, y: Float, sx: Float, sy: Float, size: Float, line: Paint, thin: Paint, leaf: Paint) {
    c.save()
    c.translate(x, y)
    c.scale(sx, sy)
    c.save()
    c.translate(size * 0.35f, size * 0.38f)
    c.drawPath(scrollPath(size), line)
    c.restore()
    c.save()
    c.translate(size * 0.38f, size * 0.35f)
    c.rotate(90f)
    c.scale(1f, -1f)
    c.drawPath(scrollPath(size * 0.8f), thin)
    c.restore()
    c.leaf(size * 0.5f, size * 0.5f, size * 0.32f, 45f, leaf)
    c.leaf(size * 0.5f, size * 0.5f, size * 0.22f, 15f, leaf)
    c.leaf(size * 0.5f, size * 0.5f, size * 0.22f, 75f, leaf)
    c.restore()
}

/** Barroco: damasco dourado bem fraco, moldura e volutas nos cantos. */
private fun baroque(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    val gold = p.accent
    val motif = fillPaint(gold.argb(0.055f))
    val step = 72f * u
    var row = 0
    var y = step / 2f
    while (y < h + step) {
        var x = if (row % 2 == 0) step / 2f else 0f
        while (x < w + step) {
            for (k in 0 until 4) c.leaf(x, y, 11f * u, k * 90f + 45f, motif)
            c.drawCircle(x, y, 2.2f * u, motif)
            x += step
        }
        y += step / 2f
        row++
    }
    c.drawRect(10f * u, 10f * u, w - 10f * u, h - 10f * u, strokePaint(gold.argb(0.12f), 1.2f * u))
    c.drawRect(15f * u, 15f * u, w - 15f * u, h - 15f * u, strokePaint(gold.argb(0.08f), 0.6f * u))
    val line = strokePaint(gold.argb(0.20f), 2f * u)
    val thin = strokePaint(gold.argb(0.14f), 1.3f * u)
    val leaf = fillPaint(gold.argb(0.12f))
    val s = 110f * u
    cornerOrnament(c, 0f, 0f, 1f, 1f, s, line, thin, leaf)
    cornerOrnament(c, w, 0f, -1f, 1f, s, line, thin, leaf)
    cornerOrnament(c, 0f, h, 1f, -1f, s, line, thin, leaf)
    cornerOrnament(c, w, h, -1f, -1f, s, line, thin, leaf)
}

/** Rococó: treliça leve, florzinhas, volutas douradas finas e uma concha. */
private fun rococo(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val gold = p.accent3
    val lattice = strokePaint(gold.argb(0.06f), 0.9f * u)
    val step = 46f * u
    var d = -h
    while (d < w + h) {
        c.drawLine(d, 0f, d + h, h, lattice)
        c.drawLine(d + h, 0f, d, h, lattice)
        d += step
    }
    // Flores nos cruzamentos, salteadas.
    val mint = fillPaint(p.accent.argb(0.14f))
    val pink = fillPaint(p.accent2.argb(0.12f))
    val center = fillPaint(gold.argb(0.25f))
    var y = 0f
    var row = 0
    while (y < h + step) {
        var x = if (row % 2 == 0) 0f else step / 2f
        while (x < w + step) {
            if (rnd.nextFloat() < 0.35f) {
                val petals = if (rnd.nextBoolean()) mint else pink
                for (k in 0 until 5) {
                    val t = k * TAU / 5f
                    c.drawCircle(x + cos(t) * 4f * u, y + sin(t) * 4f * u, 3f * u, petals)
                }
                c.drawCircle(x, y, 1.6f * u, center)
            }
            x += step
        }
        y += step / 2f
        row++
    }
    val line = strokePaint(gold.argb(0.30f), 1.4f * u)
    val thin = strokePaint(gold.argb(0.22f), 1f * u)
    val leaf = fillPaint(p.accent.argb(0.12f))
    cornerOrnament(c, 0f, 0f, 1f, 1f, 90f * u, line, thin, leaf)
    cornerOrnament(c, w, 0f, -1f, 1f, 90f * u, line, thin, leaf)
    // Concha no canto de baixo.
    val bx = 70f * u
    val by = h - 30f * u
    val rays = 9
    val shell = Path()
    val shellR = 48f * u
    for (i in 0..rays) {
        val t = PI.toFloat() + i * PI.toFloat() / rays
        val ex = bx + cos(t) * shellR
        val ey = by + sin(t) * shellR
        c.drawLine(bx, by, ex, ey, thin)
        if (i == 0) shell.moveTo(ex, ey) else {
            val mt = t - PI.toFloat() / rays / 2f
            shell.quadTo(bx + cos(mt) * shellR * 1.18f, by + sin(mt) * shellR * 1.18f, ex, ey)
        }
    }
    c.drawPath(shell, line)
}

/** Grega (meandro) dentro de uma faixa horizontal de altura [band], começando em (0,0). */
private fun meander(c: Canvas, length: Float, band: Float, paint: Paint) {
    val s = band / 6f
    c.drawLine(0f, 0f, length, 0f, paint)
    c.drawLine(0f, band, length, band, paint)
    val path = Path()
    var x0 = s
    fun gx(i: Int) = x0 + i * s
    fun gy(j: Int) = s + j * s
    while (x0 < length - 4 * s) {
        path.moveTo(gx(0), gy(4))
        path.lineTo(gx(0), gy(0))
        path.lineTo(gx(3), gy(0))
        path.lineTo(gx(3), gy(3))
        path.lineTo(gx(1), gy(3))
        path.lineTo(gx(1), gy(1))
        path.lineTo(gx(2), gy(1))
        path.moveTo(gx(0), gy(4))
        path.lineTo(gx(4), gy(4))
        x0 += 4 * s
    }
    c.drawPath(path, paint)
}

/** Clássico grego: faixas de grega nas quatro bordas e caneluras de coluna bem fracas. */
private fun greek(c: Canvas, w: Float, h: Float, p: Palette, u: Float) {
    val paint = strokePaint(p.accent.argb(0.18f), 1.6f * u)
    paint.strokeCap = Paint.Cap.SQUARE
    paint.strokeJoin = Paint.Join.MITER
    val band = 15f * u
    val inset = 5f * u
    c.save(); c.translate(0f, inset); meander(c, w, band, paint); c.restore()
    c.save(); c.translate(0f, h - inset - band); meander(c, w, band, paint); c.restore()
    // Laterais: a mesma faixa girada 90 graus.
    c.save(); c.translate(inset + band, 0f); c.rotate(90f); meander(c, h, band, paint); c.restore()
    c.save(); c.translate(w - inset, 0f); c.rotate(90f); meander(c, h, band, paint); c.restore()
    val flute = strokePaint(p.accent.argb(0.04f), 3f * u)
    var x = w * 0.18f
    while (x < w * 0.82f) {
        c.drawLine(x, h * 0.08f, x, h * 0.92f, flute)
        x += 46f * u
    }
}

/** Uma veia de mármore: caminho aleatório suave, com ramificações. */
private fun vein(
    c: Canvas, w: Float, h: Float, u: Float, rnd: Random,
    x0: Float, y0: Float, angle: Float, steps: Int, color: Color, alpha: Float, width: Float, depth: Int,
) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var x = x0
    var y = y0
    var a = angle
    xs.add(x); ys.add(y)
    val seg = 14f * u
    for (i in 0 until steps) {
        a += rnd.f(-0.35f, 0.35f)
        a = a * 0.88f + angle * 0.12f
        x += cos(a) * seg
        y += sin(a) * seg
        xs.add(x); ys.add(y)
        if (depth > 0 && rnd.nextInt(8) == 0) {
            val side = if (rnd.nextBoolean()) 0.7f else -0.7f
            vein(c, w, h, u, rnd, x, y, a + side, steps / 3, color, alpha * 0.8f, width * 0.6f, depth - 1)
        }
        if (x < -40f * u || x > w + 40f * u || y < -40f * u || y > h + 40f * u) break
    }
    if (xs.size < 2) return
    val path = smoothOpen(xs.toFloatArray(), ys.toFloatArray())
    c.drawPath(path, strokePaint(color.argb(alpha * 0.3f), width * 3.5f))
    c.drawPath(path, strokePaint(color.argb(alpha), width))
}

/** Mármore: nuvens suaves e veios finos (cinza no claro, branco no escuro), um deles dourado. */
private fun marble(c: Canvas, w: Float, h: Float, p: Palette, u: Float, rnd: Random) {
    val cloud = if (p.dark) Color.White else Color(0xFF6B6B6B)
    repeat(6) { c.orb(rnd.f(0f, w), rnd.f(0f, h), rnd.f(80f, 180f) * u, cloud, 0.04f) }
    val veinColor = if (p.dark) Color.White else Color(0xFF5A5A5A)
    val steps = ((w + h) / (14f * u)).toInt()
    repeat(7) { i ->
        val fromLeft = rnd.nextBoolean()
        val x0 = if (fromLeft) -10f * u else rnd.f(0f, w)
        val y0 = if (fromLeft) rnd.f(0f, h) else -10f * u
        val angle = rnd.f(0.35f, 1.1f)
        val gold = i == 3
        vein(
            c, w, h, u, rnd, x0, y0, angle, steps,
            if (gold) p.accent else veinColor,
            if (gold) 0.22f else rnd.f(0.10f, 0.20f),
            rnd.f(0.6f, 1.4f) * u, 2,
        )
    }
}
