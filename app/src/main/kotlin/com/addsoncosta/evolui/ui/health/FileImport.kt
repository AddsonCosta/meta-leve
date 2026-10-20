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
import com.addsoncosta.evolui.health.AutoFill
import com.addsoncosta.evolui.health.DocumentAutoFill
import com.addsoncosta.evolui.health.HealthFields
import com.addsoncosta.evolui.health.BioParser
import com.addsoncosta.evolui.health.ExamParser
import com.addsoncosta.evolui.health.EyeParser
import com.addsoncosta.evolui.health.EyeExam
import com.addsoncosta.evolui.health.EyeValues
import com.addsoncosta.evolui.health.FoundValue
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.LabItem
import com.addsoncosta.evolui.health.Ocr
import com.addsoncosta.evolui.health.ParsedExam
import com.addsoncosta.evolui.health.PendingFile
import com.addsoncosta.evolui.health.ReportSummary
import com.addsoncosta.evolui.health.ReportSummaryParser
import com.addsoncosta.evolui.ui.components.DateFormat
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Tudo o que a leitura do arquivo achou, conforme o tipo de registro. */
data class ImportedData(
    /** Data, laboratório e valores (exames e bioimpedância; nos outros tipos, só a data). */
    val exam: ParsedExam? = null,
    /** Receita de óculos (Oftalmologia). */
    val eye: EyeExam? = null,
    /** Resumo do laudo de imagem ou procedimento (Exames). */
    val report: ReportSummary? = null,
    /** Campos essenciais do formulário achados no texto (e a receita, se houver). */
    val fill: AutoFill? = null,
)

/** Separa, do texto lido do arquivo, o que interessa para cada tipo de registro. */
fun interpret(type: HealthType, text: String, usedPlaces: List<String>, reportMode: Boolean): ImportedData {
    val general = DocumentAutoFill.read(type, text, usedPlaces)
    return when (type) {
        HealthType.OFTALMOLOGIA -> {
            val eye = EyeParser.parse(text)
            val base = ExamParser.parse(text, emptyList(), usedPlaces)
            val date = eye.date ?: base.date
            ImportedData(exam = ParsedExam(date, null, emptyList()), eye = eye, fill = AutoFill(date = date).orElse(general))
        }
        HealthType.BIOIMPEDANCIA -> {
            // Só o essencial (peso, gordura, músculo, água, visceral, TMB, idade metabólica, IMC).
            val values = BioParser.parse(text).mapNotNull { v -> LabCatalog.find(v.name)?.let { FoundValue(it, v.value, v.line) } }
            val base = ExamParser.parse(text, emptyList(), usedPlaces)
            ImportedData(exam = ParsedExam(base.date, null, values), fill = AutoFill(date = base.date).orElse(general))
        }
        HealthType.EXAME -> {
            val parsed = ExamParser.parse(text, LabCatalog.labItems, usedPlaces)
            val complex = reportMode || (parsed.values.size < 3 && ReportSummaryParser.isComplexReport(text))
            if (complex) {
                val r = ReportSummaryParser.parse(text)
                val report = r.copy(date = parsed.date ?: r.date, place = parsed.lab ?: r.place)
                val fill = AutoFill(
                    date = report.date,
                    title = report.examName,
                    professional = report.requestingDoctor,
                    place = report.place,
                    extras = listOfNotNull(
                        report.conclusion?.let { HealthFields.REPORT_CONCLUSION to it },
                        report.reportingDoctor?.let { HealthFields.REPORT_DOCTOR to it },
                    ).toMap(),
                )
                // Laudo de imagem: não sugere valores soltos que a leitura possa ter achado no meio do texto.
                ImportedData(exam = ParsedExam(report.date, report.place, emptyList()), report = report, fill = fill)
            } else {
                val title = if (parsed.values.any { it.item.name in LabCatalog.hemogramNames }) "Hemograma e exames de sangue"
                else if (parsed.values.size >= 3) "Exames de sangue" else null
                val requesting = ReportSummaryParser.parse(text).requestingDoctor
                ImportedData(
                    exam = parsed,
                    fill = AutoFill(date = parsed.date, title = title, place = parsed.lab, professional = requesting).orElse(general.copy(extras = emptyMap(), prescription = null)),
                )
            }
        }
        else -> {
            val base = ExamParser.parse(text, emptyList(), usedPlaces)
            ImportedData(exam = base, fill = general.copy(date = general.date ?: base.date))
        }
    }
}

@Composable
fun rememberFileImporter(type: HealthType, onResult: (PendingFile, ParsedExam?) -> Unit): FileImporter {
    val callback by rememberUpdatedState(onResult)
    return rememberHealthImporter(type) { file, data -> callback(file, data.exam) }
}

/**
 * Escolhe o arquivo, guarda no app e lê o texto. [reportMode] diz se o exame é de imagem/laudo
 * (para ler a conclusão em vez de valores).
 */
