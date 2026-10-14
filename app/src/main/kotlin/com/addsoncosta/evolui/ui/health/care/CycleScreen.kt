package com.addsoncosta.evolui.ui.health.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.MenstrualCycleEntity
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.ShortDateFormat
import com.addsoncosta.evolui.ui.components.Stat
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Intensidade do fluxo. O [name] é gravado em MenstrualCycleEntity.flow. */
internal enum class MenstrualFlow(val label: String) { LEVE("Leve"), MEDIO("Médio"), INTENSO("Intenso") }

internal fun menstrualFlowOf(name: String?): MenstrualFlow? = MenstrualFlow.entries.firstOrNull { it.name == name }

private val CommonSymptoms = listOf(
    "Cólica", "Dor de cabeça", "Inchaço", "Acne", "Sensibilidade nos seios", "Alteração de humor",
    "Cansaço", "Dor nas costas", "Enjoo", "Diarreia", "Prisão de ventre", "Insônia", "Vontade de doce", "Irritação",
)

internal fun parseSymptoms(text: String?): List<String> = text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Médias e previsão calculadas a partir dos registros. */
private data class CycleStats(
    val avgCycle: Int,
    val avgPeriod: Int,
    val cycleFromData: Boolean,
    val periodFromData: Boolean,
    /** Próximas menstruações previstas (início), da mais próxima em diante. */
    val nextStarts: List<Long>,
)

private const val DEFAULT_CYCLE = 28
private const val DEFAULT_PERIOD = 5

private fun computeStats(cycles: List<MenstrualCycleEntity>, today: Long): CycleStats {
    val sorted = cycles.sortedBy { it.startEpochDay }
    val gaps = sorted.zipWithNext { a, b -> (b.startEpochDay - a.startEpochDay).toInt() }.filter { it in 15..60 }.takeLast(6)
    val lengths = sorted.mapNotNull { c -> c.endEpochDay?.let { (it - c.startEpochDay + 1).toInt() } }.filter { it in 1..15 }.takeLast(6)
    val avgCycle = if (gaps.isEmpty()) DEFAULT_CYCLE else gaps.average().roundToInt()
    val avgPeriod = if (lengths.isEmpty()) DEFAULT_PERIOD else lengths.average().roundToInt()
    val last = sorted.lastOrNull()
    val nextStarts = if (last == null) {
        emptyList()
    } else {
        var next = last.startEpochDay + avgCycle
        // Se a previsão já passou há muito tempo (registros antigos), projeta a partir de hoje.
        while (next + avgCycle < today) next += avgCycle
        (0 until 3).map { next + it * avgCycle }
    }
    return CycleStats(avgCycle, avgPeriod, gaps.isNotEmpty(), lengths.isNotEmpty(), nextStarts)
}

