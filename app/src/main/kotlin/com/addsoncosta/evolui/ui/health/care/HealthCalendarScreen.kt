package com.addsoncosta.evolui.ui.health.care

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.HealthEventDao
import com.addsoncosta.evolui.data.HealthEventEntity
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.health.Reminders
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opções de lembrete: minutos antes (nulo = sem lembrete). */
private val ReminderOptions: List<Pair<Int?, String>> = listOf(
    null to "Sem lembrete",
    0 to "Na hora",
    15 to "15 min antes",
    60 to "1 hora antes",
    1440 to "1 dia antes",
)

private fun reminderLabel(minutes: Int?): String = ReminderOptions.firstOrNull { it.first == minutes }?.second
    ?: when {
        minutes == null -> "Sem lembrete"
        minutes % 1440 == 0 -> "${minutes / 1440} dia(s) antes"
        minutes % 60 == 0 -> "${minutes / 60} h antes"
        else -> "$minutes min antes"
    }

/** Sugestões de título por tipo, para não precisar digitar. */
private fun titleSuggestions(type: HealthType): List<String> = when (type) {
    HealthType.CONSULTA -> listOf("Consulta clínico geral", "Consulta cardiologista", "Consulta dermatologista", "Consulta ginecologista", "Consulta urologista", "Consulta oftalmologista", "Consulta endocrinologista", "Consulta ortopedista")
    HealthType.EXAME -> listOf("Exame de sangue", "Exame de urina", "Ultrassom", "Eletrocardiograma", "Raio-X", "Ressonância", "Tomografia", "Mamografia")
    HealthType.MEDICACAO -> listOf("Tomar remédio", "Remédio da pressão", "Vitamina", "Antibiótico", "Injeção")
    HealthType.ODONTOLOGIA -> listOf("Limpeza", "Revisão no dentista", "Tratamento de canal", "Restauração", "Aparelho (manutenção)")
    HealthType.NUTRICAO -> listOf("Consulta nutricionista", "Retorno nutricionista")
    HealthType.FISIOTERAPIA -> listOf("Sessão de fisioterapia", "Pilates", "RPG")
    HealthType.TERAPIA -> listOf("Sessão de psicoterapia", "Psiquiatra", "Fonoaudiólogo", "Terapia ocupacional")
    HealthType.TRATAMENTO -> listOf("Sessão de tratamento", "Acupuntura", "Curativo", "Vacina")
    HealthType.BIOIMPEDANCIA -> listOf("Bioimpedância")
    HealthType.ANAMNESE -> emptyList()
}

