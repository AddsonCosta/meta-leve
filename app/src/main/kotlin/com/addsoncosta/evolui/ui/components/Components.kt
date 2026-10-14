package com.addsoncosta.evolui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

val PtBr: Locale = Locale.forLanguageTag("pt-BR")
val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", PtBr)

fun Double.fmt(decimals: Int = 0): String = NumberFormat.getNumberInstance(PtBr).apply {
    maximumFractionDigits = decimals
    minimumFractionDigits = 0
}.format(this)

/** Converte texto digitado com vírgula ou ponto. */
fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onValueChange(new) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        isError = value.isNotEmpty() && value.toDecimal() == null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Medidor semicircular do índice de desempenho (0 a 150%). */
@Composable
fun PerformanceGauge(percent: Double, size: Dp = 180.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        percent in 85.0..115.0 -> Color(0xFF2E9E5B)
        percent in 60.0..140.0 -> Color(0xFFF2A541)
        else -> Color(0xFFD9534F)
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size, size / 2 + 16.dp)) {
            val stroke = 18.dp.toPx()
            val diameter = this.size.width - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = (percent.coerceIn(0.0, 150.0) / 150.0 * 180).toFloat()
            drawArc(color, 180f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // Marca dos 100%.
            val angle = Math.toRadians(180.0 + 120.0)
            val r = diameter / 2
            val center = Offset(topLeft.x + r, topLeft.y + r)
            val inner = Offset(center.x + ((r - stroke) * Math.cos(angle)).toFloat(), center.y + ((r - stroke) * Math.sin(angle)).toFloat())
            val outer = Offset(center.x + ((r + stroke) * Math.cos(angle)).toFloat(), center.y + ((r + stroke) * Math.sin(angle)).toFloat())
            drawLine(Color.Gray, inner, outer, strokeWidth = 2.dp.toPx())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.BottomCenter)) {
            Text("${percent.fmt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("da meta do dia", style = MaterialTheme.typography.bodySmall)
        }
    }
}