@Composable
fun rememberHealthImporter(
    type: HealthType,
    reportMode: Boolean = false,
    onResult: (PendingFile, ImportedData) -> Unit,
): FileImporter {
    val context = LocalContext.current
    val repo = rememberHealthRepository()
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onResult)
    val report by rememberUpdatedState(reportMode)
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
                val places = repo.places(type)
                val data = text?.let { t -> withContext(Dispatchers.Default) { interpret(type, t, places, report) } } ?: ImportedData()
                callback(pending.copy(ocrText = text), data)
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

/** Uma linha de resumo do olho: "-2,00 -0,75 x 180° · AV 20/20 · PIO 14 mmHg · Adição +2,00". */
fun eyeSummary(v: EyeValues): String = listOfNotNull(
    v.refraction().ifBlank { null },
    v.adicao?.let { "Adição $it" },
    v.acuidade?.let { "AV $it" },
    v.pio?.let { "PIO $it mmHg" },
).joinToString(" · ")

/** "Encontramos estes dados na receita": o usuário escolhe o que vai para o formulário. */
@Composable
fun EyeSuggestionsDialog(
    eye: EyeExam,
    onApply: (date: LocalDate?, extras: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var useDate by remember { mutableStateOf(eye.date != null) }
    var useOd by remember { mutableStateOf(!eye.od.isEmpty) }
    var useOe by remember { mutableStateOf(!eye.oe.isEmpty) }
    var useDnp by remember { mutableStateOf(eye.dnp != null) }
    var useDiag by remember { mutableStateOf(eye.diagnoses.isNotEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encontramos estes dados na receita") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Confira com a receita antes de usar. Os valores vão para o formulário e você ainda pode corrigir.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                eye.date?.let { d -> CheckRow(useDate, { useDate = it }, "Data: ${d.format(DateFormat)}", null) }
                if (!eye.od.isEmpty) CheckRow(useOd, { useOd = it }, "Olho direito (OD)", eyeSummary(eye.od))
                if (!eye.oe.isEmpty) CheckRow(useOe, { useOe = it }, "Olho esquerdo (OE)", eyeSummary(eye.oe))
                eye.dnp?.let { CheckRow(useDnp, { v -> useDnp = v }, "DNP: $it mm", null) }
                if (eye.diagnoses.isNotEmpty()) CheckRow(useDiag, { useDiag = it }, "Diagnósticos: ${eye.diagnoses.joinToString(", ")}", null)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val all = eye.toExtras()
                val chosen = all.filterKeys { k ->
                    when {
                        k.startsWith("od_") -> useOd
                        k.startsWith("oe_") -> useOe
                        k == "dnp" -> useDnp
                        k == EyeParser.DIAGNOSES_KEY -> useDiag
                        else -> false
                    }
                }
                onApply(if (useDate) eye.date else null, chosen)
                onDismiss()
            }) { Text("Usar selecionados") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

/** "Resumo do laudo": exame, data, local, médicos e conclusão, para o usuário conferir. */
@Composable
fun ReportSuggestionsDialog(
    summary: ReportSummary,
    onApply: (ReportSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    var useName by remember { mutableStateOf(summary.examName != null) }
    var useDate by remember { mutableStateOf(summary.date != null) }
    var usePlace by remember { mutableStateOf(summary.place != null) }
    var useReq by remember { mutableStateOf(summary.requestingDoctor != null) }
    var useRep by remember { mutableStateOf(summary.reportingDoctor != null) }
    var useConc by remember { mutableStateOf(summary.conclusion != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resumo do laudo") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "O app guarda só o essencial do laudo. O arquivo completo continua anexado. Confira antes de usar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                summary.examName?.let { CheckRow(useName, { v -> useName = v }, "Exame: $it", null) }
                summary.date?.let { d -> CheckRow(useDate, { useDate = it }, "Data: ${d.format(DateFormat)}", null) }
                summary.place?.let { CheckRow(usePlace, { v -> usePlace = v }, "Local: $it", null) }
                summary.requestingDoctor?.let { CheckRow(useReq, { v -> useReq = v }, "Médico que pediu: $it", null) }
                summary.reportingDoctor?.let { CheckRow(useRep, { v -> useRep = v }, "Médico do laudo: $it", null) }
                summary.conclusion?.let { c ->
                    Row(Modifier.fillMaxWidth().clickable { useConc = !useConc }, verticalAlignment = Alignment.Top) {
                        Checkbox(checked = useConc, onCheckedChange = { useConc = it })
                        Column(Modifier.weight(1f)) {
                            Text("Conclusão", fontWeight = FontWeight.Bold)
                            Text(c, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (summary.conclusion == null) {
                    Text(
                        "Não achamos a conclusão no texto. Você pode digitá-la no campo \"Conclusão do laudo\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    ReportSummary(
                        examName = summary.examName.takeIf { useName },
                        date = summary.date.takeIf { useDate },
                        place = summary.place.takeIf { usePlace },
                        requestingDoctor = summary.requestingDoctor.takeIf { useReq },
                        reportingDoctor = summary.reportingDoctor.takeIf { useRep },
                        conclusion = summary.conclusion.takeIf { useConc },
                        looksLikeReport = summary.looksLikeReport,
                    ),
                )
                onDismiss()
            }) { Text("Usar selecionados") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}
