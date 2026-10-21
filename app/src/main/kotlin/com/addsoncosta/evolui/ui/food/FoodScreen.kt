package com.addsoncosta.evolui.ui.food

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.data.nutritionFor
import com.addsoncosta.evolui.data.Portions
import com.addsoncosta.evolui.data.SavedMeal
import com.addsoncosta.evolui.data.SavedMealItemEntity
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.LocalDate

@Composable
fun FoodScreen() {
    val context = LocalContext.current
    val container = appContainer()
    val vm: FoodViewModel = viewModel(factory = viewModelFactory { initializer { FoodViewModel(container) } })
    var editor by remember { mutableStateOf<SavedMealDraft?>(null) }
    var picking by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(false) }
    val selectedDate by vm.date.collectAsStateWithLifecycle()
    val draft = editor

    when {
        photo -> PhotoMealScreen(
            vm = vm,
            onClose = {
                photo = false
                vm.query.value = ""
            },
        )
        draft != null && picking -> FoodPicker(
            vm = vm,
            onBack = {
                picking = false
                vm.query.value = ""
            },
            onAdd = { item ->
                editor = editor?.let { it.copy(items = it.items + item) }
                picking = false
                vm.query.value = ""
            },
        )
        draft != null -> SavedMealEditor(
            draft = draft,
            foods = vm.foods,
            onChange = { editor = it },
            onAddFood = {
                vm.query.value = ""
                picking = true
            },
            onSave = {
                vm.saveMeal(draft)
                editor = null
                Toast.makeText(context, "Refeição \"${draft.name.trim()}\" salva", Toast.LENGTH_SHORT).show()
            },
            onSaveAndLog = {
                vm.saveAndLogMeal(draft)
                editor = null
                Toast.makeText(context, loggedMessage(draft.type, draft.items.sumOf { it.kcal }), Toast.LENGTH_LONG).show()
            },
            dayLabel = dayLabel(selectedDate),
            onDelete = {
                vm.deleteSavedMeal(draft.id)
                editor = null
            },
            onClose = { editor = null },
        )
        else -> FoodHome(vm = vm, onOpenEditor = { editor = it }, onOpenPhoto = { photo = true })
    }
}

internal fun dayLabel(date: LocalDate) = if (date == LocalDate.now()) "hoje" else date.format(DayFormat)

