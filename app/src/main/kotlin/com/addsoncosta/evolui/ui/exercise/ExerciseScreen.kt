package com.addsoncosta.evolui.ui.exercise

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.data.ExerciseEntity
import com.addsoncosta.evolui.data.WorkoutEntity
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.PtBr
import com.addsoncosta.evolui.ui.components.ShortDateFormat
import com.addsoncosta.evolui.ui.components.Stat
import com.addsoncosta.evolui.ui.components.TimeFormat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.today.sourceLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class ExerciseTab { REGISTER, ROUTINE, HISTORY }

/** Seção Treino: registrar exercícios (com busca) e ver o histórico de atividades. */
@Composable
fun ExerciseScreen() {
    val container = appContainer()
    val vm: ExerciseViewModel = viewModel(factory = viewModelFactory { initializer { ExerciseViewModel(container) } })
    var tab by rememberSaveable { mutableStateOf(ExerciseTab.REGISTER) }

    BackHandler(enabled = tab != ExerciseTab.REGISTER) { tab = ExerciseTab.REGISTER }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Overline("Treino")
            Text("Exercícios", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == ExerciseTab.REGISTER,
                    onClick = { tab = ExerciseTab.REGISTER },
                    label = { Text("Registrar") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                )
                FilterChip(
                    selected = tab == ExerciseTab.ROUTINE,
                    onClick = { tab = ExerciseTab.ROUTINE },
                    label = { Text("Minha rotina") },
                    leadingIcon = { Icon(Icons.Filled.EventRepeat, null) },
                )
                FilterChip(
                    selected = tab == ExerciseTab.HISTORY,
                    onClick = { tab = ExerciseTab.HISTORY },
                    label = { Text("Histórico") },
                    leadingIcon = { Icon(Icons.Filled.History, null) },
                )
            }
        }
        when (tab) {
            ExerciseTab.REGISTER -> RegisterTab(vm, Modifier.weight(1f))
            ExerciseTab.ROUTINE -> RoutineTab(vm, Modifier.weight(1f))
            ExerciseTab.HISTORY -> HistoryTab(vm, Modifier.weight(1f))
        }
    }
}

// Registrar

