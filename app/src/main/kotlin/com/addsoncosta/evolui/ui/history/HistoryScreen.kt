package com.addsoncosta.evolui.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.fmt
import java.time.LocalDate

@Composable
fun HistoryScreen() {
    val container = appContainer()
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Peso", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Início ${s.profile.startWeightKg.fmt(1)} kg · Atual ${s.currentWeight.fmt(1)} kg · Meta ${s.profile.targetWeightKg.fmt(1)} kg",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val sign = if (s.changeKg > 0) "+" else ""
                    Text("Variação desde ${s.profile.startDate.format(DateFormat)}: $sign${s.changeKg.fmt(1)} kg")
                    if (s.weights.size >= 2) {
                        WeightChart(s.weights, s.profile.targetWeightKg)
                    } else {
                        Text("Registre o peso em pelo menos dois dias para ver o gráfico.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resumo da dieta", fontWeight = FontWeight.SemiBold)
                    Text("Consumo total: ${s.totalEaten.fmt()} kcal · média ${s.avgEaten.fmt()} kcal por dia registrado")
                    Text("Exercício total: ${s.totalExercise.fmt()} kcal · média ${s.avgExercise.fmt()} kcal por dia")
                }
            }
        }
        item { Text("Dias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (s.days.isEmpty()) item { Text("Ainda não há registros.") }
        items(s.days, key = { it.date.toEpochDay() }) { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(d.date.format(DayFormat))
                    d.weightKg?.let { Text("${it.fmt(1)} kg", style = MaterialTheme.typography.bodySmall) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${d.eaten.fmt()} kcal")
                    if (d.exercise > 0) Text("−${d.exercise.fmt()} kcal exercício", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}

/** Linha do peso ao longo do tempo, com a meta tracejada. */
@Composable
private fun WeightChart(points: List<Pair<LocalDate, Double>>, targetKg: Double) {
    val line = MaterialTheme.colorScheme.primary
    val goal = MaterialTheme.colorScheme.secondary
    val minDay = points.first().first.toEpochDay()
    val maxDay = points.last().first.toEpochDay().coerceAtLeast(minDay + 1)
    val minW = minOf(points.minOf { it.second }, targetKg) - 1
    val maxW = maxOf(points.maxOf { it.second }, targetKg) + 1
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        fun x(day: Long) = (day - minDay).toFloat() / (maxDay - minDay) * size.width
        fun y(w: Double) = ((maxW - w) / (maxW - minW)).toFloat() * size.height
        drawLine(goal, Offset(0f, y(targetKg)), Offset(size.width, y(targetKg)), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        val path = Path()
        points.forEachIndexed { i, (d, w) ->
            val p = Offset(x(d.toEpochDay()), y(w))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            drawCircle(line, 4.dp.toPx(), p)
        }
        drawPath(path, line, style = Stroke(3.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().first.format(DateFormat), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(points.last().first.format(DateFormat), style = MaterialTheme.typography.labelSmall)
    }
}
