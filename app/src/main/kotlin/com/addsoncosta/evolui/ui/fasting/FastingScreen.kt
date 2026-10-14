package com.addsoncosta.evolui.ui.fasting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addsoncosta.evolui.data.FastEntity
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.Ring
import com.addsoncosta.evolui.ui.components.Stat
import com.addsoncosta.evolui.ui.components.TimeFormat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Protocolos de jejum: horas em jejum e explicação curta. */
private enum class Protocol(val hours: Int, val label: String, val about: String) {
    P12(12, "12:12", "Ótimo para começar: jejum de 12 h, por exemplo das 20h às 8h."),
    P14(14, "14:10", "Um passo além: 14 h de jejum e 10 h para comer."),
    P16(16, "16:8", "O mais popular: 16 h de jejum, janela de 8 h (ex.: 12h às 20h)."),
    P18(18, "18:6", "Intermediário: 18 h de jejum e 6 h para comer."),
    P20(20, "20:4", "Avançado: janela de só 4 h para comer."),
    P23(23, "23:1", "Uma refeição por dia. Só com orientação profissional."),
}

private const val HOUR = 3_600_000L

@Composable
fun FastingScreen() {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val fasts by remember { repo.fasts() }.collectAsState(initial = emptyList())
    val running = fasts.firstOrNull { it.endMillis == null }
    val done = fasts.filter { it.endMillis != null }
    var protocol by rememberSaveable { mutableStateOf(Protocol.P16) }
    var confirmStop by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var phaseInfo by remember { mutableStateOf<FastPhase?>(null) }

    LaunchedEffect(running?.id) {
        while (running != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Overline("Jejum intermitente")
                Text(if (running != null) "Em jejum" else "Pronto para começar", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Panel {
                val target = running?.targetHours ?: protocol.hours
                val elapsed = if (running != null) (now - running.startMillis).coerceAtLeast(0) else 0L
                val progress = elapsed.toFloat() / (target * HOUR)
                Ring(
                    progress = progress,
                    color = if (progress >= 1f) Evolui.Neon else Evolui.Purple,
                    overColor = Evolui.Neon,
                    size = 240.dp,
                    stroke = 20.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (running != null) Text(FastPhase.at(elapsed / HOUR.toDouble()).emoji, fontSize = 30.sp)
                        Text(clock(elapsed), style = MaterialTheme.typography.displaySmall)
                        Text(
                            when {
                                running == null -> "meta de $target h"
                                progress >= 1f -> "Meta batida! 🎉"
                                else -> "faltam ${clock(target * HOUR - elapsed)}"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (running != null) {
                    val phase = FastPhase.at(elapsed / HOUR.toDouble())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { phaseInfo = phase }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(phase.emoji, fontSize = 32.sp)
                        Column(Modifier.weight(1f)) {
                            Overline("Fase atual · ${phase.range}", color = MaterialTheme.colorScheme.primary)
                            Text(phase.title, style = MaterialTheme.typography.titleMedium)
                            Text(phase.short, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Info, "Saber mais", tint = MaterialTheme.colorScheme.primary)
                    }
                    val zone = ZoneId.systemDefault()
                    val start = Instant.ofEpochMilli(running.startMillis).atZone(zone)
                    val end = Instant.ofEpochMilli(running.startMillis + running.targetHours * HOUR).atZone(zone)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(start.format(TimeFormat), "Começou")
                        Stat(end.format(TimeFormat), "Termina")
                        Stat("${running.targetHours} h", "Meta")
                    }
                    Button(
                        onClick = { confirmStop = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text("Encerrar jejum", fontWeight = FontWeight.Bold) }
                } else {
                    Text("Escolha o protocolo", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Protocol.entries.forEach { p ->
                            FilterChip(selected = protocol == p, onClick = { protocol = p }, label = { Text(p.label) })
                        }
                    }
                    Text(protocol.about, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            scope.launch { repo.saveFast(FastEntity(startMillis = System.currentTimeMillis(), targetHours = protocol.hours)) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Começar jejum agora", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            Panel {
                Text("Fases do jejum", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (running != null) "Veja em que fase você está e quando começa cada uma. Toque numa fase para entender."
                    else "O que acontece no corpo, hora a hora. Toque numa fase para entender.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PhaseTimeline(
                    startMillis = running?.startMillis,
                    elapsedHours = running?.let { (now - it.startMillis).coerceAtLeast(0) / HOUR.toDouble() },
                )
                Text(FASTING_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (done.isNotEmpty()) {
            item {
                val hours = done.sumOf { (it.endMillis!! - it.startMillis) / HOUR.toDouble() }
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("${done.size}", "Jejuns")
                        Stat("${done.count { it.endMillis!! - it.startMillis >= it.targetHours * HOUR }}", "Metas batidas", color = Evolui.Neon)
                        Stat(hours.fmt(), "Horas no total")
                    }
                }
            }
            item { Overline("Histórico") }
            items(done, key = { it.id }) { f -> FastRow(f, onDelete = { scope.launch { repo.deleteFast(f) } }) }
        }
        item {
            Text(
                "Jejum não é indicado para gestantes, menores de idade, diabéticos ou quem tem histórico de transtorno alimentar. Na dúvida, fale com um profissional. As fases são aproximadas e variam de pessoa para pessoa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    phaseInfo?.let { PhaseDialog(it, onDismiss = { phaseInfo = null }) }

    if (confirmStop && running != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Encerrar o jejum?") },
            text = { Text("Você está em jejum há ${clock(now - running.startMillis)}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    scope.launch { repo.saveFast(running.copy(endMillis = System.currentTimeMillis())) }
                }) { Text("Encerrar") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continuar") } },
        )
    }
}

@Composable
private fun FastRow(fast: FastEntity, onDelete: () -> Unit) {
    val duration = fast.endMillis!! - fast.startMillis
    val ok = duration >= fast.targetHours * HOUR
    val start = Instant.ofEpochMilli(fast.startMillis).atZone(ZoneId.systemDefault())
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(start.toLocalDate().format(DayFormat), fontWeight = FontWeight.Bold)
                Text("Início ${start.format(TimeFormat)} · meta ${fast.targetHours} h", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val reached = FastPhase.at(duration / HOUR.toDouble())
                Text("${reached.emoji} Chegou a: ${reached.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(clock(duration), style = MaterialTheme.typography.titleMedium, color = if (ok) Evolui.Neon else Evolui.Orange)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Apagar") }
        }
    }
}

/** 16h05 ou 0h42. */
private fun clock(millis: Long): String {
    val totalMin = (millis.coerceAtLeast(0) / 60_000)
    return "${totalMin / 60}h%02d".format(totalMin % 60)
}
