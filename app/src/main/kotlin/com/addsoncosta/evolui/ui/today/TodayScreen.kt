package com.addsoncosta.evolui.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.MiniRing
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.Ring
import com.addsoncosta.evolui.ui.components.Stat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.share.SharePeriod
import com.addsoncosta.evolui.ui.share.ShareSheet
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(onAddFood: () -> Unit, onAddExercise: () -> Unit, onOpenProfile: () -> Unit) {
    val container = appContainer()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.syncHealth() }
    val s = state ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

    var editWeight by rememberSaveable { mutableStateOf(false) }
    var editActivity by rememberSaveable { mutableStateOf(false) }
    var editNote by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val isToday = s.date == LocalDate.now()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Cabeçalho.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline(if (isToday) greeting() + ", " + s.profile.name.substringBefore(" ") else "Registro do dia")
                    Text(if (isToday) "Hoje" else s.date.format(DayFormat), style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, "Compartilhar") }
                IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Settings, "Perfil e ajustes") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.shiftDay(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                    Text("Dia anterior")
                }
                Box(Modifier.weight(1f))
                if (!isToday) {
                    TextButton(onClick = { vm.shiftDay(1) }) {
                        Text("Próximo dia")
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }

        // Anel principal: quanto já comeu da meta do dia.
        item {
            val goal = s.summary.intakeGoal.coerceAtLeast(1.0)
            val progress = (s.summary.eaten / goal).toFloat()
            val over = s.summary.budget < 0
            Panel {
                Ring(progress, Evolui.Neon, size = 230.dp, stroke = 22.dp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            kotlin.math.abs(s.summary.budget).fmt(),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(if (over) "kcal acima da meta" else "kcal restantes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(s.summary.intakeGoal.fmt(), "Meta", color = Evolui.Neon)
                    Stat(s.summary.eaten.fmt(), "Comido")
                    Stat(s.summary.exercise.fmt(), "Treino", color = Evolui.Orange)
                }
                Text(
                    "A meta já soma as calorias dos treinos: quanto mais você se mexe, mais pode comer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.summary.belowSafeMinimum) {
                    Text(
                        "Sua meta ficou abaixo de 1.200 kcal. Em Perfil, escolha um ritmo mais lento.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Macronutrientes.
        item {
            val m = s.summary.macros
            fun p(eaten: Double, goal: Double) = if (goal <= 0) 0f else (eaten / goal).toFloat()
            Panel {
                Text("Macronutrientes", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniRing("Carbo", "${s.eatenCarb.fmt()} / ${m.carbG.fmt()} g", p(s.eatenCarb, m.carbG), Evolui.Cyan)
                    MiniRing("Proteína", "${s.eatenProtein.fmt()} / ${m.proteinG.fmt()} g", p(s.eatenProtein, m.proteinG), Evolui.Neon)
                    MiniRing("Gordura", "${s.eatenFat.fmt()} / ${m.fatG.fmt()} g", p(s.eatenFat, m.fatG), Evolui.Orange)
                }
            }
        }

        // Samsung Health.
        item {
            val h = health
            if (h != null) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, null, tint = Evolui.Red)
                        Text("  Samsung Health", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(h.steps.toDouble().fmt(), "passos", color = Evolui.Cyan)
                        Stat(h.activeKcal.fmt(), "kcal ativas", color = Evolui.Orange)
                        Stat("${h.workouts.size}", if (h.workouts.size == 1) "treino" else "treinos")
                    }
                    if (h.workouts.isNotEmpty()) {
                        Text("Os treinos entram sozinhos na lista abaixo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (container.health.available) {
                Panel(Modifier.clickable(onClick = onOpenProfile)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, null, tint = Evolui.Red)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Conectar o Samsung Health", fontWeight = FontWeight.Bold)
                            Text("Traga passos, treinos e peso automaticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                }
            }
        }

        // Peso, atividade e anotação.
        item {
            Panel {
                EditableRow("Peso", "${s.weightKg.fmt(1)} kg" + if (s.log?.weightKg == null) " (último registro)" else "") { editWeight = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                EditableRow("Atividade do dia", s.activity.label) { editActivity = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                EditableRow("Anotação", s.log?.note ?: "Toque para escrever") { editNote = true }
            }
        }

        // Refeições.
        item {
            Panel {
                SectionHeader("Refeições", "${s.summary.eaten.fmt()} kcal", onAddFood)
                if (s.meals.isEmpty()) Text("Nada registrado ainda. Toque em + para adicionar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                MealType.entries.forEach { type ->
                    val meals = s.meals.filter { it.mealType == type.name }
                    if (meals.isNotEmpty()) {
                        Text(type.label, style = MaterialTheme.typography.labelLarge, color = Evolui.Neon)
                        meals.forEach { meal -> EntryRow(meal.name, "${meal.grams.fmt()} g", meal.kcal) { vm.deleteMeal(meal) } }
                    }
                }
            }
        }

        // Exercícios.
        item {
            Panel {
                SectionHeader("Exercícios", "${s.summary.exercise.fmt()} kcal", onAddExercise)
                if (s.workouts.isEmpty()) Text("Nenhum exercício hoje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.workouts.forEach { w ->
                    EntryRow(w.name, "${w.minutes.fmt()} min" + if (w.externalId != null) " · Samsung Health" else "", w.kcal, Evolui.Orange) { vm.deleteWorkout(w) }
                }
            }
        }
    }

    if (sharing) ShareSheet(onDismiss = { sharing = false }, initialPeriod = SharePeriod.Today)

    if (editWeight) {
        var text by remember { mutableStateOf(s.weightKg.fmt(1)) }
        val value = text.toDecimal()
        AlertDialog(
            onDismissRequest = { editWeight = false },
            title = { Text("Peso do dia") },
            text = {
                Column {
                    Text("O melhor horário é de manhã, antes do café.", style = MaterialTheme.typography.bodySmall)
                    NumberField(
                        text, { text = it }, "Peso", suffix = "kg",
                        help = "Em quilos. Ex.: 98,8",
                        error = if (text.isNotEmpty() && (value == null || value !in 25.0..400.0)) "Entre 25 e 400 kg" else null,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value in 25.0..400.0, onClick = {
                    vm.setWeight(value!!)
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
                    Text("Sem contar os exercícios, como foi seu dia?", style = MaterialTheme.typography.bodySmall)
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
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(level.label)
                                Text(level.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
            text = {
                OutlinedTextField(
                    text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex.: dormi mal, comi fora, treino pesado") },
                )
            },
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

@Composable
private fun EditableRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, maxLines = 2, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Editar $label")
    }
}

@Composable
private fun SectionHeader(title: String, total: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, null)
            Text(" Adicionar")
        }
    }
}

@Composable
private fun EntryRow(title: String, detail: String, kcal: Double, color: Color = MaterialTheme.colorScheme.onSurface, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${kcal.fmt()} kcal", fontWeight = FontWeight.Bold, color = color)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Remover", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
