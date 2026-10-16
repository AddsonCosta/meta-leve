package com.addsoncosta.evolui.ui.theme

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Ilustrações de cada seção, num canto do fundo, quase transparentes e na cor do tema.
// Desenhadas numa caixa de 100 x 100 que é escalada para o tamanho final.

/** Tintas da ilustração: [fill]/[line] na cor principal, [fill2]/[line2] na secundária. */
private class Art(val fill: Paint, val line: Paint, val fill2: Paint, val line2: Paint)

/** Desenha a ilustração de [section] no canto de baixo à direita. [artAlpha] (0 a 1) atenua tudo. */
internal fun drawSectionArt(c: Canvas, w: Float, h: Float, p: Palette, section: Section, artAlpha: Float) {
    val k = artAlpha.coerceIn(0f, 1f)
    if (k <= 0f) return
    val s = min(w, h) * 0.62f
    val cx = w - s * 0.40f
    val cy = if (h > w * 1.3f) h * 0.70f else h - s * 0.52f
    val art = Art(
        fill = fillPaint(p.accent.argb(0.08f * k)),
        line = strokePaint(p.accent.argb(0.17f * k), 2.4f),
        fill2 = fillPaint(p.accent2.argb(0.08f * k)),
        line2 = strokePaint(p.accent2.argb(0.15f * k), 2.4f),
    )
    c.save()
    c.translate(cx - s / 2f, cy - s / 2f)
    c.scale(s / 100f, s / 100f)
    when (section) {
        Section.TODAY -> today(c, art)
        Section.FOOD -> food(c, art)
        Section.EXERCISE -> exercise(c, art)
        Section.FASTING -> fasting(c, art)
        Section.HEALTH -> health(c, art)
        Section.PROGRESS -> progress(c, art)
        Section.BADGES -> badges(c, art)
        Section.SETTINGS -> settings(c, art)
    }
    c.restore()
}

private fun Canvas.both(path: Path, fill: Paint, line: Paint) {
    drawPath(path, fill)
    drawPath(path, line)
}

/** Sol com raios dentro de um anel de progresso. */
private fun today(c: Canvas, a: Art) {
    c.drawCircle(50f, 50f, 18f, a.fill)
    c.drawCircle(50f, 50f, 18f, a.line)
    for (i in 0 until 12) {
        val t = i * PI.toFloat() / 6f
        c.drawLine(50f + cos(t) * 25f, 50f + sin(t) * 25f, 50f + cos(t) * 33f, 50f + sin(t) * 33f, a.line)
    }
    val ring = RectF(8f, 8f, 92f, 92f)
    c.drawArc(ring, 0f, 360f, false, strokePaint(a.line2.color, 1.2f))
    c.drawArc(ring, -90f, 260f, false, strokePaint(a.line2.color, 5f))
}

/** Prato com garfo e uma folha. */
private fun food(c: Canvas, a: Art) {
    c.drawCircle(56f, 54f, 34f, a.fill)
    c.drawCircle(56f, 54f, 34f, a.line)
    c.drawCircle(56f, 54f, 24f, a.line)
    // Garfo.
    c.drawLine(12f, 40f, 12f, 92f, a.line)
    for (x in listOf(6f, 12f, 18f)) c.drawLine(x, 10f, x, 30f, a.line)
    val neck = Path().apply {
        moveTo(6f, 30f)
        quadTo(6f, 40f, 12f, 40f)
        quadTo(18f, 40f, 18f, 30f)
    }
    c.drawPath(neck, a.line)
    // Folha no prato.
    c.save()
    c.translate(42f, 66f)
    c.rotate(-40f)
    val leaf = leafPath(34f, 0.42f)
    c.both(leaf, a.fill2, a.line2)
    c.drawLine(0f, 0f, 30f, 0f, a.line2)
    c.restore()
}

/** Corredor em movimento, com linhas de velocidade. */
private fun exercise(c: Canvas, a: Art) {
    val body = strokePaint(a.line.color, 6f)
    c.drawCircle(62f, 16f, 8f, a.fill)
    c.drawCircle(62f, 16f, 8f, a.line)
    c.drawLine(58f, 28f, 50f, 54f, body)
    c.drawLine(57f, 32f, 70f, 42f, body)
    c.drawLine(70f, 42f, 80f, 35f, body)
    c.drawLine(56f, 32f, 42f, 38f, body)
    c.drawLine(42f, 38f, 35f, 49f, body)
    c.drawLine(50f, 54f, 64f, 68f, body)
    c.drawLine(64f, 68f, 61f, 87f, body)
    c.drawLine(50f, 54f, 39f, 70f, body)
    c.drawLine(39f, 70f, 22f, 73f, body)
    for ((y, len) in listOf(30f to 22f, 44f to 30f, 58f to 18f)) c.drawLine(4f, y, 4f + len, y, a.line2)
}

