package com.addsoncosta.evolui.ui.food

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.MealEntity
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.data.PortionChoice
import com.addsoncosta.evolui.data.Portions
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.data.SavedMeal
import com.addsoncosta.evolui.data.SavedMealItemEntity
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal

/** Refeição salva sendo criada ou editada (só vai para o banco ao tocar em Salvar). */
data class SavedMealDraft(
    val id: Long = 0,
    val name: String = "",
    val type: MealType = defaultMealType(),
    val items: List<SavedMealItemEntity> = emptyList(),
    val createdMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(meal: SavedMeal) = SavedMealDraft(meal.meal.id, meal.meal.name, meal.type, meal.items, meal.meal.createdMillis)
    }
}

private fun defaultMealName(type: MealType) = "Meu " + type.label.lowercase()

/** "hoje" ou "em 12/10". */
internal fun onDay(dayLabel: String) = if (dayLabel == "hoje") dayLabel else "em $dayLabel"

/** Aviso depois de registrar: "Registrado no Almoço: 520 kcal". */
internal fun loggedMessage(type: MealType, kcal: Double) = "Registrado no ${type.label}: ${kcal.fmt()} kcal"

/** Lista curta das refeições salvas, na tela Comer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SavedMealsPanel(
    meals: List<SavedMeal>,
    dayLabel: String,
    onLog: (SavedMeal) -> Unit,
    onQuickLog: (SavedMeal) -> Unit,
    onSaveDay: () -> Unit,
    onNew: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    Panel {
        Text("Refeições salvas", style = MaterialTheme.typography.titleMedium)
        if (meals.isEmpty()) {
            Text(
                "Salve o que você come sempre (ex.: \"Meu café\" = café + tapioca + ovo) e registre tudo de uma vez " +
                    "com um toque em Registrar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (meals.size > 4) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Buscar refeição salva") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val f = Repository.normalize(filter)
            val shown = if (f.isEmpty()) meals else meals.filter { Repository.normalize(it.meal.name).contains(f) }
            if (shown.isEmpty()) Text("Nenhuma refeição com esse nome.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.forEach { meal ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    Modifier.fillMaxWidth().clickable { onLog(meal) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(meal.meal.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${meal.type.label} · ${meal.kcal.fmt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            meal.items.joinToString(" + ") { it.name }.ifEmpty { "Sem itens" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(onClick = { onQuickLog(meal) }, enabled = meal.items.isNotEmpty()) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text(" Registrar")
                    }
                }
            }
            Text(
                "Registrar soma tudo ${onDay(dayLabel)}, na refeição de costume. Toque no nome para escolher outra refeição ou editar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveDay) { Text("Salvar refeição do dia") }
            OutlinedButton(onClick = onNew) {
                Icon(Icons.Filled.Add, null)
                Text(" Montar do zero")
            }
        }
    }
}

/** Confirmar o registro de uma refeição salva no dia selecionado. */
@Composable
internal fun LogSavedMealDialog(meal: SavedMeal, dayLabel: String, onDismiss: () -> Unit, onLog: (MealType) -> Unit, onEdit: () -> Unit) {
    var type by remember(meal.meal.id) { mutableStateOf(meal.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.meal.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Registrar em: $dayLabel", style = MaterialTheme.typography.bodySmall)
                meal.items.forEach { item ->
                    Text("• ${item.name} — ${amountLabel(item.grams, item.amount, item.unit)} · ${item.kcal.fmt()} kcal", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Total: ${meal.kcal.fmt()} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Refeição", style = MaterialTheme.typography.labelLarge)
                MealTypePicker(type, { type = it })
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null)
                    Text(" Editar nome ou itens")
                }
            }
        },
        confirmButton = { TextButton(enabled = meal.items.isNotEmpty(), onClick = { onLog(type) }) { Text("Registrar tudo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Salvar como refeição pronta o que já foi registrado num tipo de refeição do dia. */
@Composable
internal fun SaveDayMealDialog(dayLabel: String, dayMeals: List<MealEntity>, onDismiss: () -> Unit, onSave: (MealType, String) -> Unit) {
    val initialType = remember {
        val guess = defaultMealType()
        if (dayMeals.any { it.mealType == guess.name }) guess
        else MealType.entries.firstOrNull { t -> dayMeals.any { it.mealType == t.name } } ?: guess
    }
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf(defaultMealName(initialType)) }
    var nameEdited by remember { mutableStateOf(false) }
    val items = dayMeals.filter { it.mealType == type.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar refeição") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Escolha qual refeição de $dayLabel vai virar uma refeição pronta:", style = MaterialTheme.typography.bodySmall)
                MealTypePicker(type, {
                    type = it
                    if (!nameEdited) name = defaultMealName(it)
                })
                if (items.isEmpty()) {
                    Text(
                        "Nada registrado em ${type.label} neste dia. Registre os alimentos primeiro ou use \"Montar do zero\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    items.forEach { m ->
                        Text("• ${m.name} — ${amountLabel(m.grams, m.amount, m.unit)} · ${m.kcal.fmt()} kcal", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Total: ${items.sumOf { it.kcal }.fmt()} kcal", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameEdited = true },
                    label = { Text("Nome") },
                    supportingText = { Text("Ex.: Meu café, Almoço da semana") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = items.isNotEmpty() && name.isNotBlank(), onClick = { onSave(type, name.trim()) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Tela para criar ou editar uma refeição salva. */
@Composable
internal fun SavedMealEditor(
    draft: SavedMealDraft,
    foods: FoodRepository,
    onChange: (SavedMealDraft) -> Unit,
    onAddFood: () -> Unit,
    onSave: () -> Unit,
    onSaveAndLog: () -> Unit,
    dayLabel: String,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "topo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column {
                    Overline("Refeição salva")
                    Text(if (draft.id == 0L) "Nova refeição" else "Editar refeição", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        item(key = "nome") {
            Panel {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("Nome") },
                    supportingText = { Text("Ex.: Meu café, Lanche da tarde") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Refeição de costume", style = MaterialTheme.typography.labelLarge)
                MealTypePicker(draft.type, { onChange(draft.copy(type = it)) })
                Text(
                    "Na hora de registrar você ainda pode escolher outra refeição.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "itens") {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alimentos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${draft.items.sumOf { it.kcal }.fmt()} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (draft.items.isEmpty()) {
                    Text("Nenhum alimento ainda. Toque em \"Adicionar alimento\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                draft.items.forEachIndexed { index, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(Modifier.fillMaxWidth().clickable { editIndex = index }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(item.name)
                            Text(
                                "${amountLabel(item.grams, item.amount, item.unit)} · ${item.kcal.fmt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onChange(draft.copy(items = draft.items.filterIndexed { i, _ -> i != index })) }) {
                            Icon(Icons.Filled.Close, "Tirar ${item.name}")
                        }
                    }
                }
                if (draft.items.isNotEmpty()) {
                    Text("Toque num alimento para mudar a quantidade.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onAddFood, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(" Adicionar alimento")
                }
            }
        }
        item(key = "salvar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ok = draft.name.isNotBlank() && draft.items.isNotEmpty()
                Button(onClick = onSaveAndLog, enabled = ok, modifier = Modifier.fillMaxWidth()) {
                    Text("Salvar e registrar ${onDay(dayLabel)}")
                }
                OutlinedButton(onClick = onSave, enabled = ok, modifier = Modifier.fillMaxWidth()) { Text("Só salvar") }
                if (!ok) {
                    Text("Dê um nome e adicione pelo menos um alimento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "\"Salvar e registrar\" soma ${draft.items.sumOf { it.kcal }.fmt()} kcal no ${draft.type.label}. " +
                            "\"Só salvar\" guarda a refeição para registrar depois com um toque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (draft.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(" Apagar esta refeição salva", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    val index = editIndex
    val editing = index?.let { draft.items.getOrNull(it) }
    if (index != null && editing != null) {
        EditItemDialog(
            item = editing,
            foods = foods,
            onDismiss = { editIndex = null },
            onSave = { updated ->
                onChange(draft.copy(items = draft.items.mapIndexed { i, old -> if (i == index) updated else old }))
                editIndex = null
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${draft.name}\"?") },
            text = { Text("A refeição salva some da lista. O que já foi registrado nos dias continua lá.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Mudar a quantidade de um item: com as medidas do alimento, ou só em gramas se o alimento foi apagado. */
@Composable
private fun EditItemDialog(item: SavedMealItemEntity, foods: FoodRepository, onDismiss: () -> Unit, onSave: (SavedMealItemEntity) -> Unit) {
    val state by produceState<FoodLookup>(FoodLookup.Loading, item.foodId) {
        val id = item.foodId
        value = if (id == null) FoodLookup.Missing else foods.food(id)?.let { FoodLookup.Found(it) } ?: FoodLookup.Missing
    }
    when (val s = state) {
        FoodLookup.Loading -> Unit
        is FoodLookup.Found -> PortionDialog(
            food = s.food,
            foods = foods,
            onDismiss = onDismiss,
            onConfirm = { choice, _ -> onSave(FoodRepository.item(s.food, choice)) },
            confirmLabel = "Salvar",
            showMealType = false,
            initial = PortionChoice(item.grams, item.amount, item.unit),
        )
        FoodLookup.Missing -> {
            var grams by remember { mutableStateOf(Portions.inputText(item.grams)) }
            val g = grams.toDecimal()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(item.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Este alimento não está mais na lista; dá para mudar só o peso.", style = MaterialTheme.typography.bodySmall)
                        NumberField(grams, { grams = it }, "Quantidade em gramas", Modifier.fillMaxWidth(), suffix = "g")
                    }
                },
                confirmButton = {
                    TextButton(enabled = g != null && g > 0, onClick = { if (g != null) onSave(FoodRepository.scaled(item, g)) }) { Text("Salvar") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            )
        }
    }
}

private sealed interface FoodLookup {
    data object Loading : FoodLookup
    data object Missing : FoodLookup
    data class Found(val food: FoodEntity) : FoodLookup
}