/** Último dia da menstruação para mostrar (registrado, ou estimado se ainda está aberta). */
private fun periodEnd(c: MenstrualCycleEntity, avgPeriod: Int, today: Long): Long {
    c.endEpochDay?.let { return it }
    val days = today - c.startEpochDay
    return if (days in 0L..15L) today else c.startEpochDay + avgPeriod - 1
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val dao = container.database.menstrualCycleDao()
    val cycles by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now().toEpochDay() }
    val stats = remember(cycles) { computeStats(cycles, today) }

    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(today) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MenstrualCycleEntity?>(null) }

    val latest = cycles.firstOrNull() // a lista vem do mais recente para o mais antigo
    val ongoing = latest?.takeIf { it.endEpochDay == null && today - it.startEpochDay in 0L..15L }

    // Dias marcados no calendário.
    val periodDays = remember(cycles, stats) {
        val set = HashSet<Long>()
        cycles.forEach { c -> for (d in c.startEpochDay..periodEnd(c, stats.avgPeriod, today)) set.add(d) }
        set
    }
    val predictedDays = remember(stats) {
        stats.nextStarts.flatMap { s -> (s until s + stats.avgPeriod).toList() }.toSet()
    }
    val fertileDays = remember(stats) {
        stats.nextStarts.flatMap { s -> ((s - 19)..(s - 13)).toList() }.toSet()
    }
    val ovulationDays = remember(stats) { stats.nextStarts.map { it - 14 }.toSet() }
    val cycleAt: (Long) -> MenstrualCycleEntity? = { day -> cycles.firstOrNull { day in it.startEpochDay..periodEnd(it, stats.avgPeriod, today) } }

    val periodColor = Evolui.Red
    val fertileColor = Evolui.Cyan

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") { CareHeader("Saúde", "Ciclo menstrual", ReportArea.CICLO, onBack) }

        // Ação rápida.
        item(key = "acao") {
            Panel {
                if (ongoing != null) {
                    val day = today - ongoing.startEpochDay + 1
                    Text("Menstruação em andamento", style = MaterialTheme.typography.titleMedium)
                    Text("Dia $day · começou ${relativeDayText(ongoing.startEpochDay, today).lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { container.scope.launch { dao.save(ongoing.copy(endEpochDay = today)) } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Terminou hoje", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { editing = ongoing }, modifier = Modifier.fillMaxWidth()) { Text("Editar fluxo, sintomas ou datas") }
                } else {
                    Text("Registrar menstruação", style = MaterialTheme.typography.titleMedium)
                    Text("Marque o primeiro dia. Quando terminar, volte e toque em \"Terminou hoje\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { container.scope.launch { dao.save(MenstrualCycleEntity(startEpochDay = today)) } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Começou hoje", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { editing = MenstrualCycleEntity(startEpochDay = today) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Registrar com outra data")
                    }
                }
            }
        }

        // Médias e previsão.
        item(key = "previsao") {
            Panel {
                if (cycles.isEmpty()) {
                    Text("Previsão", style = MaterialTheme.typography.titleMedium)
                    EmptyState("Registre sua menstruação para ver a previsão da próxima e a janela fértil aproximada. Quanto mais meses registrados, melhor a estimativa.")
                } else {
                    val next = stats.nextStarts.firstOrNull()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("${stats.avgCycle} dias", if (stats.cycleFromData) "ciclo médio" else "ciclo (padrão)")
                        Stat("${stats.avgPeriod} dias", if (stats.periodFromData) "menstruação" else "menstr. (padrão)")
                        if (next != null) Stat(LocalDate.ofEpochDay(next).format(ShortDateFormat), "próxima", color = periodColor)
                    }
                    if (next != null && ongoing == null) {
                        val inDays = next - today
                        Text(
                            when {
                                inDays > 1 -> "Próxima menstruação por volta de ${relativeDayText(next, today)} (em $inDays dias)."
                                inDays == 1L -> "Próxima menstruação prevista para amanhã."
                                inDays == 0L -> "Próxima menstruação prevista para hoje."
                                else -> "A previsão era ${relativeDayText(next, today)}: ${-inDays} dia(s) de atraso. Atrasos acontecem; se continuar, converse com seu médico."
                            },
                        )
                        Text(
                            "Janela fértil aproximada: ${LocalDate.ofEpochDay(next - 19).format(ShortDateFormat)} a ${LocalDate.ofEpochDay(next - 13).format(ShortDateFormat)}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!stats.cycleFromData) {
                        Text("Usando 28 dias até você registrar pelo menos dois meses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = Evolui.Orange)
                    Text(
                        "  É só uma estimativa pelas datas que você registrou. Ciclos variam e a previsão pode errar: não use como método para evitar gravidez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Calendário.
        item(key = "calendario") {
            Panel {
                MonthGrid(
                    month = YearMonth.parse(monthKey),
                    selectedDay = selectedDay,
                    onMonthChange = { monthKey = it.toString() },
                    onDayClick = { selectedDay = it },
                    mark = { d ->
                        when {
                            d in periodDays -> DayMark(fill = periodColor.copy(alpha = 0.35f))
                            d in predictedDays -> DayMark(outline = periodColor.copy(alpha = 0.7f))
                            d in fertileDays -> DayMark(fill = fertileColor.copy(alpha = 0.22f), dots = if (d in ovulationDays) listOf(fertileColor) else emptyList())
                            else -> DayMark()
                        }
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendItem(fill = periodColor.copy(alpha = 0.35f), label = "Menstruação")
                    LegendItem(outline = periodColor.copy(alpha = 0.7f), label = "Prevista")
                    LegendItem(fill = fertileColor.copy(alpha = 0.22f), label = "Fértil (aprox.)")
                }
                // O que fazer com o dia tocado.
                val atDay = cycleAt(selectedDay)
                val dayLabel = LocalDate.ofEpochDay(selectedDay).format(ShortDateFormat)
                if (atDay != null) {
                    OutlinedButton(onClick = { editing = atDay }, modifier = Modifier.fillMaxWidth()) { Text("Editar menstruação de $dayLabel") }
                } else if (selectedDay <= today) {
                    OutlinedButton(onClick = { editing = MenstrualCycleEntity(startEpochDay = selectedDay) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Menstruação começou em $dayLabel")
                    }
                }
            }
        }

        // Histórico.
        item(key = "hist") { Overline("Histórico") }
        if (cycles.isNotEmpty()) {
            item(key = "busca") { SearchField(query, { query = it }, "Buscar sintoma, nota ou data") }
        }
        val sortedAsc = cycles.sortedBy { it.startEpochDay }
        val nextStartOf = sortedAsc.zipWithNext().associate { (a, b) -> a.id to b.startEpochDay }
        val shown = cycles.filter { c ->
            matches(query, c.symptoms, c.notes, menstrualFlowOf(c.flow)?.label, dayText(c.startEpochDay), c.endEpochDay?.let { dayText(it) })
        }
        if (cycles.isEmpty()) {
            item(key = "vazio") { Panel { EmptyState("Nenhum registro ainda. Toque em \"Começou hoje\" no primeiro dia da menstruação.") } }
        } else if (shown.isEmpty()) {
            item(key = "nada") { EmptyState("Nada encontrado para \"$query\".") }
        }
        items(shown, key = { it.id }) { c ->
            CycleRow(c, nextStartOf[c.id], today, onClick = { editing = c })
        }
    }

    editing?.let { c ->
        CycleEditor(
            initial = c,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                container.scope.launch { dao.save(updated) }
            },
            onDelete = {
                editing = null
                container.scope.launch { dao.delete(c) }
            },
        )
    }
}

@Composable
private fun LegendItem(label: String, fill: Color? = null, outline: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        var m = Modifier.size(14.dp).clip(CircleShape)
        if (fill != null) m = m.background(fill)
        if (outline != null) m = m.border(2.dp, outline, CircleShape)
        Box(m)
        Text(" $label", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CycleRow(c: MenstrualCycleEntity, nextStart: Long?, today: Long, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClick = onClick)) {
        val end = c.endEpochDay
        Text(
            if (end != null) "${dayText(c.startEpochDay)} a ${LocalDate.ofEpochDay(end).format(ShortDateFormat)}"
            else "${dayText(c.startEpochDay)} · sem data de fim",
            fontWeight = FontWeight.Bold,
        )
        val parts = buildList {
            if (end != null) add("${end - c.startEpochDay + 1} dias de menstruação")
            else if (today - c.startEpochDay in 0L..15L) add("em andamento")
            menstrualFlowOf(c.flow)?.let { add("fluxo ${it.label.lowercase()}") }
            if (nextStart != null) add("ciclo de ${nextStart - c.startEpochDay} dias")
        }
        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val symptoms = parseSymptoms(c.symptoms)
        if (symptoms.isNotEmpty()) Text(symptoms.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        c.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleEditor(
    initial: MenstrualCycleEntity,
    onDismiss: () -> Unit,
    onSave: (MenstrualCycleEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = initial.id == 0L
    val initialSymptoms = remember { parseSymptoms(initial.symptoms) }
    var start by remember { mutableStateOf(initial.startEpochDay) }
    var end by remember { mutableStateOf(initial.endEpochDay) }
    var flow by remember { mutableStateOf(menstrualFlowOf(initial.flow)) }
    var symptoms by remember { mutableStateOf(initialSymptoms.filter { it in CommonSymptoms }.toSet()) }
    var otherSymptoms by remember { mutableStateOf(initialSymptoms.filter { it !in CommonSymptoms }.joinToString(", ")) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val endValue = end
    val valid = endValue == null || endValue >= start

    val save = {
        if (valid) {
            val all = CommonSymptoms.filter { it in symptoms } + parseSymptoms(otherSymptoms)
            onSave(
                initial.copy(
                    startEpochDay = start,
                    endEpochDay = end,
                    flow = flow?.name,
                    symptoms = all.joinToString(", ").ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                ),
            )
        }
    }

    FormDialog(title = if (isNew) "Registrar menstruação" else "Editar menstruação", onDismiss = onDismiss, onSave = save, saveEnabled = valid) {
        Text("Primeiro dia", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, null)
            Text(" ${dayText(start)}")
        }
        Text("Último dia", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, null)
            Text(" ${endValue?.let { dayText(it) } ?: "Ainda não terminou"}")
        }
        if (endValue != null) {
            TextButton(onClick = { end = null }) { Text("Ainda não terminou") }
            Text(
                if (valid) "Duração: ${endValue - start + 1} dias." else "O último dia precisa ser depois do primeiro.",
                style = MaterialTheme.typography.bodySmall,
                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }

        Text("Fluxo", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenstrualFlow.entries.forEach { f ->
                FilterChip(selected = flow == f, onClick = { flow = if (flow == f) null else f }, label = { Text(f.label) })
            }
        }

        Text("Sintomas", fontWeight = FontWeight.Bold)
        Text("Toque em todos que sentiu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CommonSymptoms.forEach { s ->
                val on = s in symptoms
                FilterChip(selected = on, onClick = { symptoms = if (on) symptoms - s else symptoms + s }, label = { Text(s) })
            }
        }
        HelpField(otherSymptoms, { otherSymptoms = it }, "Outros sintomas", "Separe por vírgula. Ex.: tontura, dor nas pernas (opcional)")
        HelpField(notes, { notes = it }, "Observações", "Ex.: tomei um analgésico no 1º dia (opcional)", singleLine = false)

        if (!isNew) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(" Apagar registro", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickStart) DatePick(start, onDismiss = { pickStart = false }, onPick = { start = it })
    if (pickEnd) DatePick(end ?: maxOf(start, LocalDate.now().toEpochDay()), onDismiss = { pickEnd = false }, onPick = { end = it })
    if (confirmDelete) {
        ConfirmDialog(
            title = "Apagar este registro?",
            text = "A menstruação de ${dayText(initial.startEpochDay)} sai do histórico e da previsão.",
            confirmLabel = "Apagar",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
    }
}
