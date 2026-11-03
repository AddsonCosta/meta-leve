package com.addsoncosta.evolui.ui.today

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.addsoncosta.evolui.data.MealEntity
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.data.WorkoutEntity
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.health.care.DatePick
import java.time.LocalDate

/** Botão que mostra o dia e abre o calendário (só dias até hoje). */
@Composable
internal fun DayButton(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }) {
        Icon(Icons.Filled.CalendarMonth, null)
        Text("  " + if (date == LocalDate.now()) "Hoje" else date.format(DayFormat))
    }
    if (picking) {
        DatePick(date.toEpochDay(), onDismiss = { picking = false }) { day ->
            onPick(LocalDate.ofEpochDay(day).coerceAtMost(LocalDate.now()))
        }
    }
}

/** Corrigir um alimento já registrado: quantidade, refeição e dia. */
@Composable
internal fun EditMealDialog(meal: MealEntity, onDismiss: () -> Unit, onSave: (grams: Double, type: MealType, date: LocalDate) -> Unit) {
    var text by remember { mutableStateOf(meal.grams.fmt()) }
    var type by remember { mutableStateOf(MealType.entries.firstOrNull { it.name == meal.mealType } ?: MealType.SNACK) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(meal.epochDay)) }
    val grams = text.toDecimal()
    val valid = grams != null && grams > 0 && grams <= 5000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.name, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    text, { text = it }, "Quantidade", suffix = "g",
                    help = if (valid && meal.grams > 0) "${(meal.kcal * grams!! / meal.grams).fmt()} kcal" else null,
                    error = if (text.isNotEmpty() && !valid) "Entre 1 e 5000 g" else null,
                )
                Text("Refeição", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                }
                Text("Dia", style = MaterialTheme.typography.labelLarge)
                DayButton(date) { date = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(grams!!, type, date)
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Corrigir um treino lançado no Evolui: minutos e dia. */
@Composable
internal fun EditWorkoutDialog(workout: WorkoutEntity, onDismiss: () -> Unit, onSave: (minutes: Double, date: LocalDate) -> Unit) {
    var text by remember { mutableStateOf(workout.minutes.fmt()) }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(workout.epochDay)) }
    val minutes = text.toDecimal()
    val valid = minutes != null && minutes > 0 && minutes <= 1440
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(workout.name, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    text, { text = it }, "Duração", suffix = "min",
                    help = if (valid && workout.minutes > 0) "${(workout.kcal * minutes!! / workout.minutes).fmt()} kcal" else null,
                    error = if (text.isNotEmpty() && !valid) "Entre 1 e 1440 minutos" else null,
                )
                Text("Dia", style = MaterialTheme.typography.labelLarge)
                DayButton(date) { date = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(minutes!!, date)
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
