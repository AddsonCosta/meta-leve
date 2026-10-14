package com.addsoncosta.evolui.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.data.Measure
import com.addsoncosta.evolui.data.MeasureKind
import com.addsoncosta.evolui.data.PortionChoice
import com.addsoncosta.evolui.data.Portions
import com.addsoncosta.evolui.data.nutritionFor
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Refeição sugerida pela hora do dia. */
internal fun defaultMealType(): MealType {
    val hour = LocalTime.now().hour
    return when {
        hour < 10 -> MealType.BREAKFAST
        hour < 15 -> MealType.LUNCH
        hour < 18 -> MealType.SNACK
        else -> MealType.DINNER
    }
}

/** Os 4 tipos de refeição, quebrando linha quando não cabem (fonte grande). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealTypePicker(selected: MealType, onSelect: (MealType) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { t ->
            FilterChip(selected = selected == t, onClick = { onSelect(t) }, label = { Text(t.label) })
        }
    }
}

/** Texto da quantidade como na tela Hoje: "1,5 xícara (≈ 360 g)" ou "120 g". */
internal fun amountLabel(grams: Double, amount: Double?, unit: String?): String =
    if (unit != null && amount != null && unit != Portions.GRAMS) {
        if (unit == Portions.ML) "${amount.fmt()} ml (≈ ${grams.fmt()} g)" else "${amount.fmt(2)} $unit (≈ ${grams.fmt()} g)"
    } else {
        "${grams.fmt()} g"
    }

/** Explica a medida escolhida: "1 copo americano = 190 ml ≈ 196 g". */
private fun describe(m: Measure): String = when (m.kind) {
    MeasureKind.GRAMS -> "Peso em gramas (o mais exato, se você tiver balança)."
    MeasureKind.ML -> "1 ml ≈ ${m.gramsPerUnit.fmt(2)} g deste alimento."
    MeasureKind.VOLUME -> "1 ${m.name} = ${(m.mlPerUnit ?: 0.0).fmt()} ml ≈ ${m.gramsPerUnit.fmt()} g"
    MeasureKind.PORTION -> "1 ${m.name} ≈ ${m.gramsPerUnit.fmt(1)} g (medida caseira aproximada)"
    MeasureKind.CUSTOM -> "1 ${m.name} = ${m.gramsPerUnit.fmt(1)} g (medida criada por você)"
}

