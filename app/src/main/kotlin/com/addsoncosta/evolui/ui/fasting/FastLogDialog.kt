package com.addsoncosta.evolui.ui.fasting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.addsoncosta.evolui.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.FastEntity
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.health.care.DatePick
import com.addsoncosta.evolui.ui.health.care.TimePick
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Registrar um jejum que já foi feito (ou corrigir um do histórico): protocolo, início e fim.
 * O fim começa como início + horas do protocolo; nada pode ficar no futuro.
 */
@Composable
internal fun FastLogDialog(existing: FastEntity?, onDismiss: () -> Unit, onSave: (FastEntity) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    fun toLocal(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun toMillis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    var hours by remember { mutableStateOf(existing?.targetHours ?: 16) }
    var start by remember {
        mutableStateOf(existing?.let { toLocal(it.startMillis) } ?: now.minusDays(1).withHour(20).withMinute(0).withSecond(0).withNano(0))
    }
    var end by remember {
        mutableStateOf(existing?.endMillis?.let { toLocal(it) } ?: earlier(start.plusHours(hours.toLong()), now))
    }
    var endTouched by remember { mutableStateOf(existing != null) }
    var pick by remember { mutableStateOf<String?>(null) }

    fun setStart(t: LocalDateTime) {
        start = earlier(t, now)
        if (!endTouched) end = earlier(start.plusHours(hours.toLong()), now)
    }

    val valid = end.isAfter(start) && !end.isAfter(now.plusMinutes(1))
    val duration = java.time.Duration.between(start, end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Registrar jejum já feito" else "Corrigir jejum") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Protocolo adotado", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(12 to "12:12", 14 to "14:10", 16 to "16:8", 18 to "18:6", 20 to "20:4", 23 to "23:1", 24 to "24 h", 36 to "36 h").forEach { (h, label) ->
                        FilterChip(selected = hours == h, onClick = {
                            hours = h
                            if (!endTouched) end = earlier(start.plusHours(h.toLong()), now)
                        }, label = { Text(label) })
                    }
                }
                Text("Começou", style = MaterialTheme.typography.labelLarge)
                DateTimeButtons(start, onDate = { pick = "startDate" }, onTime = { pick = "startTime" })
                Text("Terminou", style = MaterialTheme.typography.labelLarge)
                DateTimeButtons(end, onDate = { pick = "endDate" }, onTime = { pick = "endTime" })
                Text(
                    if (valid) "Duração: ${duration.toHours()}h%02d".format(duration.toMinutes() % 60) +
                        if (duration.toMinutes() >= hours * 60L) " · meta batida" else " · meta de $hours h não batida"
                    else "O fim precisa ser depois do início e não pode ser no futuro.",
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    FastEntity(
                        id = existing?.id ?: 0,
                        startMillis = toMillis(start),
                        endMillis = toMillis(end),
                        targetHours = hours,
                    )
                )
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    when (pick) {
        "startDate" -> DatePick(start.toLocalDate().toEpochDay(), { pick = null }) { setStart(LocalDate.ofEpochDay(it).atTime(start.toLocalTime())) }
        "startTime" -> TimePick(start.hour * 60 + start.minute, { pick = null }) { setStart(start.toLocalDate().atTime(LocalTime.of(it / 60, it % 60))) }
        "endDate" -> DatePick(end.toLocalDate().toEpochDay(), { pick = null }) {
            endTouched = true
            end = earlier(LocalDate.ofEpochDay(it).atTime(end.toLocalTime()), now)
        }
        "endTime" -> TimePick(end.hour * 60 + end.minute, { pick = null }) {
            endTouched = true
            end = earlier(end.toLocalDate().atTime(LocalTime.of(it / 60, it % 60)), now)
        }
    }
}

@Composable
private fun DateTimeButtons(t: LocalDateTime, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDate, modifier = Modifier.weight(1.4f)) {
            Icon(Icons.Filled.CalendarMonth, null)
            Text(" " + t.toLocalDate().format(DayFormat), maxLines = 1)
        }
        OutlinedButton(onClick = onTime, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Schedule, null)
            Text(" %02d:%02d".format(t.hour, t.minute))
        }
    }
}

private fun earlier(a: LocalDateTime, b: LocalDateTime): LocalDateTime = if (a.isAfter(b)) b else a
