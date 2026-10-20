package com.addsoncosta.evolui.ui.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.addsoncosta.evolui.data.PhotoRepository
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.share.loadBitmap
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data, peso e texto de um lado do antes e depois. Nulo = automático
 * (antes: data de início e peso inicial; depois: hoje e o peso atual).
 */
internal data class SlotLabel(val epochDay: Long? = null, val weightKg: Double? = null, val caption: String? = null) {
    val isAuto: Boolean get() = epochDay == null && weightKg == null && caption == null
}

/** O que aparece de fato num lado: data, peso e o texto. */
internal data class ResolvedLabel(val date: LocalDate, val weightKg: Double?, val text: String)

internal fun SlotLabel.resolve(autoDate: LocalDate, autoWeight: Double?): ResolvedLabel {
    val date = epochDay?.let { LocalDate.ofEpochDay(it) } ?: autoDate
    val kg = if (epochDay == null && weightKg == null) autoWeight else weightKg
    return ResolvedLabel(date, kg, caption?.takeIf { it.isNotBlank() } ?: defaultCaption(date, kg))
}

/** Guarda o que o usuário mudou em cada lado (só no celular, como a escolha das fotos). */
internal class SlotLabelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("antes_depois_textos", Context.MODE_PRIVATE)

    fun read(key: String): SlotLabel = SlotLabel(
        epochDay = if (prefs.contains("${key}_dia")) prefs.getLong("${key}_dia", 0L) else null,
        weightKg = if (prefs.contains("${key}_peso")) prefs.getFloat("${key}_peso", 0f).toDouble() else null,
        caption = prefs.getString("${key}_texto", null),
    )

    fun save(key: String, label: SlotLabel) {
        prefs.edit().apply {
            if (label.epochDay != null) putLong("${key}_dia", label.epochDay) else remove("${key}_dia")
            if (label.weightKg != null) putFloat("${key}_peso", label.weightKg.toFloat()) else remove("${key}_peso")
            if (label.caption != null) putString("${key}_texto", label.caption) else remove("${key}_texto")
        }.apply()
    }
}

/** "120 dias de diferença · −8,4 kg". */
internal fun differenceText(before: ResolvedLabel, after: ResolvedLabel): String {
    val days = abs(after.date.toEpochDay() - before.date.toEpochDay())
    val diff = if (before.weightKg != null && after.weightKg != null) after.weightKg - before.weightKg else null
    val kg = diff?.let { (if (it > 0.04) "+" else if (it < -0.04) "−" else "") + abs(it).fmt(1) }
    return "$days ${if (days == 1L) "dia" else "dias"} de diferença" + (kg?.let { " · $it kg" } ?: "")
}

/**
 * Gira o arquivo da foto de progresso em [degrees] (90, 180, 270) e salva o enquadramento novo.
 * Grava num arquivo novo (a miniatura em memória não fica com a versão antiga) e apaga o antigo.
 */
internal suspend fun rotatePhoto(context: Context, photoRepo: PhotoRepository, photo: PhotoEntity, degrees: Int, scale: Double, offsetX: Double, offsetY: Double) {
    val turn = ((degrees % 360) + 360) % 360
    withContext(Dispatchers.IO) {
        val old = photoRepo.file(photo)
        val src = loadBitmap(context, old, 3000) ?: error("Não foi possível abrir a foto")
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
        val name = "foto_${System.currentTimeMillis()}.jpg"
        val file = File(old.parentFile, name)
        file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        photoRepo.update(photo.copy(fileName = name, scale = scale, offsetX = offsetX, offsetY = offsetY))
        old.delete()
    }
}

/** Editar a data, o peso e o texto de um lado do antes e depois. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlotLabelDialog(
    title: String,
    label: SlotLabel,
    autoDate: LocalDate,
    autoWeight: Double?,
    autoName: String,
    photo: PhotoEntity?,
    onDismiss: () -> Unit,
    onSave: (SlotLabel) -> Unit,
) {
    val start = label.resolve(autoDate, autoWeight)
    var date by remember { mutableStateOf(start.date) }
    var weight by remember { mutableStateOf(start.weightKg?.fmt(1) ?: "") }
    var caption by remember { mutableStateOf(label.caption ?: "") }
    var pickingDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            date = autoDate
                            weight = autoWeight?.fmt(1) ?: ""
                        },
                        label = { Text(autoName) },
                    )
                    if (photo != null) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                date = LocalDate.ofEpochDay(photo.epochDay)
                                weight = photo.weightKg?.fmt(1) ?: weight
                            },
                            label = { Text("Data da foto") },
                        )
                    }
                }
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, null)
                    Text("  Data: ${date.format(DateFormat)}")
                }
                NumberField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = "Peso",
                    suffix = "kg",
                    help = "Ex.: 98,8. Deixe vazio para não mostrar.",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Texto na foto (opcional)") },
                    supportingText = { Text("Vazio mostra: ${defaultCaption(date, weight.toDecimal())}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = weight.toDecimal()?.takeIf { it in 20.0..400.0 }
                val text = caption.trim().takeIf { it.isNotEmpty() }
                // Igual ao automático: continua automático (o "depois" segue o dia de hoje).
                val same = date == autoDate && text == null && weight.trim() == (autoWeight?.fmt(1) ?: "")
                onSave(if (same) SlotLabel() else SlotLabel(date.toEpochDay(), kg, text))
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (!label.isAuto) TextButton(onClick = { onSave(SlotLabel()) }) { Text("Automático") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state)
        }
    }
}