/** Tela principal: busca, refeições salvas, água e lista de alimentos. */
@Composable
private fun FoodHome(vm: FoodViewModel, onOpenEditor: (SavedMealDraft) -> Unit, onOpenPhoto: () -> Unit) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val savedMeals by vm.savedMeals.collectAsStateWithLifecycle()
    val dayMeals by vm.dayMeals.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FoodEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<FoodEntity?>(null) }
    var deletingFood by remember { mutableStateOf<FoodEntity?>(null) }
    var loggingSaved by remember { mutableStateOf<SavedMeal?>(null) }
    var savingDay by remember { mutableStateOf(false) }

    fun logSaved(meal: SavedMeal, type: MealType) {
        vm.logSavedMeal(meal, type)
        Toast.makeText(context, loggedMessage(type, meal.kcal), Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Comer")
                Text("Alimentos", style = MaterialTheme.typography.headlineMedium)
                Text("Registrando em: ${dayLabel(date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null)
                Text(" Novo")
            }
        }
        SearchField(query, { vm.query.value = it })
        FoodList(
            vm = vm,
            query = query,
            onFood = { selected = it },
            modifier = Modifier.weight(1f),
            header = {
                if (query.isBlank()) {
                    item(key = "foto") { PhotoMealCard(onOpen = onOpenPhoto, modifier = Modifier.padding(bottom = 12.dp)) }
                    item(key = "sugestoes") {
                        QuickSuggestions(
                            vm = vm,
                            hint = "Refeição salva registra na hora; alimento abre a medida, já com a última que você usou.",
                            onFood = { selected = it },
                            onMeal = { meal -> logSaved(meal, meal.type) },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    item(key = "salvas") {
                        SavedMealsPanel(
                            meals = savedMeals,
                            dayLabel = dayLabel(date),
                            onLog = { loggingSaved = it },
                            onQuickLog = { meal -> logSaved(meal, meal.type) },
                            onSaveDay = { savingDay = true },
                            onNew = { onOpenEditor(SavedMealDraft()) },
                        )
                    }
                    item(key = "agua") { WaterCard(date, Modifier.padding(top = 12.dp)) }
                }
            },
        )
    }

    selected?.let { food ->
        PortionDialog(
            food = food,
            foods = vm.foods,
            onDismiss = { selected = null },
            onConfirm = { choice, type ->
                vm.log(food, choice, type)
                selected = null
                Toast.makeText(context, loggedMessage(type, food.nutritionFor(choice.grams).kcal), Toast.LENGTH_SHORT).show()
            },
            onEditFood = if (food.custom) ({ editingFood = food; selected = null }) else null,
            onDeleteFood = if (food.custom) ({ deletingFood = food; selected = null }) else null,
        )
    }
    if (creating) {
        CustomFoodDialog(initial = null, onDismiss = { creating = false }, onSave = { vm.saveCustom(it); creating = false })
    }
    editingFood?.let { food ->
        CustomFoodDialog(initial = food, onDismiss = { editingFood = null }, onSave = { vm.saveCustom(it); editingFood = null })
    }
    deletingFood?.let { food ->
        AlertDialog(
            onDismissRequest = { deletingFood = null },
            title = { Text("Excluir \"${food.name}\"?") },
            text = { Text("O alimento sai da lista. O que você já registrou nos dias continua lá.") },
            confirmButton = { TextButton(onClick = { vm.delete(food); deletingFood = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deletingFood = null }) { Text("Cancelar") } },
        )
    }
    loggingSaved?.let { meal ->
        LogSavedMealDialog(
            meal = meal,
            dayLabel = dayLabel(date),
            onDismiss = { loggingSaved = null },
            onLog = { type ->
                loggingSaved = null
                logSaved(meal, type)
            },
            onEdit = {
                loggingSaved = null
                onOpenEditor(SavedMealDraft.from(meal))
            },
        )
    }
    if (savingDay) {
        SaveDayMealDialog(
            dayLabel = dayLabel(date),
            dayMeals = dayMeals,
            onDismiss = { savingDay = false },
            onSave = { type, name ->
                vm.saveDayAsMeal(type, name)
                savingDay = false
                Toast.makeText(context, "Refeição \"$name\" salva", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** Escolher um alimento para pôr numa refeição salva. */
@Composable
private fun FoodPicker(vm: FoodViewModel, onBack: () -> Unit, onAdd: (SavedMealItemEntity) -> Unit) {
    BackHandler(onBack = onBack)
    val query by vm.query.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<FoodEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp, 16.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Column {
                Overline("Refeição salva")
                Text("Adicionar alimento", style = MaterialTheme.typography.headlineMedium)
            }
        }
        SearchField(query, { vm.query.value = it })
        FoodList(vm = vm, query = query, onFood = { picked = it }, modifier = Modifier.weight(1f))
    }
    picked?.let { food ->
        PortionDialog(
            food = food,
            foods = vm.foods,
            onDismiss = { picked = null },
            onConfirm = { choice, _ ->
                picked = null
                onAdd(FoodRepository.item(food, choice))
            },
            confirmLabel = "Adicionar",
            showMealType = false,
        )
    }
}

@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Buscar (ex.: arroz, peixe, fruta)") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Limpar") } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

/**
 * Lista de alimentos. Com a busca vazia: favoritos, usados recentemente e a lista toda em ordem alfabética
 * (com filtro por categoria). Com texto: os resultados da busca.
 */
@Composable
internal fun FoodList(
    vm: FoodViewModel,
    query: String,
    onFood: (FoodEntity) -> Unit,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
) {
    val searching = query.isNotBlank()
    val search by vm.search.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    // A lista completa só é lida quando a busca está vazia.
    val all = if (searching) emptyList() else vm.browse.collectAsStateWithLifecycle().value

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        header()
        if (searching) {
            val ready = search.query == query.trim()
            if (ready && search.items.isEmpty()) {
                item(key = "vazio") {
                    Text(
                        "Nada encontrado para \"${query.trim()}\". Tente uma palavra só (ex.: \"pão\" em vez de \"pãozinho\") " +
                            "ou toque em + Novo para cadastrar o alimento com os dados da embalagem.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(search.items, key = { "r${it.id}" }) { food -> FoodRow(food, { onFood(food) }, { vm.toggleFavorite(food) }) }
        } else {
            if (favorites.isNotEmpty()) {
                item(key = "t-fav") { SectionTitle("Favoritos") }
                items(favorites, key = { "f${it.id}" }) { food -> FoodRow(food, { onFood(food) }, { vm.toggleFavorite(food) }) }
            }
            if (recents.isNotEmpty()) {
                item(key = "t-rec") { SectionTitle("Usados recentemente") }
                items(recents, key = { "u${it.id}" }) { food -> FoodRow(food, { onFood(food) }, { vm.toggleFavorite(food) }) }
            }
            item(key = "t-all") {
                Column {
                    SectionTitle("Todos os alimentos (A a Z)")
                    if (favorites.isEmpty()) {
                        Text(
                            "Dica: toque na estrela de um alimento para ele aparecer no topo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        item(key = "todas") {
                            FilterChip(selected = category == null, onClick = { vm.category.value = null }, label = { Text("Todas") })
                        }
                        items(categories, key = { it }) { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { vm.category.value = if (category == c) null else c },
                                label = { Text(c) },
                            )
                        }
                    }
                }
            }
            items(all, key = { "a${it.id}" }) { food -> FoodRow(food, { onFood(food) }, { vm.toggleFavorite(food) }) }
        }
        item(key = "fonte") {
            Text(
                "Dados por 100 g: TACO (NEPA/Unicamp), TBCA/USP e USDA. Medidas caseiras (≈) são aproximadas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Overline(text, Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit, onFavorite: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text(food.name) },
            supportingContent = {
                Text(
                    "${food.category} · ${food.kcal.fmt()} kcal/100 g" + when {
                        food.custom -> " · próprio"
                        food.densityGPerMl != null -> " · mede em ml"
                        else -> ""
                    }
                )
            },
            trailingContent = {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (food.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (food.favorite) "Tirar dos favoritos" else "Favoritar",
                        tint = if (food.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Cadastrar ou editar um alimento próprio, com os dados da embalagem. */
@Composable
private fun CustomFoodDialog(initial: FoodEntity?, onDismiss: () -> Unit, onSave: (FoodEntity) -> Unit) {
    // Edição: mostra os valores da porção do rótulo (ou de 100 g, se não houver porção).
    val startServing = initial?.servingGrams ?: 100.0
    val k = startServing / 100
    fun start(v: Double?) = if (initial == null || v == null) "" else Portions.inputText(Portions.round1(v * k))
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var serving by remember { mutableStateOf(Portions.inputText(startServing)) }
    var servingName by remember { mutableStateOf(initial?.servingName ?: "") }
    var kcal by remember { mutableStateOf(start(initial?.kcal)) }
    var carb by remember { mutableStateOf(start(initial?.carb)) }
    var protein by remember { mutableStateOf(start(initial?.protein)) }
    var fat by remember { mutableStateOf(start(initial?.fat)) }
    var fiber by remember { mutableStateOf(start(initial?.fiber)) }
    var liquid by remember { mutableStateOf(initial?.densityGPerMl != null) }
    val servingG = serving.toDecimal()
    val valid = name.isNotBlank() && servingG != null && servingG > 0 && kcal.toDecimal() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo alimento" else "Editar alimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    supportingText = { Text("Ex.: Iogurte da marca X") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Copie da tabela nutricional da embalagem, para uma porção:", style = MaterialTheme.typography.bodySmall)
                NumberField(serving, { serving = it }, "Porção", Modifier.fillMaxWidth(), suffix = "g", help = "Ex.: 30 (o rótulo diz \"Porção de 30 g\")")
                OutlinedTextField(
                    value = servingName,
                    onValueChange = { servingName = it },
                    label = { Text("Nome da porção (opcional)") },
                    placeholder = { Text("fatia") },
                    supportingText = { Text("Ex.: 1 fatia, 1 unidade, 2 biscoitos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(kcal, { kcal = it }, "Calorias", Modifier.fillMaxWidth(), suffix = "kcal", help = "Valor energético da porção")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carb, { carb = it }, "Carbo", Modifier.weight(1f), suffix = "g")
                    NumberField(protein, { protein = it }, "Proteína", Modifier.weight(1f), suffix = "g")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(fat, { fat = it }, "Gordura", Modifier.weight(1f), suffix = "g")
                    NumberField(fiber, { fiber = it }, "Fibra", Modifier.weight(1f), suffix = "g")
                }
                Row(Modifier.fillMaxWidth().clickable { liquid = !liquid }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = liquid, onCheckedChange = { liquid = it })
                    Column {
                        Text("É bebida ou líquido")
                        Text("Permite registrar em ml, copo e xícara.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                // Os valores são guardados por 100 g, como na TACO.
                val g = servingG ?: return@TextButton
                val per100 = 100 / g
                val base = initial ?: FoodEntity(
                    category = "Meus alimentos",
                    name = "",
                    searchName = "",
                    kcal = 0.0,
                    protein = 0.0,
                    fat = 0.0,
                    carb = 0.0,
                    fiber = 0.0,
                )
                onSave(
                    base.copy(
                        name = name.trim(),
                        kcal = (kcal.toDecimal() ?: 0.0) * per100,
                        protein = (protein.toDecimal() ?: 0.0) * per100,
                        fat = (fat.toDecimal() ?: 0.0) * per100,
                        carb = (carb.toDecimal() ?: 0.0) * per100,
                        fiber = (fiber.toDecimal() ?: 0.0) * per100,
                        servingName = servingName.trim().ifBlank { null },
                        servingGrams = if (servingName.isBlank()) null else g,
                        densityGPerMl = if (liquid) (initial?.densityGPerMl ?: 1.0) else null,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
