package com.addsoncosta.evolui.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.addsoncosta.evolui.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.DailyTaskEntity
import com.addsoncosta.evolui.data.DailyTaskRepository
import com.addsoncosta.evolui.data.TaskProgress
import com.addsoncosta.evolui.data.TaskUnit
import com.addsoncosta.evolui.habits.HabitReminders
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.health.care.TimePick
import com.addsoncosta.evolui.ui.health.care.rememberNotificationAsker
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Sugestões prontas: nome, unidade e meta por dia. */
private val PRESETS = listOf(
    Triple("Flexões", TaskUnit.REPS, 30.0),
    Triple("Abdominais", TaskUnit.REPS, 40.0),
    Triple("Prancha", TaskUnit.SECONDS, 120.0),
    Triple("Barra fixa", TaskUnit.REPS, 15.0),
    Triple("Agachamentos", TaskUnit.REPS, 50.0),
    Triple("Polichinelos", TaskUnit.REPS, 60.0),
    Triple("Alongamento", TaskUnit.MINUTES, 10.0),
)

/** Treino › Tarefas do dia: metas diárias opcionais com lembretes ao longo do dia. */
@Composable
internal fun TasksTab(modifier: Modifier = Modifier) {
    val container = appContainer()
    val repo = remember { DailyTaskRepository(container.database) }
    val date by container.selectedDate.collectAsStateWithLifecycle()
    val day by remember(date) { repo.day(date) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val all by remember { repo.tasks() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val paused = all.filter { !it.active }
    var editing by remember { mutableStateOf<DailyTaskEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val askNotifications = rememberNotificationAsker("Para lembrar você das tarefas de treino ao longo do dia, o Evolui precisa mostrar notificações.")

    fun save(task: DailyTaskEntity) {
        scope.launch {
            val id = repo.save(task)
            HabitReminders.scheduleTask(context.applicationContext, task.copy(id = id))
        }
        if (task.remindersPerDay > 0 && task.active) askNotifications()
    }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Pequenas metas para todo dia, como 30 flexões ou 2 minutos de prancha. Divida em séries: o Evolui avisa ao longo do dia " +
                    "e você marca o que fez. São opcionais e não mudam a meta de calorias.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(day, key = { it.task.id }) { p ->
            TaskCard(
                progress = p,
                onLog = { amount -> scope.launch { repo.log(p.task, date, amount) } },
                onUndo = { p.logs.lastOrNull()?.let { last -> scope.launch { repo.undo(last) } } },
                onEdit = { editing = p.task },
            )
        }
        if (paused.isNotEmpty()) {
            item { Text("Pausadas", style = MaterialTheme.typography.labelLarge) }
            items(paused, key = { "p${it.id}" }) { t ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.name} · ${t.target.fmt()} ${t.taskUnit.short}", Modifier.weight(1f))
                        IconButton(onClick = { editing = t }) { Icon(Icons.Filled.Edit, "Editar") }
                    }
                }
            }
        }
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Add, null)
                Text(" Nova tarefa diária", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (creating || editing != null) {
        TaskDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { t ->
                save(t)
                creating = false
                editing = null
            },
            onDelete = editing?.let { t ->
                {
                    scope.launch {
                        HabitReminders.cancelTask(context.applicationContext, t.id)
                        repo.delete(t)
                    }
                    editing = null
                }
            },
        )
    }
}

