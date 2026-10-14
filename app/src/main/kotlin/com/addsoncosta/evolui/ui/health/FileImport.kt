package com.addsoncosta.evolui.ui.health

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.health.Attachments
import com.addsoncosta.evolui.health.ExamParser
import com.addsoncosta.evolui.health.FoundValue
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.LabItem
import com.addsoncosta.evolui.health.Ocr
import com.addsoncosta.evolui.health.ParsedExam
import com.addsoncosta.evolui.health.PendingFile
import com.addsoncosta.evolui.ui.components.DateFormat
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Escolhe um PDF ou imagem, guarda no app e tenta ler o texto. */
class FileImporter(private val start: () -> Unit, private val busyState: State<Boolean>) {
    val busy: Boolean get() = busyState.value
    fun launch() = start()
}

/** Itens do catálogo que fazem sentido para a leitura de cada tipo. */
fun catalogFor(type: HealthType): List<LabItem> = when (type) {
    HealthType.EXAME -> LabCatalog.labItems
    HealthType.BIOIMPEDANCIA -> LabCatalog.bioimpedance
    else -> emptyList()
}

@Composable
fun rememberFileImporter(type: HealthType, onResult: (PendingFile, ParsedExam?) -> Unit): FileImporter {
    val context = LocalContext.current
    val repo = rememberHealthRepository()
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy.value = true
            try {
                val pending = Attachments.copyIn(context, uri)
                if (pending == null) {
                    Toast.makeText(context, "Não foi possível abrir este arquivo.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val text = Ocr.read(context, Attachments.file(context, pending.fileName), pending.mimeType)
                if (text == null) {
                    Toast.makeText(
                        context,
                        "Arquivo guardado. Não deu para ler o texto dele neste celular; preencha os dados à mão, sem problema.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                val parsed = text?.let { ExamParser.parse(it, catalogFor(type), repo.places(type)) }
                callback(pending.copy(ocrText = text), parsed)
            } finally {
                busy.value = false
            }
        }
    }
    return remember(launcher) { FileImporter({ launcher.launch(Attachments.pickerTypes) }, busy) }
}

/** Valor encontrado no arquivo virando um valor de exame. */
fun FoundValue.toResult(): LabResultEntity =
    LabResultEntity(recordId = 0, epochDay = 0, analyte = item.name, value = value, unit = item.unit.ifBlank { null })

/**
 * "Encontramos estes valores no arquivo": o usuário confere e escolhe o que usar.
 * [showLab] mostra a sugestão de laboratório (só para exames).
 */
@Composable
fun SuggestionsDialog(
    parsed: ParsedExam,
    showLab: Boolean,
    onApply: (date: LocalDate?, lab: String?, values: List<FoundValue>) -> Unit,
    onDismiss: () -> Unit,
) {
    var useDate by remember { mutableStateOf(parsed.date != null) }
    var useLab by remember { mutableStateOf(showLab && parsed.lab != null) }
    val chosen = remember { mutableStateMapOf<String, Boolean>().apply { parsed.values.forEach { put(it.item.name, true) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encontramos estes dados no arquivo") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Confira com o laudo antes de usar. A leitura automática pode errar números ou unidades.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                parsed.date?.let { d -> CheckRow(useDate, { useDate = it }, "Data: ${d.format(DateFormat)}", null) }
                if (showLab) parsed.lab?.let { l -> CheckRow(useLab, { useLab = it }, "Laboratório: $l", null) }
                if (parsed.values.isNotEmpty()) {
                    Text("Valores (${parsed.values.size})", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    parsed.values.forEach { v ->
                        CheckRow(
                            chosen[v.item.name] == true,
                            { chosen[v.item.name] = it },
                            "${v.item.name}: ${LabCatalog.num(v.value)} ${v.item.unit}".trim(),
                            v.line,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    if (useDate) parsed.date else null,
                    if (useLab) parsed.lab else null,
                    parsed.values.filter { chosen[it.item.name] == true },
                )
                onDismiss()
            }) { Text("Usar selecionados") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, title: String, detail: String?) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(title)
            if (detail != null) Text("“$detail”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