/**
 * Escolher medida e quantidade de um alimento. Usado para registrar (com o tipo de refeição)
 * e para montar refeições salvas ([showMealType] = false).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PortionDialog(
    food: FoodEntity,
    foods: FoodRepository,
    onDismiss: () -> Unit,
    onConfirm: (PortionChoice, MealType) -> Unit,
    confirmLabel: String = "Registrar",
    showMealType: Boolean = true,
    initial: PortionChoice? = null,
    onEditFood: (() -> Unit)? = null,
    onDeleteFood: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val portions by remember(food.id) { foods.portions(food.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val measures = remember(food, portions) { Portions.measures(food, portions) }
    var measureKey by remember(food.id) { mutableStateOf(Portions.GRAMS) }
    var amountText by remember(food.id) { mutableStateOf(Portions.inputText(initial?.grams ?: 100.0)) }
    var type by remember { mutableStateOf(defaultMealType()) }
    var ready by remember(food.id) { mutableStateOf(false) }
    var addingMeasure by remember { mutableStateOf(false) }
    var deletingMeasure by remember { mutableStateOf<Measure?>(null) }

    // Medida inicial: a da escolha anterior, a última usada com este alimento ou a porção típica.
    LaunchedEffect(food.id) {
        val list = Portions.measures(food, foods.portions(food.id).first())
        val (m, amount) = if (initial != null) {
            Portions.fromChoice(list, initial.grams, initial.amount, initial.unit)
        } else {
            Portions.defaultChoice(food, list, foods.lastMeal(food.id))
        }
        measureKey = m.key
        amountText = Portions.inputText(amount)
        ready = true
    }

    val measure = measures.firstOrNull { it.key == measureKey } ?: Portions.gramsMeasure(measures)
    val amount = amountText.toDecimal()
    val grams = amount?.let { measure.grams(it) }
    val n = food.nutritionFor(grams ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${food.category} · ${food.kcal.fmt()} kcal em 100 g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Medida", style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    measures.forEach { m ->
                        FilterChip(
                            selected = m.key == measure.key,
                            onClick = {
                                if (m.key != measure.key) {
                                    measureKey = m.key
                                    amountText = Portions.inputText(Portions.defaultAmount(m))
                                }
                            },
                            label = { Text(m.name) },
                        )
                    }
                    AssistChip(
                        onClick = { addingMeasure = true },
                        label = { Text("Minha medida") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
                Text(describe(measure), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (measure.kind == MeasureKind.CUSTOM) {
                    TextButton(onClick = { deletingMeasure = measure }) {
                        Icon(Icons.Filled.Delete, null)
                        Text(" Apagar esta medida")
                    }
                }
                NumberField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = when (measure.kind) {
                        MeasureKind.GRAMS -> "Quantidade em gramas"
                        MeasureKind.ML -> "Quantidade em ml"
                        else -> "Quantas? (${measure.name})"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    suffix = when (measure.kind) {
                        MeasureKind.GRAMS -> "g"
                        MeasureKind.ML -> "ml"
                        else -> null
                    },
                    help = if (measure.kind == MeasureKind.GRAMS || measure.kind == MeasureKind.ML) "Ex.: 120" else "Aceita meio: 0,5 · 1,5 · 2,5",
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Portions.quickAmounts(measure).forEach { (label, value) ->
                        FilterChip(
                            selected = amount == value,
                            onClick = { amountText = Portions.inputText(value) },
                            label = { Text(label) },
                        )
                    }
                }
                HorizontalDivider()
                if (amount != null && grams != null && grams > 0) {
                    val line = when (measure.kind) {
                        MeasureKind.GRAMS -> "${grams.fmt()} g"
                        MeasureKind.ML -> "${amount.fmt()} ml ≈ ${grams.fmt()} g"
                        MeasureKind.VOLUME -> "${amount.fmt(2)} ${measure.name} = ${(amount * (measure.mlPerUnit ?: 0.0)).fmt()} ml ≈ ${grams.fmt()} g"
                        else -> "${amount.fmt(2)} ${measure.name} ≈ ${grams.fmt()} g"
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (if (measure.kind == MeasureKind.GRAMS) "" else "≈ ") + "${n.kcal.fmt()} kcal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Carbo ${n.carb.fmt(1)} g · Proteína ${n.protein.fmt(1)} g · Gordura ${n.fat.fmt(1)} g · Fibra ${n.fiber.fmt(1)} g",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Digite a quantidade.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showMealType) {
                    Text("Refeição", style = MaterialTheme.typography.labelLarge)
                    MealTypePicker(type, { type = it })
                }
                if (onEditFood != null || onDeleteFood != null) {
                    HorizontalDivider()
                    Row {
                        onEditFood?.let {
                            TextButton(onClick = it) {
                                Icon(Icons.Filled.Edit, null)
                                Text(" Editar")
                            }
                        }
                        onDeleteFood?.let {
                            TextButton(onClick = it) {
                                Icon(Icons.Filled.Delete, null)
                                Text(" Excluir")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready && grams != null && grams > 0,
                onClick = { if (grams != null && amount != null) onConfirm(measure.choice(amount), type) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (addingMeasure) {
        NewMeasureDialog(
            food = food,
            onDismiss = { addingMeasure = false },
            onSave = { name, g ->
                addingMeasure = false
                scope.launch {
                    val id = foods.addPortion(food.id, name, g)
                    measureKey = "p$id"
                    amountText = "1"
                }
            },
        )
    }
    deletingMeasure?.let { m ->
        AlertDialog(
            onDismissRequest = { deletingMeasure = null },
            title = { Text("Apagar a medida?") },
            text = { Text("\"${m.name}\" sai da lista deste alimento. Os registros antigos não mudam.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingMeasure = null
                    m.portion?.let { p -> scope.launch { foods.deletePortion(p) } }
                    measureKey = Portions.GRAMS
                    amountText = "100"
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { deletingMeasure = null }) { Text("Cancelar") } },
        )
    }
}

/** Criar uma medida própria para o alimento (ex.: "minha caneca" = 350 ml). */
@Composable
private fun NewMeasureDialog(food: FoodEntity, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    val density = food.densityGPerMl
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var inMl by remember { mutableStateOf(density != null) }
    val v = value.toDecimal()
    val grams = v?.let { if (inMl && density != null) it * density else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Minha medida") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da medida") },
                    placeholder = { Text("minha caneca") },
                    supportingText = { Text("Ex.: minha caneca, meu pote, 1 pegador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (density != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = inMl, onClick = { inMl = true }, label = { Text("em ml") })
                        FilterChip(selected = !inMl, onClick = { inMl = false }, label = { Text("em gramas") })
                    }
                }
                NumberField(
                    value = value,
                    onValueChange = { value = it },
                    label = if (inMl && density != null) "Quanto cabe" else "Quanto pesa",
                    modifier = Modifier.fillMaxWidth(),
                    suffix = if (inMl && density != null) "ml" else "g",
                    help = "Pese uma vez na balança de cozinha ou veja na embalagem.",
                )
                if (inMl && density != null && grams != null) {
                    Text("≈ ${grams.fmt()} g", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && grams != null && grams > 0,
                onClick = { if (grams != null) onSave(name.trim(), Portions.round1(grams)) },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