@Composable
fun HealthCalendarScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val dao = container.database.healthEventDao()
    val events by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now().toEpochDay() }

    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthKey)
    var selectedDay by rememberSaveable { mutableStateOf(today) }
    var query by rememberSaveable { mutableStateOf("") }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HealthEventEntity?>(null) }

    // Garante que os avisos estejam agendados (ex.: depois de atualizar o app).
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Reminders.rescheduleOnce(appContext) } }

    val byDay = remember(events) { events.groupBy { it.epochDay } }
    val toggleDone: (HealthEventEntity) -> Unit = { e -> flipDone(container.scope, dao, appContext, e) }
    val newEvent = { day: Long -> HealthEventEntity(type = HealthType.CONSULTA.name, epochDay = day, title = "") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { CareHeader("Saúde", "Calendário", ReportArea.CALENDARIO, onBack) }
            item(key = "busca") { SearchField(query, { query = it }, "Buscar por nome, local ou tipo") }

            if (query.isNotBlank()) {
                val found = events.filter { e ->
                    val t = healthTypeOf(e.type)
                    matches(query, e.title, e.place, e.notes, t.label, t.singular, dayText(e.epochDay))
                }.sortedByDescending { it.epochDay }
                item(key = "res") { Overline(if (found.size == 1) "1 evento encontrado" else "${found.size} eventos encontrados") }
                if (found.isEmpty()) item(key = "nada") { EmptyState("Nada encontrado para \"$query\". Tente outra palavra, como \"dentista\" ou \"exame\".") }
                items(found, key = { it.id }) { e -> EventRow(e, showDate = true, onClick = { editing = e }, onToggleDone = { toggleDone(e) }) }
            } else {
                item(key = "mes") {
                    Panel {
                        MonthGrid(
                            month = month,
                            selectedDay = selectedDay,
                            onMonthChange = { monthKey = it.toString() },
                            onDayClick = { selectedDay = it },
                            mark = { day -> DayMark(dots = byDay[day]?.map { healthTypeOf(it.type).color() }?.distinct() ?: emptyList()) },
                        )
                        MonthLegend(events.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month })
                    }
                }

                // Eventos do dia tocado.
                val dayEvents = byDay[selectedDay].orEmpty()
                item(key = "dia") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            LocalDate.ofEpochDay(selectedDay).format(LongDayFormat).capitalized(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editing = newEvent(selectedDay) }) {
                            Icon(Icons.Filled.Add, null)
                            Text(" Marcar")
                        }
                    }
                }
                if (dayEvents.isEmpty()) {
                    item(key = "dia_vazio") { EmptyState("Nada marcado neste dia. Toque em \"Marcar\" para adicionar.") }
                } else {
                    items(dayEvents, key = { "d${it.id}" }) { e -> EventRow(e, showDate = false, onClick = { editing = e }, onToggleDone = { toggleDone(e) }) }
                }

                // Próximos compromissos.
                val upcoming = events.filter { !it.done && it.epochDay >= today }.take(30)
                item(key = "prox") { Overline("Próximos") }
                if (upcoming.isEmpty()) {
                    item(key = "prox_vazio") {
                        Panel {
                            EmptyState(
                                if (events.isEmpty()) "Toque em + para marcar sua primeira consulta, exame ou horário de remédio. Você pode receber um aviso antes."
                                else "Nenhum compromisso pela frente. Toque em + para marcar.",
                            )
                        }
                    }
                } else {
                    items(upcoming, key = { "p${it.id}" }) { e -> EventRow(e, showDate = true, onClick = { editing = e }, onToggleDone = { toggleDone(e) }) }
                }

                // Anteriores (recolhido).
                val past = events.filter { it.epochDay < today }.asReversed()
                if (past.isNotEmpty()) {
                    item(key = "passados") {
                        OutlinedButton(onClick = { showPast = !showPast }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showPast) "Esconder anteriores" else "Ver anteriores (${past.size})")
                        }
                    }
                    if (showPast) {
                        items(past.take(60), key = { "a${it.id}" }) { e -> EventRow(e, showDate = true, onClick = { editing = e }, onToggleDone = { toggleDone(e) }) }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = newEvent(selectedDay) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Novo evento") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { e ->
        EventEditor(
            initial = e,
            onDismiss = { editing = null },
            onSave = { event, repeatDays ->
                editing = null
                selectedDay = event.epochDay
                monthKey = YearMonth.from(LocalDate.ofEpochDay(event.epochDay)).toString()
                saveEvent(container.scope, dao, appContext, event, repeatDays)
            },
            onDelete = { event ->
                editing = null
                container.scope.launch {
                    dao.delete(event)
                    Reminders.cancel(appContext, event.id)
                }
            },
        )
    }
}

private fun flipDone(scope: CoroutineScope, dao: HealthEventDao, context: Context, e: HealthEventEntity) {
    scope.launch {
        val updated = e.copy(done = !e.done)
        dao.save(updated)
        Reminders.schedule(context, updated)
    }
}