/** Cartão de uma tarefa com o progresso do dia e botões para marcar séries. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskCard(progress: TaskProgress, onLog: (Double) -> Unit, onUndo: () -> Unit, onEdit: (() -> Unit)? = null) {
    val t = progress.task
    val unit = t.taskUnit.short
    var other by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${progress.done.fmt()} de ${t.target.fmt()} $unit" + if (progress.complete) " · feito!" else "",
                    color = if (progress.complete) Evolui.Neon else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                if (t.remindersPerDay > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(14.dp))
                        Text(
                            " ${t.remindersPerDay} avisos, das ${hm(t.startMinute)} às ${hm(t.endMinute)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar tarefa") }
        }
        LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = Evolui.Neon)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onLog(t.perReminder) }) { Text("+ ${t.perReminder.fmt()} $unit") }
            OutlinedButton(onClick = { other = true }) { Text("Outro valor") }
            TextButton(onClick = onUndo, enabled = progress.logs.isNotEmpty()) { Text("Desfazer") }
        }
    }
    if (other) {
        var text by remember { mutableStateOf("") }
        val value = text.toDecimal()
        AlertDialog(
            onDismissRequest = { other = false },
            title = { Text(t.name) },
            text = { NumberField(text, { text = it }, "Quanto você fez", suffix = unit, help = "Ex.: 12") },
            confirmButton = {
                TextButton(enabled = value != null && value > 0, onClick = {
                    onLog(value!!)
                    other = false
                }) { Text("Somar") }
            },
            dismissButton = { TextButton(onClick = { other = false }) { Text("Cancelar") } },
        )
    }
}

private fun hm(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDialog(initial: DailyTaskEntity?, onDismiss: () -> Unit, onSave: (DailyTaskEntity) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.taskUnit ?: TaskUnit.REPS) }
    var target by remember { mutableStateOf(initial?.target?.fmt() ?: "") }
    var reminders by remember { mutableStateOf(initial?.remindersPerDay ?: 3) }
    var start by remember { mutableStateOf(initial?.startMinute ?: (9 * 60)) }
    var end by remember { mutableStateOf(initial?.endMinute ?: (20 * 60)) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var picking by remember { mutableStateOf<String?>(null) }
    val goal = target.toDecimal()
    val valid = name.isNotBlank() && goal != null && goal > 0 && (reminders == 0 || end > start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova tarefa diária" else "Editar tarefa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initial == null) {
                    Text("Sugestões", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { (n, u, g) ->
                            FilterChip(selected = name == n, onClick = {
                                name = n
                                unit = u
                                target = g.fmt()
                            }, label = { Text(n) })
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Medida", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskUnit.entries.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) }) }
                }
                NumberField(target, { target = it }, "Meta por dia", suffix = unit.short, help = "Ex.: 30")
                Text("Avisos por dia", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3, 4, 6, 8).forEach { n ->
                        FilterChip(selected = reminders == n, onClick = { reminders = n }, label = { Text(if (n == 0) "Sem aviso" else "$n") })
                    }
                }
                if (reminders > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) { Text("De ${hm(start)}") }
                        OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) { Text("Até ${hm(end)}") }
                    }
                    if (goal != null && goal > 0) {
                        val per = if (reminders <= 1) goal else kotlin.math.ceil(goal / reminders)
                        Text(
                            "Em cada aviso: ${per.fmt()} ${unit.short}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (end <= start) Text("O fim precisa ser depois do início.", color = MaterialTheme.colorScheme.error)
                }
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ativa", Modifier.weight(1f))
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                    onDelete?.let { TextButton(onClick = it) { Text("Apagar tarefa", color = MaterialTheme.colorScheme.error) } }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    DailyTaskEntity(
                        id = initial?.id ?: 0,
                        name = name.trim(),
                        unit = unit.name,
                        target = goal!!,
                        remindersPerDay = reminders,
                        startMinute = start,
                        endMinute = end,
                        active = active,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    when (picking) {
        "start" -> TimePick(start, { picking = null }) { start = it }
        "end" -> TimePick(end, { picking = null }) { end = it }
    }
}

/** Para a tela Hoje: as tarefas do dia com botão de marcar. */
@Composable
fun TodayTasks(date: LocalDate) {
    val container = appContainer()
    val repo = remember { DailyTaskRepository(container.database) }
    val day by remember(date) { repo.day(date) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    if (day.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tarefas do dia", style = MaterialTheme.typography.titleMedium)
        day.forEach { p ->
            TaskCard(
                progress = p,
                onLog = { amount -> scope.launch { repo.log(p.task, date, amount) } },
                onUndo = { p.logs.lastOrNull()?.let { last -> scope.launch { repo.undo(last) } } },
            )
        }
    }
}
