package com.addsoncosta.evolui.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.HealthEventEntity
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.health.Reminders
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.health.care.color
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Próximos eventos do calendário de saúde, para a tela Hoje: até 3 compromissos dos próximos
 * 7 dias e os remédios de hoje. Sem nada marcado, não desenha nada.
 */
@Composable
fun UpcomingEventsCard(onOpenHealth: () -> Unit, modifier: Modifier = Modifier) {
    val container = appContainer()
    val appContext = LocalContext.current.applicationContext
    val dao = container.database.healthEventDao()
    val today = remember { LocalDate.now().toEpochDay() }
    val events by remember(today) { dao.observeRange(today, today + 7) }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Reagenda os avisos uma vez por abertura do app (cobre atualizações do app).
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Reminders.rescheduleOnce(appContext) } }

    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val takeTypes = setOf(HealthType.MEDICACAO.name, HealthType.SUPLEMENTACAO.name)
    val meds = events.filter { it.type in takeTypes && it.epochDay == today }
    val upcoming = events.filter { e ->
        !e.done && e.type !in takeTypes &&
            (e.epochDay > today || e.minuteOfDay == null || e.minuteOfDay >= nowMinute - 60)
    }.take(3)
    if (meds.isEmpty() && upcoming.isEmpty()) return

    Panel(modifier.clickable(onClick = onOpenHealth)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, null, tint = Evolui.Cyan)
            Text("  Agenda de saúde", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("ver tudo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        upcoming.forEach { e -> UpcomingRow(e, today) }
        if (meds.isNotEmpty()) {
            Overline(if (meds.any { it.type == HealthType.SUPLEMENTACAO.name }) "Remédios e suplementos de hoje" else "Remédios de hoje", modifier = Modifier.padding(top = 4.dp))
            meds.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.title,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (e.done) TextDecoration.LineThrough else null,
                        )
                        Text(e.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "a qualquer hora", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        container.scope.launch {
                            val updated = e.copy(done = !e.done)
                            dao.save(updated)
                            Reminders.schedule(appContext, updated)
                        }
                    }) {
                        if (e.done) Icon(Icons.Filled.CheckCircle, "Tomado", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Filled.RadioButtonUnchecked, "Marcar como tomado")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(e: HealthEventEntity, today: Long) {
    val type = HealthType.entries.firstOrNull { it.name == e.type } ?: HealthType.CONSULTA
    val day = when (e.epochDay) {
        today -> "Hoje"
        today + 1 -> "Amanhã"
        else -> LocalDate.ofEpochDay(e.epochDay).format(DayFormat)
    }
    val time = e.minuteOfDay?.let { " às %02d:%02d".format(it / 60, it % 60) } ?: ""
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(type.color()))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(e.title.ifBlank { type.singular }, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(day + time, e.place?.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
