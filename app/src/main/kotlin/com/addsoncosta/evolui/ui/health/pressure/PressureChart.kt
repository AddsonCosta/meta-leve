package com.addsoncosta.evolui.ui.health.pressure

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.addsoncosta.evolui.data.BloodPressureEntity
import com.addsoncosta.evolui.ui.components.ShortDateFormat
import com.addsoncosta.evolui.ui.theme.Evolui

/**
 * Linhas da sistólica e da diastólica ao longo do tempo, com as linhas de 140 e 90 mmHg
 * (início da hipertensão estágio 1) tracejadas. [points] em ordem de data.
 */
@Composable
internal fun PressureChart(points: List<BloodPressureEntity>, sysColor: Color, diaColor: Color, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val limitColor = Evolui.Red.copy(alpha = 0.6f)
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val padL = 72f
        val padR = 16f
        val padT = 16f
        val padB = 44f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        if (w <= 0f || h <= 0f) return@Canvas

        var minV = minOf(points.minOf { it.diastolic }, 70) - 10
        var maxV = maxOf(points.maxOf { it.systolic }, 150) + 10
        minV = minV.coerceAtLeast(0)
        if (maxV <= minV) maxV = minV + 10
        val first = points.first().epochMillis
        val last = points.last().epochMillis
        fun x(i: Int, millis: Long): Float = when {
            points.size == 1 -> padL + w / 2
            last == first -> padL + w * i / (points.size - 1)
            else -> padL + w * ((millis - first).toFloat() / (last - first).toFloat())
        }
        fun y(v: Int): Float = padT + h * (1f - (v - minV).toFloat() / (maxV - minV).toFloat())

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }

        // Grade: 80, 120 (linhas finas) e os limites 90 e 140 (tracejados).
        listOf(80, 120).filter { it in minV..maxV }.forEach { v ->
            drawLine(gridColor.copy(alpha = 0.5f), Offset(padL, y(v)), Offset(padL + w, y(v)), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText("$v", padL - 12f, y(v) + 10f, label)
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
        listOf(90, 140).filter { it in minV..maxV }.forEach { v ->
            drawLine(limitColor, Offset(padL, y(v)), Offset(padL + w, y(v)), strokeWidth = 2.5f, pathEffect = dash)
            drawContext.canvas.nativeCanvas.drawText("$v", padL - 12f, y(v) + 10f, label)
        }
        drawLine(gridColor, Offset(padL, padT + h), Offset(padL + w, padT + h), strokeWidth = 2f)

        fun series(color: Color, value: (BloodPressureEntity) -> Int) {
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val px = x(i, p.epochMillis)
                    val py = y(value(p))
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, color, style = Stroke(width = 5f))
            }
            val r = if (points.size > 40) 5f else 9f
            points.forEachIndexed { i, p -> drawCircle(color, radius = r, center = Offset(x(i, p.epochMillis), y(value(p)))) }
        }
        series(sysColor) { it.systolic }
        series(diaColor) { it.diastolic }

        // Datas da primeira e da última medida.
        val dates = Paint(label).apply { textAlign = Paint.Align.LEFT }
        val firstText = dateTimeOf(first).toLocalDate().format(ShortDateFormat)
        drawContext.canvas.nativeCanvas.drawText(firstText, padL, padT + h + 34f, dates)
        if (points.size > 1 && last != first) {
            val lastText = dateTimeOf(last).toLocalDate().format(ShortDateFormat)
            dates.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(lastText, padL + w, padT + h + 34f, dates)
        }
    }
}
