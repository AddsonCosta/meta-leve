package com.addsoncosta.evolui.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
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
import com.addsoncosta.evolui.data.ExerciseEntity
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.LocalDate

@Composable
fun ExerciseScreen() {
    val container = appContainer()
    val vm: ExerciseViewModel = viewModel(factory = viewModelFactory { initializer { ExerciseViewModel(container) } })
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val weight by vm.weightKg.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Exercícios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Registrando em: " + (if (date == LocalDate.now()) "hoje" else date.format(DayFormat)) + " · peso ${weight.fmt(1)} kg",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null)
                Text("Novo")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(exercises, key = { it.id }) { ex ->
                ListItem(
                    overlineContent = { Text(ex.category) },
                    headlineContent = { Text(ex.name) },
                    supportingContent = { Text("≈ ${vm.kcal(ex, 30.0).fmt()} kcal em 30 min") },
                    trailingContent = {
                        IconButton(onClick = { vm.toggleFavorite(ex) }) {
                            Icon(if (ex.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito")
                        }
                    },
                    modifier = Modifier.clickable { selected = ex },
                )
                HorizontalDivider()
            }
            item {
                Text(
                    "Gasto estimado pelo valor MET de cada atividade (Compendium of Physical Activities).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    selected?.let { ex ->
        var minutes by remember(ex.id) { mutableStateOf("30") }
        val m = minutes.toDecimal()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(ex.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minutes, { minutes = it }, "Duração", Modifier.fillMaxWidth(), suffix = "min")
                    Text("${vm.kcal(ex, m ?: 0.0).fmt()} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(enabled = m != null && m > 0, onClick = { vm.log(ex, m!!); selected = null }) { Text("Registrar") }
            },
            dismissButton = {
                Row {
                    if (ex.custom) TextButton(onClick = { vm.delete(ex); selected = null }) { Icon(Icons.Filled.Delete, null); Text("Excluir") }
                    TextButton(onClick = { selected = null }) { Text("Cancelar") }
                }
            },
        )
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var met by remember { mutableStateOf("") }
        val metValue = met.toDecimal()
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Novo exercício") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                    NumberField(met, { met = it }, "Intensidade (MET)", Modifier.fillMaxWidth())
                    Text(
                        "MET indica quantas vezes a atividade gasta mais energia que o repouso. Ex.: caminhada 3,5; corrida leve 8; musculação 3,5 a 6.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && metValue != null && metValue in 1.0..25.0, onClick = {
                    vm.saveCustom(name.trim(), metValue!!)
                    creating = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}
