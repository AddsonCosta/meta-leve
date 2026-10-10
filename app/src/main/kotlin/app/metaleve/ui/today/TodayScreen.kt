package app.metaleve.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.metaleve.core.ActivityLevel
import app.metaleve.data.MealType
import app.metaleve.ui.appContainer
import app.metaleve.ui.components.DayFormat
import app.metaleve.ui.components.NumberField
import app.metaleve.ui.components.PerformanceGauge
import app.metaleve.ui.components.fmt
import app.metaleve.ui.components.toDecimal
import java.time.LocalDate

@Composable
fun TodayScreen(onAddFood: () -> Unit, onAddExercise: () -> Unit) {
    val container = appContainer()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

    var editWeight by rememberSaveable { mutableStateOf(false) }
    var editActivity by rememberSaveable { mutableStateOf(false) }
    var editNote by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Dia anterior") }
                Text(
                    if (s.date == LocalDate.now()) "Hoje" else s.date.format(DayFormat),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.shiftDay(1) }, enabled = s.date.isBefore(LocalDate.now())) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo dia")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PerformanceGauge(s.summary.performance)
                    Text(
                        if (s.summary.budget >= 0) "Você ainda pode consumir ${s.summary.budget.fmt()} kcal"
                        else "Você passou ${(-s.summary.budget).fmt()} kcal da meta",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Gasto base", s.summary.burn)
                        Stat("Exercício", s.summary.exercise)
                        Stat("Consumido", s.summary.eaten)
                        Stat("Meta", s.summary.target)
                    }
                    if (s.summary.belowSafeMinimum) {
                        Text(
                            "Sua meta de consumo está abaixo de 1.200 kcal. Considere um ritmo mais lento.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Macronutrientes", fontWeight = FontWeight.SemiBold)
                    MacroBar("Carboidratos", s.eatenCarb, s.summary.macros.carbG)
                    MacroBar("Proteínas", s.eatenProtein, s.summary.macros.proteinG)
                    MacroBar("Gorduras", s.eatenFat, s.summary.macros.fatG)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    EditableRow("Peso", "${s.weightKg.fmt(1)} kg" + if (s.log?.weightKg == null) " (último registro)" else "") { editWeight = true }
                    HorizontalDivider()
                    EditableRow("Atividade do dia", s.activity.label) { editActivity = true }
                    HorizontalDivider()
                    EditableRow("Anotação", s.log?.note ?: "Nenhuma") { editNote = true }
                }
            }
        }
        item {
            SectionHeader("Refeições", onAddFood)
        }
        if (s.meals.isEmpty()) item { Text("Nada registrado ainda.", style = MaterialTheme.typography.bodyMedium) }
        MealType.entries.forEach { type ->
            val meals = s.meals.filter { it.mealType == type.name }
            if (meals.isNotEmpty()) {
                item { Text(type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                items(meals, key = { "m${it.id}" }) { meal ->
                    EntryRow(meal.name, "${meal.grams.fmt()} g", meal.kcal) { vm.deleteMeal(meal) }
                }
            }
        }
        item { SectionHeader("Exercícios", onAddExercise) }
        if (s.workouts.isEmpty()) item { Text("Nenhum exercício registrado.", style = MaterialTheme.typography.bodyMedium) }
        items(s.workouts, key = { "w${it.id}" }) { w ->
            EntryRow(w.name, "${w.minutes.fmt()} min", w.kcal) { vm.deleteWorkout(w) }
        }
    }

    if (editWeight) {
        var text by remember { mutableStateOf(s.weightKg.fmt(1)) }
        AlertDialog(
            onDismissRequest = { editWeight = false },
            title = { Text("Peso do dia") },
            text = {
                Column {
                    Text("O melhor horário é de manhã, antes do café.", style = MaterialTheme.typography.bodySmall)
                    NumberField(text, { text = it }, "Peso", suffix = "kg")
                }
            },
            confirmButton = {
                TextButton(enabled = text.toDecimal()?.let { it in 25.0..400.0 } == true, onClick = {
                    vm.setWeight(text.toDecimal()!!)
                    editWeight = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editWeight = false }) { Text("Cancelar") } },
        )
    }
    if (editActivity) {
        AlertDialog(
            onDismissRequest = { editActivity = false },
            title = { Text("Atividade do dia") },
            text = {
                Column {
                    ActivityLevel.entries.forEach { level ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = s.activity == level, role = Role.RadioButton, onClick = {
                                    vm.setActivity(level)
                                    editActivity = false
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.activity == level, onClick = null)
                            Text(level.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editActivity = false }) { Text("Fechar") } },
        )
    }
    if (editNote) {
        var text by remember { mutableStateOf(s.log?.note ?: "") }
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("Anotação do dia") },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNote(text)
                    editNote = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.fmt(), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MacroBar(label: String, eaten: Double, goal: Double) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${eaten.fmt()} / ${goal.fmt()} g", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { if (goal <= 0) 0f else (eaten / goal).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun EditableRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, maxLines = 2)
        }
        IconButton(onClick = onClick) { Icon(Icons.Filled.Edit, "Editar $label") }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, null)
            Text("Adicionar")
        }
    }
}

@Composable
private fun EntryRow(title: String, detail: String, kcal: Double, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Text("${kcal.fmt()} kcal", fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remover") }
    }
}