@Composable
private fun RegisterTab(vm: ExerciseViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val person by vm.person.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "info") {
            Panel {
                Text(
                    "Registrando em: " + if (date == LocalDate.now()) "hoje" else date.format(DayFormat),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(personLine(person), color = Evolui.Orange, fontWeight = FontWeight.Bold)
                Text(
                    "Toque num exercício, digite os minutos e veja na hora quanto você gastou. Para outro dia, troque a data na tela Hoje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "busca") {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar exercício") },
                placeholder = { Text("Ex.: corrida, bike, beach tennis") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Limpar busca") } }
                } else null,
                singleLine = true,
            )
        }

        item(key = "categorias") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "todas") {
                    FilterChip(selected = category == null, onClick = { vm.setCategory(null) }, label = { Text("Todas") })
                }
                item(key = "favoritos") {
                    FilterChip(
                        selected = category == ExerciseViewModel.FAVORITES,
                        onClick = { vm.setCategory(if (category == ExerciseViewModel.FAVORITES) null else ExerciseViewModel.FAVORITES) },
                        label = { Text("Favoritos") },
                        leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) },
                    )
                }
                items(categories, key = { "c-$it" }) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { vm.setCategory(if (category == c) null else c) },
                        label = { Text(c) },
                    )
                }
            }
        }

        if (exercises.isEmpty()) {
            item(key = "vazio") {
                Panel {
                    Text(
                        when {
                            query.isNotBlank() -> "Nada encontrado para “${query.trim()}”."
                            category == ExerciseViewModel.FAVORITES -> "Você ainda não tem favoritos."
                            else -> "Nenhum exercício nesta categoria."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (category == ExerciseViewModel.FAVORITES && query.isBlank()) {
                            "Toque na estrela ao lado de um exercício para ele aparecer aqui e no topo da lista."
                        } else {
                            "Tente outra palavra (ex.: caminhada, musculação, dança) ou escolha \"Todas\". Se não achar, crie o seu exercício."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Text(" Criar meu exercício")
                    }
                }
            }
        }

        items(exercises, key = { it.id }) { ex ->
            ListItem(
                overlineContent = { Text(ex.category) },
                headlineContent = { Text(ex.name) },
                supportingContent = { Text("≈ ${person.kcal(ex.met, 30.0).fmt()} kcal em 30 min") },
                trailingContent = {
                    IconButton(onClick = { vm.toggleFavorite(ex) }) {
                        Icon(
                            if (ex.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (ex.favorite) "Tirar dos favoritos" else "Marcar como favorito",
                            tint = if (ex.favorite) Evolui.Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { selected = ex },
            )
        }

        if (exercises.isNotEmpty()) {
            item(key = "novo") {
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Criar meu exercício")
                }
            }
        }

        item(key = "fonte") {
            Text(
                "Gasto estimado pelo valor MET de cada atividade (Compendium of Physical Activities), ajustado ao seu gasto em repouso " +
                    "(fórmula de Mifflin-St Jeor, com seu peso, altura, idade e sexo). É uma estimativa; relógios e cintas medem melhor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    selected?.let { ex ->
        LogDialog(
            exercise = ex,
            person = person,
            onDismiss = { selected = null },
            onLog = { minutes, start ->
                vm.log(ex, minutes, start)
                Toast.makeText(context, "${ex.name}: ${minutes.fmt()} min registrados", Toast.LENGTH_SHORT).show()
                selected = null
            },
            onDelete = if (ex.custom) {
                { vm.delete(ex); selected = null }
            } else null,
        )
    }

    if (creating) {
        CreateExerciseDialog(
            initialName = query.trim(),
            person = person,
            onDismiss = { creating = false },
            onSave = { name, met ->
                vm.saveCustom(name, met)
                creating = false
            },
        )
    }
}

/** "Calculado para você: 98 kg, 1,78 m, 45 anos". */
private fun personLine(p: PersonInfo): String {
    val parts = mutableListOf("${p.weightKg.fmt(1)} kg")
    p.heightCm?.let { parts += "${(it / 100).fmt(2)} m" }
    p.age?.let { parts += "$it anos" }
    return "Calculado para você: " + parts.joinToString(", ")
}

@Composable
private fun LogDialog(
    exercise: ExerciseEntity,
    person: PersonInfo,
    onDismiss: () -> Unit,
    onLog: (Double, LocalTime?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var minutes by remember(exercise.id) { mutableStateOf("30") }
    var start by remember(exercise.id) { mutableStateOf("") }
    var confirmDelete by remember(exercise.id) { mutableStateOf(false) }
    val m = minutes.toDecimal()
    val validMinutes = m != null && m > 0 && m <= 1440
    val startTime = parseTime(start)
    val startError = start.isNotBlank() && startTime == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(
                    minutes, { minutes = it }, "Duração", Modifier.fillMaxWidth(), suffix = "min",
                    help = "Quantos minutos durou. Ex.: 45",
                    error = if (minutes.isNotEmpty() && !validMinutes) "Digite de 1 a 1440 minutos" else null,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { v ->
                        FilterChip(selected = m == v.toDouble(), onClick = { minutes = v.toString() }, label = { Text("$v min") })
                    }
                }
                Text(
                    "≈ ${(if (validMinutes) person.kcal(exercise.met, m!!) else 0.0).fmt()} kcal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Evolui.Orange,
                    fontWeight = FontWeight.Bold,
                )
                Text(personLine(person), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = start,
                    onValueChange = { new -> start = new.filter { it.isDigit() || it == ':' || it == 'h' }.take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Começou às (opcional)") },
                    placeholder = { Text("Ex.: 07:30") },
                    supportingText = {
                        Text(if (startError) "Use horas e minutos, ex.: 07:30 ou 0730" else "Deixe vazio para contar que terminou agora.")
                    },
                    isError = startError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = validMinutes && !startError, onClick = { onLog(m!!, startTime) }) { Text("Registrar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null)
                        Text("Excluir")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir este exercício?") },
            text = { Text("\"${exercise.name}\" sai da sua lista. Os treinos já registrados continuam no histórico.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Aceita "7", "07:30", "7h30" ou "0730". */
private fun parseTime(text: String): LocalTime? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val (h, m) = when {
        ':' in t || 'h' in t -> {
            val parts = t.split(':', 'h')
            (parts.getOrNull(0)?.toIntOrNull() ?: return null) to (parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0)
        }
        t.length <= 2 -> (t.toIntOrNull() ?: return null) to 0
        t.length in 3..4 -> (t.dropLast(2).toIntOrNull() ?: return null) to (t.takeLast(2).toIntOrNull() ?: return null)
        else -> return null
    }
    if (h !in 0..23 || m !in 0..59) return null
    return LocalTime.of(h, m)
}

@Composable
private fun CreateExerciseDialog(initialName: String, person: PersonInfo, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var met by remember { mutableStateOf("") }
    val metValue = met.toDecimal()
    val validMet = metValue != null && metValue in 1.0..25.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    supportingText = { Text("Ex.: Aula de ritmos, Treino na praça") },
                    singleLine = true,
                )
                NumberField(
                    met, { met = it }, "Intensidade (MET)", Modifier.fillMaxWidth(),
                    help = "Quantas vezes gasta mais que o repouso. Leve 2 a 3; moderado 4 a 6; intenso 7 ou mais.",
                    error = if (met.isNotEmpty() && !validMet) "Use um valor de 1 a 25" else null,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Leve" to 3.0, "Moderado" to 5.0, "Intenso" to 8.0).forEach { (label, value) ->
                        FilterChip(selected = metValue == value, onClick = { met = value.fmt(1) }, label = { Text(label) })
                    }
                }
                if (validMet) {
                    Text(
                        "Em 30 min ≈ ${person.kcal(metValue!!, 30.0).fmt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = Evolui.Orange,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && validMet, onClick = { onSave(name.trim(), metValue!!) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// Histórico

@Composable
private fun HistoryTab(vm: ExerciseViewModel, modifier: Modifier) {
    val groups by vm.history.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val connected by vm.healthConnected.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val message by vm.syncMessage.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WorkoutEntity?>(null) }

    LaunchedEffect(Unit) { vm.onHistoryOpened() }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "semana") { WeekPanel(week) }

        item(key = "health") {
            Panel {
                if (connected) {
                    Text("Treinos de outros apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Treinos do Samsung Health, Strava e outros apps entram sozinhos, sem repetir o mesmo treino.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { vm.syncWeek() }, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
                        if (syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Buscando...")
                        } else {
                            Icon(Icons.Filled.Sync, null)
                            Text(" Buscar treinos dos últimos 7 dias")
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Evolui.Neon) }
                } else {
                    Text("Trazer treinos do Samsung Health e Strava", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Conecte o Health Connect em Perfil › Samsung Health. Depois, os treinos dos outros apps aparecem aqui sozinhos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            item(key = "vazio") {
                Panel {
                    Icon(Icons.Filled.FitnessCenter, null, tint = Evolui.Orange)
                    Text("Nenhuma atividade registrada ainda", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Vá em \"Registrar\", escolha um exercício e digite os minutos. Ele aparece aqui, separado por dia.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        groups.forEach { group ->
            item(key = "d-${group.date.toEpochDay()}") {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(group.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "${group.minutes.fmt()} min · ${group.kcal.fmt()} kcal",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(group.workouts, key = { "w-${it.id}" }) { w ->
                ListItem(
                    headlineContent = { Text(w.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(listOfNotNull(timeRange(w), "${w.minutes.fmt()} min", originLabel(w)).joinToString(" · "))
                    },
                    trailingContent = { Text("${w.kcal.fmt()} kcal", color = Evolui.Orange, fontWeight = FontWeight.Bold) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { editing = w },
                )
            }
        }

        if (groups.isNotEmpty()) {
            item(key = "dica") {
                Text(
                    "Toque numa atividade para mudar os minutos ou apagar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    editing?.let { w ->
        EditWorkoutDialog(
            workout = w,
            onDismiss = { editing = null },
            onSave = { minutes -> vm.updateMinutes(w, minutes); editing = null },
            onDelete = { vm.deleteWorkout(w); editing = null },
        )
    }
}

@Composable
private fun WeekPanel(week: WeekSummary) {
    Panel {
        Overline("Esta semana · ${week.start.format(ShortDateFormat)} a ${week.end.format(ShortDateFormat)}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(week.minutes.fmt(), "minutos", color = Evolui.Neon)
            Stat(week.kcal.fmt(), "kcal", color = Evolui.Orange)
            Stat("${week.count}", if (week.count == 1) "atividade" else "atividades")
        }
        LinearProgressIndicator(
            progress = { week.progress },
            modifier = Modifier.fillMaxWidth(),
            color = Evolui.Neon,
        )
        val left = WeekSummary.WHO_WEEKLY_MINUTES - week.minutes
        Text(
            if (left <= 0) {
                "Meta da OMS cumprida: pelo menos 150 minutos de atividade moderada na semana. Parabéns!"
            } else {
                "Faltam ${left.fmt()} min para a meta da OMS: 150 minutos de atividade moderada por semana (ex.: 30 min, 5 dias)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditWorkoutDialog(workout: WorkoutEntity, onDismiss: () -> Unit, onSave: (Double) -> Unit, onDelete: () -> Unit) {
    var minutes by remember(workout.id) { mutableStateOf(workout.minutes.fmt(1)) }
    var confirmDelete by remember(workout.id) { mutableStateOf(false) }
    val m = minutes.toDecimal()
    val valid = m != null && m > 0 && m <= 1440
    val imported = workout.externalId != null
    val kcal = if (valid && workout.minutes > 0) workout.kcal * m!! / workout.minutes else workout.kcal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(workout.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(dayLabel(LocalDate.ofEpochDay(workout.epochDay)), timeRange(workout), originLabel(workout)).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberField(
                    minutes, { minutes = it }, "Duração", Modifier.fillMaxWidth(), suffix = "min",
                    help = "As calorias mudam na mesma proporção.",
                    error = if (minutes.isNotEmpty() && !valid) "Digite de 1 a 1440 minutos" else null,
                )
                Text("≈ ${kcal.fmt()} kcal", style = MaterialTheme.typography.headlineSmall, color = Evolui.Orange, fontWeight = FontWeight.Bold)
                if (imported) {
                    Text(
                        "Este treino veio de ${sourceLabel(workout.source)}. A mudança vale só no Evolui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && m != workout.minutes, onClick = { onSave(m!!) }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar esta atividade?") },
            text = {
                Text(
                    when {
                        imported -> "Ela sai do Evolui e não volta na próxima sincronização. No ${sourceLabel(workout.source)} ela continua."
                        workout.hcWrittenId != null -> "Ela sai do Evolui e também do Health Connect (e do Samsung Health)."
                        else -> "Ela sai do Evolui e as calorias deixam de contar no dia."
                    }
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** De onde veio a atividade: Evolui, Samsung Health, Strava... */
private fun originLabel(w: WorkoutEntity): String = when {
    w.externalId != null || w.source != null -> sourceLabel(w.source)
    w.hcWrittenId != null -> "Evolui (enviado ao Samsung Health)"
    else -> "Evolui"
}

private fun timeRange(w: WorkoutEntity): String? {
    val start = w.startMillis ?: return null
    val zone = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(start).atZone(zone).format(TimeFormat)
    val e = w.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(TimeFormat) }
    return if (e != null) "$s–$e" else s
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val base = when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> date.format(DayFormat).replaceFirstChar { it.titlecase(PtBr) }
    }
    return if (date.year != today.year) "$base de ${date.year}" else base
}
