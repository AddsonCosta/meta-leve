package app.metaleve.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.metaleve.data.FoodEntity
import app.metaleve.data.MealType
import app.metaleve.ui.appContainer
import app.metaleve.ui.components.DayFormat
import app.metaleve.ui.components.NumberField
import app.metaleve.ui.components.fmt
import app.metaleve.ui.components.toDecimal
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun FoodScreen() {
    val container = appContainer()
    val vm: FoodViewModel = viewModel(factory = viewModelFactory { initializer { FoodViewModel(container) } })
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FoodEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alimentos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Registrando em: " + if (date == LocalDate.now()) "hoje" else date.format(DayFormat),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null)
                Text("Novo")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Buscar (ex.: arroz, banana, frango)") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, "Limpar") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(results, key = { it.id }) { food ->
                ListItem(
                    headlineContent = { Text(food.name) },
                    supportingContent = {
                        Text("${food.category} · ${food.kcal.fmt()} kcal/100 g" + if (food.custom) " · próprio" else "")
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.toggleFavorite(food) }) {
                            Icon(if (food.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito")
                        }
                    },
                    modifier = Modifier.clickable { selected = food },
                )
                HorizontalDivider()
            }
            item {
                Text(
                    "Dados da Tabela Brasileira de Composição de Alimentos (TACO, NEPA/Unicamp), valores por 100 g.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    selected?.let { food ->
        LogFoodDialog(
            food = food,
            onDismiss = { selected = null },
            onLog = { grams, type ->
                vm.log(food, grams, type)
                selected = null
            },
            onDelete = if (food.custom) ({ vm.delete(food); selected = null }) else null,
        )
    }
    if (creating) {
        CustomFoodDialog(onDismiss = { creating = false }, onSave = { vm.saveCustom(it); creating = false })
    }
}

private fun defaultMealType(): MealType {
    val hour = LocalTime.now().hour
    return when {
        hour < 10 -> MealType.BREAKFAST
        hour < 15 -> MealType.LUNCH
        hour < 18 -> MealType.SNACK
        else -> MealType.DINNER
    }
}

@Composable
private fun LogFoodDialog(food: FoodEntity, onDismiss: () -> Unit, onLog: (Double, MealType) -> Unit, onDelete: (() -> Unit)?) {
    var grams by remember { mutableStateOf((food.servingGrams ?: 100.0).fmt()) }
    var type by remember { mutableStateOf(defaultMealType()) }
    val g = grams.toDecimal()
    val f = (g ?: 0.0) / 100
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(grams, { grams = it }, "Quantidade", Modifier.fillMaxWidth(), suffix = "g")
                food.servingName?.let { name ->
                    TextButton(onClick = { grams = food.servingGrams!!.fmt() }) { Text("1 $name (${food.servingGrams!!.fmt()} g)") }
                }
                Text("${(food.kcal * f).fmt()} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Carbo ${(food.carb * f).fmt(1)} g · Proteína ${(food.protein * f).fmt(1)} g · Gordura ${(food.fat * f).fmt(1)} g · Fibra ${(food.fiber * f).fmt(1)} g",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label.substringBefore(" ")) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = g != null && g > 0, onClick = { onLog(g!!, type) }) { Text("Registrar") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Icon(Icons.Filled.Delete, null); Text("Excluir") } }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun CustomFoodDialog(onDismiss: () -> Unit, onSave: (FoodEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("100") }
    var servingName by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    val servingG = serving.toDecimal()
    val valid = name.isNotBlank() && servingG != null && servingG > 0 && kcal.toDecimal() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo alimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                Text("Informe os valores da tabela nutricional para uma porção:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(serving, { serving = it }, "Porção", Modifier.weight(1f), suffix = "g")
                    OutlinedTextField(servingName, { servingName = it }, label = { Text("Nome da porção") }, placeholder = { Text("fatia") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                NumberField(kcal, { kcal = it }, "Calorias", Modifier.fillMaxWidth(), suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(carb, { carb = it }, "Carbo", Modifier.weight(1f), suffix = "g")
                    NumberField(protein, { protein = it }, "Proteína", Modifier.weight(1f), suffix = "g")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(fat, { fat = it }, "Gordura", Modifier.weight(1f), suffix = "g")
                    NumberField(fiber, { fiber = it }, "Fibra", Modifier.weight(1f), suffix = "g")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                // Os valores são guardados por 100 g, como na TACO.
                val per100 = 100 / servingG!!
                onSave(
                    FoodEntity(
                        category = "Meus alimentos",
                        name = name.trim(),
                        searchName = "",
                        kcal = kcal.toDecimal()!! * per100,
                        protein = (protein.toDecimal() ?: 0.0) * per100,
                        fat = (fat.toDecimal() ?: 0.0) * per100,
                        carb = (carb.toDecimal() ?: 0.0) * per100,
                        fiber = (fiber.toDecimal() ?: 0.0) * per100,
                        servingName = servingName.trim().ifBlank { null },
                        servingGrams = if (servingName.isBlank()) null else servingG,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
