package com.addsoncosta.evolui.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.Portions
import com.addsoncosta.evolui.data.WATER_ML_PER_KG
import com.addsoncosta.evolui.data.waterGoalMl
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.Ring
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.coroutines.launch

/** Meta usada quando ainda não há peso nem meta escolhida. */
private const val FALLBACK_GOAL_ML = 2000.0
private const val GLASS_ML = 200.0
private const val BOTTLE_ML = 500.0

private enum class WaterDialog { ADD, ADJUST, GOAL }

/** Água do dia, para a tela Hoje (e Comer): progresso, botões rápidos, desfazer e meta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterCard(date: LocalDate, modifier: Modifier = Modifier) {
    val container = appContainer()
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val log by remember(date) { repo.dayLog(date) }.collectAsStateWithLifecycle(initialValue = null)
    val latestWeight by remember(date) { repo.latestWeight(date) }.collectAsStateWithLifecycle(initialValue = null)
    val profile by remember { repo.profile }.collectAsStateWithLifecycle(initialValue = null)
    val chosenGoal by container.settings.waterGoalMl.collectAsStateWithLifecycle()

    val weight = latestWeight ?: profile?.startWeightKg
    val goal = waterGoalMl(weight ?: 0.0, chosenGoal).takeIf { it > 0 } ?: FALLBACK_GOAL_ML
    val drunk = log?.waterMl ?: 0.0
    val left = (goal - drunk).coerceAtLeast(0.0)
    // O que foi somado neste dia nesta tela, para o botão Desfazer.
    val added = remember(date) { mutableStateListOf<Double>() }
    var dialog by remember { mutableStateOf<WaterDialog?>(null) }

    fun setTotal(change: (Double) -> Double) {
        scope.launch {
            repo.updateDay(date) { day ->
                val total = change(day.waterMl ?: 0.0).coerceAtLeast(0.0)
                day.copy(waterMl = total.takeIf { it > 0 })
            }
        }
    }
    fun add(ml: Double) {
        added.add(ml)
        setTotal { it + ml }
    }

    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(progress = (drunk / goal).toFloat(), color = Evolui.Cyan, size = 72.dp, stroke = 8.dp) {
                Icon(Icons.Filled.WaterDrop, null, tint = Evolui.Cyan)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("Água", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${drunk.fmt()} / ${goal.fmt()} ml",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { dialog = WaterDialog.ADJUST },
                )
                Text(
                    if (left > 0) "Faltam ${left.fmt()} ml (≈ ${ceil(left / GLASS_ML).toInt()} copos de 200 ml)" else "Meta do dia alcançada!",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (left > 0) MaterialTheme.colorScheme.onSurfaceVariant else Evolui.Cyan,
                )
            }
            IconButton(onClick = { dialog = WaterDialog.GOAL }) { Icon(Icons.Filled.Edit, "Mudar a meta de água") }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { add(GLASS_ML) }) { Text("+ Copo 200 ml") }
            FilledTonalButton(onClick = { add(BOTTLE_ML) }) { Text("+ Garrafa 500 ml") }
            OutlinedButton(onClick = { dialog = WaterDialog.ADD }) { Text("Outro valor") }
            OutlinedButton(
                enabled = added.isNotEmpty(),
                onClick = {
                    val last = added.removeLastOrNull()
                    if (last != null) setTotal { it - last }
                },
            ) { Text("Desfazer") }
            TextButton(onClick = { dialog = WaterDialog.ADJUST }) { Text("Ajustar total") }
        }
        Text(
            if (chosenGoal != null) "Meta escolhida por você. Toque no lápis para mudar."
            else if (weight != null) "Meta: ≈ 35 ml por kg (${weight.fmt(1)} kg). Ajuste se seu médico indicar outra quantidade."
            else "Meta padrão de 2 litros. Registre seu peso para calcular ≈ 35 ml por kg.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    when (dialog) {
        WaterDialog.ADD -> AmountDialog(
            title = "Quanto você bebeu?",
            initial = "",
            help = "Ex.: 300 para um copo grande, 350 para uma lata",
            quick = listOf(100.0, 250.0, 300.0, 350.0, 1000.0),
            confirm = "Somar",
            onDismiss = { dialog = null },
            onConfirm = { ml ->
                if (ml > 0) add(ml)
                dialog = null
            },
        )
        WaterDialog.ADJUST -> AmountDialog(
            title = "Total de água do dia",
            initial = Portions.inputText(drunk),
            help = "Corrige o total do dia. Ex.: 1500",
            quick = emptyList(),
            confirm = "Salvar",
            onDismiss = { dialog = null },
            onConfirm = { ml ->
                added.clear()
                setTotal { ml }
                dialog = null
            },
        )
        WaterDialog.GOAL -> GoalDialog(
            current = chosenGoal,
            weight = weight,
            onDismiss = { dialog = null },
            onSave = { ml ->
                container.settings.setWaterGoalMl(ml)
                dialog = null
            },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountDialog(
    title: String,
    initial: String,
    help: String,
    quick: List<Double>,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.toDecimal()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(text, { text = it }, "Água", Modifier.fillMaxWidth(), suffix = "ml", decimal = false, help = help)
                if (quick.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.forEach { q ->
                            FilterChip(selected = value == q, onClick = { text = Portions.inputText(q) }, label = { Text("${q.fmt()} ml") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value >= 0, onClick = { if (value != null) onConfirm(value) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun GoalDialog(current: Double?, weight: Double?, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    val calculated = weight?.let { waterGoalMl(it, null) }
    var text by remember { mutableStateOf(Portions.inputText(current ?: calculated ?: FALLBACK_GOAL_ML)) }
    val value = text.toDecimal()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta de água") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A referência comum é ≈ ${WATER_ML_PER_KG.fmt()} ml por kg de peso" +
                        (if (weight != null && calculated != null) " (para ${weight.fmt(1)} kg: ${calculated.fmt()} ml)." else ".") +
                        " Ajuste se seu médico indicar outra quantidade (por exemplo, em problemas de rim ou coração).",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField(text, { text = it }, "Meta por dia", Modifier.fillMaxWidth(), suffix = "ml", decimal = false, help = "Ex.: 2500")
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Voltar a calcular pelo peso") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value >= 500, onClick = { if (value != null) onSave(value) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