/** Relógio com a lua crescente. */
private fun fasting(c: Canvas, a: Art) {
    c.drawCircle(46f, 56f, 34f, a.fill)
    c.drawCircle(46f, 56f, 34f, a.line)
    for (i in 0 until 12) {
        val t = i * PI.toFloat() / 6f
        val r0 = if (i % 3 == 0) 25f else 28f
        c.drawLine(46f + cos(t) * r0, 56f + sin(t) * r0, 46f + cos(t) * 31f, 56f + sin(t) * 31f, a.line)
    }
    val hand = strokePaint(a.line.color, 4f)
    c.drawLine(46f, 56f, 46f, 36f, hand)
    c.drawLine(46f, 56f, 60f, 64f, hand)
    val moon = Path().apply { addCircle(80f, 20f, 14f, Path.Direction.CW) }
    val bite = Path().apply { addCircle(87f, 14f, 12f, Path.Direction.CW) }
    moon.op(bite, Path.Op.DIFFERENCE)
    c.both(moon, a.fill2, a.line2)
    c.drawPath(starPath(64f, 10f, 3.5f), a.fill2)
}

/** Coração com linha de batimento. */
private fun health(c: Canvas, a: Art) {
    c.both(heartPath(50f, 52f, 76f), a.fill, a.line)
    val pulse = Path().apply {
        moveTo(2f, 52f)
        lineTo(30f, 52f)
        lineTo(38f, 36f)
        lineTo(47f, 70f)
        lineTo(55f, 42f)
        lineTo(61f, 52f)
        lineTo(98f, 52f)
    }
    c.drawPath(pulse, strokePaint(a.line2.color, 3.2f))
}

/** Montanha com bandeira no topo e um gráfico subindo. */
private fun progress(c: Canvas, a: Art) {
    val mountain = Path().apply {
        moveTo(4f, 92f)
        lineTo(36f, 44f)
        lineTo(50f, 60f)
        lineTo(70f, 28f)
        lineTo(96f, 92f)
        close()
    }
    c.both(mountain, a.fill, a.line)
    c.drawLine(70f, 28f, 70f, 10f, a.line)
    val flag = Path().apply {
        moveTo(70f, 10f)
        lineTo(84f, 15f)
        lineTo(70f, 20f)
        close()
    }
    c.both(flag, a.fill2, a.line2)
    val chart = Path().apply {
        moveTo(8f, 80f)
        lineTo(28f, 66f)
        lineTo(44f, 72f)
        lineTo(62f, 50f)
        lineTo(86f, 36f)
    }
    c.drawPath(chart, strokePaint(a.line2.color, 3.2f))
    val head = Path().apply {
        moveTo(78f, 34f)
        lineTo(88f, 35f)
        lineTo(84f, 44f)
    }
    c.drawPath(head, strokePaint(a.line2.color, 3.2f))
}

/** Medalha com fita e louros. */
private fun badges(c: Canvas, a: Art) {
    val ribbon = Path().apply {
        moveTo(30f, 4f)
        lineTo(44f, 4f)
        lineTo(54f, 40f)
        lineTo(44f, 42f)
        close()
        moveTo(70f, 4f)
        lineTo(56f, 4f)
        lineTo(46f, 40f)
        lineTo(56f, 42f)
        close()
    }
    c.both(ribbon, a.fill2, a.line2)
    c.drawCircle(50f, 62f, 22f, a.fill)
    c.drawCircle(50f, 62f, 22f, a.line)
    c.drawCircle(50f, 62f, 16f, a.line)
    c.both(starPath(50f, 62f, 10f), a.fill, a.line)
    // Louros: folhas ao longo de um arco dos dois lados.
    for (i in 0 until 6) {
        val deg = 115f + i * 22f
        val t = deg * PI.toFloat() / 180f
        val x = 50f + cos(t) * 32f
        val y = 62f + sin(t) * 32f
        c.leaf(x, y, 10f, deg + 90f + 25f, a.fill2)
        val mt = (180f - deg) * PI.toFloat() / 180f
        c.leaf(50f + cos(mt) * 32f, 62f + sin(mt) * 32f, 10f, 180f - deg - 90f - 25f, a.fill2)
    }
}

/** Engrenagem com [teeth] dentes; furo no meio. */
private fun gearPath(cx: Float, cy: Float, r: Float, teeth: Int): Path {
    val p = Path()
    val n = teeth * 4
    for (i in 0 until n) {
        val t = i * 2f * PI.toFloat() / n
        val rad = if (i % 4 == 1 || i % 4 == 2) r else r * 0.8f
        val x = cx + cos(t) * rad
        val y = cy + sin(t) * rad
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    p.addCircle(cx, cy, r * 0.32f, Path.Direction.CCW)
    p.fillType = Path.FillType.EVEN_ODD
    return p
}

/** Duas engrenagens. */
private fun settings(c: Canvas, a: Art) {
    c.both(gearPath(42f, 60f, 30f, 9), a.fill, a.line)
    c.both(gearPath(80f, 22f, 15f, 7), a.fill2, a.line2)
}
