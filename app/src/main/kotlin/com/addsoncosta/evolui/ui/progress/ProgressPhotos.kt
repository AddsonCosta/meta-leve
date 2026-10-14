package com.addsoncosta.evolui.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.PhotoEntity
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.PtBr
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CaptionDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", PtBr)

/** Legenda automática: "14 out 2026 · 98,8 kg". */
internal fun defaultCaption(date: LocalDate, weightKg: Double?): String =
    date.format(CaptionDate).replace(".", "") + (weightKg?.let { " · ${it.fmt(1)} kg" } ?: "")

/** Legenda mostrada na foto: a editada pelo usuário ou a automática. */
internal fun captionOf(photo: PhotoEntity): String =
    photo.caption?.takeIf { it.isNotBlank() } ?: defaultCaption(LocalDate.ofEpochDay(photo.epochDay), photo.weightKg)

/** Editar data, peso e o texto que aparece na foto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPhotoDialog(photo: PhotoEntity, onDismiss: () -> Unit, onSave: (PhotoEntity) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(photo.epochDay)) }
    var weight by remember { mutableStateOf(photo.weightKg?.fmt(1) ?: "") }
    var caption by remember { mutableStateOf(captionOf(photo)) }
    var pickingDate by remember { mutableStateOf(false) }

    // Se o texto ainda é o automático, ele acompanha a data e o peso.
    fun refresh(oldDate: LocalDate, oldWeight: String) {
        if (caption.trim() == defaultCaption(oldDate, oldWeight.toDecimal())) caption = defaultCaption(date, weight.toDecimal())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar foto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Text("  Data: ${date.format(DateFormat)}")
                }
                Text("Toque para mudar o dia em que a foto foi tirada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(
                    value = weight,
                    onValueChange = { new ->
                        val old = weight
                        weight = new
                        refresh(date, old)
                    },
                    label = "Peso no dia",
                    suffix = "kg",
                    help = "Ex.: 98,8. Deixe vazio se não souber.",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Texto na foto") },
                    supportingText = { Text("Ex.: ${defaultCaption(date, weight.toDecimal())}. Deixe vazio para usar data e peso.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = weight.toDecimal()?.takeIf { it in 20.0..400.0 }
                val text = caption.trim()
                val custom = text.takeIf { it.isNotEmpty() && it != defaultCaption(date, kg) }
                onSave(photo.copy(epochDay = date.toEpochDay(), weightKg = kg, caption = custom))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val old = date
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        refresh(old, weight)
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state)
        }
    }
}