/** Salva o evento e agenda o aviso. Com [repeatDays] > 1 (evento novo), cria um por dia. */
private fun saveEvent(scope: CoroutineScope, dao: HealthEventDao, context: Context, event: HealthEventEntity, repeatDays: Int) {
    scope.launch {
        if (event.id == 0L && repeatDays > 1) {
            val list = (0 until repeatDays).map { event.copy(epochDay = event.epochDay + it) }
            val ids = dao.insertMany(list)
            list.zip(ids).forEach { (ev, id) -> Reminders.schedule(context, ev.copy(id = id)) }
        } else {
            val id = dao.save(event)
            val saved = if (event.id == 0L) event.copy(id = id) else event
            Reminders.schedule(context, saved)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthLegend(monthEvents: List<HealthEventEntity>) {
    val types = monthEvents.map { healthTypeOf(it.type) }.distinct().sortedBy { it.ordinal }
    if (types.isEmpty()) {
        Text("Os dias com bolinha têm eventos. Toque num dia para ver.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        types.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(t.color()))
                Text(" ${t.label}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EventRow(event: HealthEventEntity, showDate: Boolean, onClick: () -> Unit, onToggleDone: () -> Unit) {
    val type = healthTypeOf(event.type)
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(type)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    event.title.ifBlank { type.singular },
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (event.done) TextDecoration.LineThrough else null,
                )
                val parts = buildList {
                    if (showDate) add(relativeDayText(event.epochDay))
                    add(event.minuteOfDay?.let { minuteText(it) } ?: "dia inteiro")
                    add(type.singular)
                    event.place?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                Text(parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (event.remindMinutesBefore != null && !event.done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" ${reminderLabel(event.remindMinutesBefore)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onToggleDone) {
                if (event.done) Icon(Icons.Filled.CheckCircle, "Marcar como não feito", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Filled.RadioButtonUnchecked, "Marcar como feito")
            }
        }
    }
}

/** Formulário de evento (novo ou edição). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventEditor(
    initial: HealthEventEntity,
    onDismiss: () -> Unit,
    onSave: (HealthEventEntity, Int) -> Unit,
    onDelete: (HealthEventEntity) -> Unit,
) {
    val isNew = initial.id == 0L
    var type by remember { mutableStateOf(healthTypeOf(initial.type)) }
    var epochDay by remember { mutableStateOf(initial.epochDay) }
    var minute by remember { mutableStateOf(initial.minuteOfDay) }
    var title by remember { mutableStateOf(initial.title) }
    var place by remember { mutableStateOf(initial.place.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var remind by remember { mutableStateOf(initial.remindMinutesBefore) }
    var done by remember { mutableStateOf(initial.done) }
    var repeat by remember { mutableStateOf(false) }
    var repeatDays by remember { mutableStateOf("7") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotifications = rememberNotificationAsker()

    val days = if (isNew && repeat) (repeatDays.toIntOrNull() ?: 0) else 1
    val valid = days in 1..366

    val save = {
        onSave(
            initial.copy(
                type = type.name,
                epochDay = epochDay,
                minuteOfDay = minute,
                title = title.trim().ifBlank { type.singular },
                place = place.trim().ifBlank { null },
                notes = notes.trim().ifBlank { null },
                remindMinutesBefore = remind,
                done = done,
            ),
            days,
        )
    }

    FormDialog(title = if (isNew) "Novo evento" else "Editar evento", onDismiss = onDismiss, onSave = save, saveEnabled = valid) {
        Text("Tipo", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CalendarTypes.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(t.singular) },
                    leadingIcon = { Icon(t.icon(), null, Modifier.size(18.dp), tint = t.color()) },
                )
            }
        }

        HelpField(title, { title = it }, "Título", "Ex.: Consulta cardiologista. Se ficar vazio, usa \"${type.singular}\".")
        val suggestions = titleSuggestions(type).filter { it != title && matches(title, it) }
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(6).forEach { s -> AssistChip(onClick = { title = s }, label = { Text(s) }) }
            }
        }

        Text("Quando", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.DateRange, null)
                Text(" ${dayText(epochDay)}")
            }
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Schedule, null)
                Text(" ${minute?.let { minuteText(it) } ?: "Sem hora"}")
            }
        }
        if (minute != null) {
            TextButton(onClick = { minute = null }) { Text("Tirar o horário (dia inteiro)") }
        } else {
            Text("Sem hora, o evento vale o dia inteiro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HelpField(place, { place = it }, "Local", "Ex.: Clínica Vida, sala 204 (opcional)")
        HelpField(notes, { notes = it }, "Observações", "Ex.: levar exames antigos, ir em jejum (opcional)", singleLine = false)

        Text("Lembrete", fontWeight = FontWeight.Bold)
        val options = if (ReminderOptions.any { it.first == remind }) ReminderOptions else ReminderOptions + (remind to reminderLabel(remind))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = remind == value,
                    onClick = {
                        remind = value
                        if (value != null) askNotifications()
                    },
                    label = { Text(label) },
                )
            }
        }
        Text(
            if (minute == null) "Sem horário marcado, o aviso usa 9h da manhã do dia. O aviso pode chegar alguns minutos depois."
            else "O aviso aparece no celular e pode chegar alguns minutos depois do horário.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isNew && type == HealthType.MEDICACAO) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Repetir todo dia", fontWeight = FontWeight.Bold)
                        Text("Cria um horário por dia, no mesmo horário.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
                if (repeat) {
                    NumberField(
                        value = repeatDays,
                        onValueChange = { repeatDays = it },
                        label = "Por quantos dias",
                        suffix = "dias",
                        decimal = false,
                        help = "Ex.: 7 para uma semana de antibiótico (até 366).",
                        error = if (!valid) "Digite de 1 a 366 dias." else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (!isNew) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Já foi feito", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = done, onCheckedChange = { done = it })
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(" Apagar evento", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickDate) DatePick(epochDay, onDismiss = { pickDate = false }, onPick = { epochDay = it })
    if (pickTime) TimePick(minute, onDismiss = { pickTime = false }, onPick = { minute = it })
    if (confirmDelete) {
        ConfirmDialog(
            title = "Apagar evento?",
            text = "\"${initial.title.ifBlank { type.singular }}\" de ${dayText(initial.epochDay)} será apagado, junto com o lembrete.",
            confirmLabel = "Apagar",
            onConfirm = { onDelete(initial) },
            onDismiss = { confirmDelete = false },
        )
    }
}
