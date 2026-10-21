package com.addsoncosta.evolui.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.RoutineCalc
import com.addsoncosta.evolui.data.ExerciseEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.data.RoutineEntryEntity
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.DayOfWeek
import java.time.LocalDate

/** Categorias sugeridas primeiro na hora de montar a rotina. */
private val ROUTINE_CATEGORIES = listOf("Casa", "Quintal e jardim", "Compras e cuidados", "Reparos e reformas", "Trabalho")

/** Rascunho do editor: atividade nova (sem [entry]) ou uma já salva. */
private class RoutineDraft(val exercise: ExerciseEntity?, val entry: RoutineEntryEntity?) {
    val name: String get() = entry?.name ?: exercise?.name.orEmpty()
    val met: Double get() = entry?.met ?: exercise?.met ?: 0.0
}

/** Minha rotina: atividades fixas do dia a dia que entram sozinhas no gasto de cada dia marcado. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RoutineTab(vm: ExerciseViewModel, modifier: Modifier) {
    val routine by vm.routine.collectAsStateWithLifecycle()
    val person by vm.person.collectAsStateWithLifecycle()
    val activity by vm.defaultActivity.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<RoutineDraft?>(null) }

    val today = LocalDate.now()
    val todayKcal = routine.filter { RoutineCalc.appliesOn(it.daysMask, today) && it.startEpochDay <= today.toEpochDay() }
        .sumOf { person.netKcal(it.met, it.minutes) }
    val weekKcal = routine.sumOf { e -> DayOfWeek.entries.count { RoutineCalc.has(e.daysMask, it) } * person.netKcal(e.met, e.minutes) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "info") {
            Panel {
                Text("Minha rotina", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Monte uma vez as atividades que você faz quase todo dia (lavar louça, levar as crianças, trabalho em pé...). " +
                        "Elas entram sozinhas no gasto de cada dia marcado. Na tela Hoje dá para desmarcar o que não fez naquele dia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (routine.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column {
                            Text(todayKcal.fmt(), style = MaterialTheme.typography.titleLarge, color = Evolui.Orange)
                            Text("kcal hoje", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text(weekKcal.fmt(), style = MaterialTheme.typography.titleLarge, color = Evolui.Orange)
                            Text("kcal na semana", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("  Adicionar atividade")
                }
            }
        }

        val level = activity
        if (routine.isNotEmpty() && level != null && level != ActivityLevel.SEDENTARY) {
            item(key = "aviso") {
                Panel {
                    Text("Para não contar duas vezes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Seu perfil está como \"${level.label}\", que já soma um pouco de movimento do dia. Com a rotina registrada, " +
                            "o mais certo é escolher \"Sedentário\" em Perfil › Nível de atividade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (routine.isEmpty()) {
            item(key = "vazio") {
                Panel {
                    Icon(Icons.Filled.EventRepeat, null, tint = Evolui.Orange)
                    Text("Sua rotina ainda está vazia", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Toque em \"Adicionar atividade\", escolha da lista, diga quantos minutos e em quais dias da semana.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(routine, key = { "r-${it.id}" }) { e ->
            ListItem(
                headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${e.minutes.fmt()} min · ${RoutineCalc.daysLabel(e.daysMask)}") },
                trailingContent = {
                    Text("${person.netKcal(e.met, e.minutes).fmt()} kcal", color = Evolui.Orange, fontWeight = FontWeight.Bold)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { draft = RoutineDraft(null, e) },
            )
        }

        item(key = "fonte") {
            Text(
                "Conta só o gasto acima do repouso (MET da atividade menos 1), porque o repouso já está no seu gasto do dia. " +
                    "Valores MET do Compendium of Physical Activities (2024), ajustados ao seu gasto em repouso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    if (picking) {
        val all by vm.allExercises.collectAsStateWithLifecycle()
        PickActivityDialog(
            exercises = all,
            onDismiss = { picking = false },
            onPick = { ex ->
                picking = false
                draft = RoutineDraft(ex, null)
            },
        )
    }

    draft?.let { d ->
        RoutineEntryDialog(
            draft = d,
            person = person,
            onDismiss = { draft = null },
            onSave = { minutes, mask ->
                val e = d.entry
                vm.saveRoutine(
                    e?.copy(minutes = minutes, daysMask = mask) ?: RoutineEntryEntity(
                        exerciseId = d.exercise?.id,
                        name = d.name,
                        met = d.met,
                        minutes = minutes,
                        daysMask = mask,
                        startEpochDay = LocalDate.now().toEpochDay(),
                    )
                )
                draft = null
            },
            onDelete = d.entry?.let { e -> { vm.deleteRoutine(e); draft = null } },
        )
    }
}

/** Escolher a atividade da lista, com busca sem acento; as do dia a dia aparecem primeiro. */
@Composable
private fun PickActivityDialog(exercises: List<ExerciseEntity>, onDismiss: () -> Unit, onPick: (ExerciseEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val keyed = remember(exercises) { exercises.map { it to Repository.normalize(it.name + " " + it.category) } }
    val shown = remember(keyed, query) {
        val words = Repository.normalize(query).split(' ').filter { it.isNotBlank() }
        keyed.filter { (_, key) -> words.all { it in key } }
            .map { it.first }
            .sortedWith(compareBy<ExerciseEntity> { ROUTINE_CATEGORIES.indexOf(it.category).let { i -> if (i < 0) 99 else i } }.thenBy { it.name })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolha a atividade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex.: louça, varrer, cuidar") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Limpar busca") } }
                    } else null,
                    singleLine = true,
                )
                if (shown.isEmpty()) {
                    Text("Nada encontrado. Tente outra palavra.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(shown, key = { it.id }) { ex ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(vertical = 8.dp)) {
                            Text(ex.name)
                            Text(ex.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Minutos e dias da semana de uma atividade da rotina. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RoutineEntryDialog(
    draft: RoutineDraft,
    person: PersonInfo,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var minutes by remember { mutableStateOf(draft.entry?.minutes?.fmt(1) ?: "20") }
    var mask by remember { mutableStateOf(draft.entry?.daysMask ?: RoutineCalc.ALL_DAYS) }
    var confirmDelete by remember { mutableStateOf(false) }
    val m = minutes.toDecimal()
    val validMinutes = m != null && m > 0 && m <= 1440
    val valid = validMinutes && mask != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    minutes, { minutes = it }, "Minutos por dia", Modifier.fillMaxWidth(), suffix = "min",
                    help = "Quanto tempo, em média, num dia. Ex.: 20",
                    error = if (minutes.isNotEmpty() && !validMinutes) "Digite de 1 a 1440 minutos" else null,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30, 60).forEach { v ->
                        FilterChip(selected = m == v.toDouble(), onClick = { minutes = v.toString() }, label = { Text("$v min") })
                    }
                }
                Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = RoutineCalc.has(mask, day),
                            onClick = { mask = RoutineCalc.toggle(mask, day) },
                            label = { Text(RoutineCalc.shortName(day)) },
                        )
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos os dias" to RoutineCalc.ALL_DAYS, "Seg a sex" to RoutineCalc.WEEKDAYS, "Fim de semana" to RoutineCalc.WEEKEND)
                        .forEach { (label, value) ->
                            FilterChip(selected = mask == value, onClick = { mask = value }, label = { Text(label) })
                        }
                }
                if (mask == 0) Text("Escolha pelo menos um dia.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(
                    "≈ ${(if (validMinutes) person.netKcal(draft.met, m!!) else 0.0).fmt()} kcal por dia",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Evolui.Orange,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Só o gasto acima do repouso (MET ${draft.met.fmt(1)} − 1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(m!!, mask) }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null)
                        Text("Tirar")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Tirar da rotina?") },
            text = { Text("\"${draft.name}\" deixa de contar nos próximos dias e também nos dias anteriores.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Tirar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
