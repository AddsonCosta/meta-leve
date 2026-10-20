package com.addsoncosta.evolui.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.SavedMeal
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt

/**
 * Sugestões de um toque: o que a pessoa mais registra nesta refeição, as refeições salvas (as desta refeição
 * primeiro) e os itens recentes. Não mostra nada enquanto não houver histórico.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickSuggestions(
    vm: FoodViewModel,
    onFood: (FoodEntity) -> Unit,
    onMeal: (SavedMeal) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    // A refeição segue o relógio a cada vez que a tela abre.
    LaunchedEffect(Unit) { vm.quickType.value = defaultMealType() }
    val type by vm.quickType.collectAsStateWithLifecycle()
    val frequent by vm.frequentFoods.collectAsStateWithLifecycle()
    val saved by vm.savedMeals.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()

    val top = frequent.take(6)
    val topIds = top.map { it.id }.toHashSet()
    val recent = recents.filter { it.id !in topIds }.take(6)
    val meals = saved.filter { it.items.isNotEmpty() }
        .sortedByDescending { it.type == type }
        .take(4)
    if (top.isEmpty() && recent.isEmpty() && meals.isEmpty()) return

    Panel(modifier) {
        Overline("Sugestões para o ${type.label.lowercase()}")
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (meals.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { m ->
                    Chip("${m.meal.name} · ${m.kcal.fmt()} kcal", Icons.Filled.Restaurant) { onMeal(m) }
                }
            }
        }
        if (top.isNotEmpty()) {
            Text("Você mais registra no ${type.label.lowercase()}", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { f -> Chip(f.name, Icons.Filled.Star) { onFood(f) } }
            }
        }
        if (recent.isNotEmpty()) {
            Text("Recentes", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { f -> Chip(f.name, Icons.Filled.History) { onFood(f) } }
            }
        }
    }
}

@Composable
private fun Chip(text: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}
