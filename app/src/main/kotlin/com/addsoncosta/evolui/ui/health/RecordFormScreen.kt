package com.addsoncosta.evolui.ui.health

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.data.HealthAttachmentEntity
import com.addsoncosta.evolui.health.AiFormReader
import com.addsoncosta.evolui.health.AiSource
import com.addsoncosta.evolui.health.Attachments
import com.addsoncosta.evolui.health.AutoFill
import com.addsoncosta.evolui.health.DocumentAutoFill
import com.addsoncosta.evolui.health.PrescribedMedicine
import com.addsoncosta.evolui.health.Prescription
import com.addsoncosta.evolui.health.TakeReminders
import com.addsoncosta.evolui.health.EyeExam
import com.addsoncosta.evolui.health.EyeParser
import com.addsoncosta.evolui.health.FieldKind
import com.addsoncosta.evolui.health.HealthFields
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.LabItem
import com.addsoncosta.evolui.health.ParsedExam
import com.addsoncosta.evolui.health.PendingFile
import com.addsoncosta.evolui.health.ReportSummary
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.health.care.rememberNotificationAsker
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Uma linha de valor no formulário (texto, para aceitar vírgula enquanto digita). */
private data class LabRow(
    val key: Long,
    val analyte: String,
    val value: String = "",
    val unit: String = "",
    val low: String = "",
    val high: String = "",
    val refText: String = "",
    val showRef: Boolean = false,
)

/** Número para editar: sem arredondar e com vírgula (ex.: 14,25). */
private fun numText(v: Double?): String {
    if (v == null) return ""
    val plain = java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
    return plain.replace('.', ',')
}

/** Formulário para criar ou editar um registro da Saúde. [recordId] 0 = novo. */
@Composable
fun RecordFormScreen(type: HealthType, recordId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val repo = rememberHealthRepository()
    val scope = rememberCoroutineScope()
    val spec = HealthFields.spec(type)
    val profile by remember { container.repository.profile }.collectAsStateWithLifecycle(initialValue = null)
    val places by remember(type) { repo.observePlaces(type) }.collectAsStateWithLifecycle(initialValue = emptyList<String>())
    val professionals by remember(type) { repo.observeProfessionals(type) }.collectAsStateWithLifecycle(initialValue = emptyList<String>())

    var loaded by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<HealthRecordEntity?>(null) }
    var day by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var title by remember { mutableStateOf("") }
    var professional by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val extras = remember { mutableStateMapOf<String, String>() }
    val rows = remember { mutableStateListOf<LabRow>() }
    val pending = remember { mutableStateListOf<PendingFile>() }
    var nextKey by remember { mutableLongStateOf(1L) }
    var initial by remember { mutableStateOf("") }
    var placeError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var addItem by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<ParsedExam?>(null) }
    var eyeSuggestion by remember { mutableStateOf<EyeExam?>(null) }
    var reportSuggestion by remember { mutableStateOf<ReportSummary?>(null) }
    // Leitura automática: campos essenciais que o documento não trouxe ficam destacados ("Preencher").
    val missing = remember { mutableStateListOf<String>() }
    var dateTouched by remember { mutableStateOf(false) }
    var dateRead by remember { mutableStateOf(false) }
    // Receita lida: remédios a salvar em Medicação junto com este registro.
    var prescriptionAsk by remember { mutableStateOf<Prescription?>(null) }
    var rxSource by remember { mutableStateOf<Prescription?>(null) }
    val extraMeds = remember { mutableStateListOf<PrescribedMedicine>() }
    var extraMedsReminders by remember { mutableStateOf(false) }
    var remindOnSave by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    val aiConnected = remember { AiFormReader.isConnected(context) }
    val askNotifications = rememberNotificationAsker()
    val savedFiles by remember(recordId) { repo.observeAttachments(recordId) }.collectAsStateWithLifecycle(initialValue = emptyList<HealthAttachmentEntity>())
    val supplements by remember(type) { repo.observeType(type) }.collectAsStateWithLifecycle(initialValue = emptyList<HealthRecordEntity>())
    // Exame de imagem ou procedimento: guarda só o resumo do laudo, sem tabela de valores.
    val reportExam = type == HealthType.EXAME && extras[HealthFields.EXAM_KIND] == HealthFields.EXAM_KIND_REPORT

    fun newRow(item: LabItem?, name: String, value: String = ""): LabRow {
        val row = LabRow(key = nextKey, analyte = item?.name ?: name, value = value, unit = item?.unit ?: "")
        nextKey += 1
        return row
    }
    fun update(key: Long, change: (LabRow) -> LabRow) {
        val i = rows.indexOfFirst { it.key == key }
        if (i >= 0) rows[i] = change(rows[i])
    }
    fun snapshot(): String = listOf(day, title, professional, place, description, status, extras.toMap(), rows.map { it.copy(key = 0, showRef = false) }, pending.size, extraMeds.size).toString()

    fun valueOf(key: String): String = when (key) {
        DocumentAutoFill.DATE -> if (dateRead || dateTouched) "ok" else ""
        DocumentAutoFill.TITLE -> title.takeIf { it != type.singular }.orEmpty()
        DocumentAutoFill.PROFESSIONAL -> professional
        DocumentAutoFill.PLACE -> place
        else -> extras[key].orEmpty()
    }
    fun marked(key: String): Boolean = key in missing

    /**
     * Preenche os campos vazios com o que a leitura achou e destaca os essenciais que faltaram.
     * Devolve quantos campos foram preenchidos.
     */
    fun applyFill(fill: AutoFill): Int {
        var count = 0
        val isNew = original == null
        fill.date?.let { d ->
            dateRead = true
            if (isNew && !dateTouched) { day = d.toEpochDay(); count++ }
        }
        fill.title?.let { if (title.isBlank() || title == type.singular) { title = it; count++ } }
        fill.professional?.let { if (professional.isBlank()) { professional = it; count++ } }
        fill.place?.let { if (place.isBlank()) { place = it; placeError = false; count++ } }
        fill.description?.let { if (description.isBlank()) { description = it; count++ } }
        fill.extras.forEach { (k, v) -> if (extras[k].isNullOrBlank() && v.isNotBlank()) { extras[k] = v; count++ } }
        val essential = DocumentAutoFill.essentialKeys(type, type == HealthType.EXAME && extras[HealthFields.EXAM_KIND] == HealthFields.EXAM_KIND_REPORT)
        missing.clear()
        essential.filter { it != DocumentAutoFill.DATE && valueOf(it).isBlank() }.forEach { missing += it }
        if (DocumentAutoFill.DATE in essential && !dateRead && !dateTouched && isNew) missing += DocumentAutoFill.DATE
        return count
    }

    /** Receita com remédios: na Medicação, o primeiro já foi para o formulário; o resto (e na Consulta, todos) vai junto ao salvar. */
    fun offerPrescription(rx: Prescription?) {
        if (rx == null || rx.medicines.isEmpty()) return
        if (type != HealthType.MEDICACAO && type != HealthType.CONSULTA) return
        rxSource = rx
        val others = if (type == HealthType.MEDICACAO) rx.medicines.drop(1) else rx.medicines
        if (others.isEmpty()) return
        prescriptionAsk = rx.copy(medicines = others)
    }

    LaunchedEffect(recordId) {
        if (recordId != 0L) {
            val r = repo.get(recordId)
            if (r != null) {
                original = r
                day = r.epochDay
                title = r.title
                professional = r.professional.orEmpty()
                place = r.place.orEmpty()
                description = r.description.orEmpty()
                status = r.status.orEmpty()
                extras.putAll(HealthRepository.decodeExtra(r.extra))
                repo.results(recordId).sortedBy { LabCatalog.order(it.analyte) }.forEach { res ->
                    rows += LabRow(
                        key = nextKey, analyte = res.analyte, value = numText(res.value), unit = res.unit.orEmpty(),
                        low = numText(res.refLow), high = numText(res.refHigh), refText = res.refText.orEmpty(),
                    )
                    nextKey += 1
                }
            }
        } else if (type == HealthType.BIOIMPEDANCIA) {
            LabCatalog.bioimpedance.forEach { rows += newRow(it, it.name) }
            title = "Bioimpedância"
        } else if (type == HealthType.MEDICACAO || type == HealthType.SUPLEMENTACAO) {
            status = "Em uso"
        }
        initial = snapshot()
        loaded = true
    }

    // Arquivos escolhidos e não salvos são apagados ao sair sem salvar.
    DisposableEffect(Unit) {
        onDispose {
            if (!saved[0]) {
                val files = pending.toList()
                container.scope.launch { files.forEach { Attachments.deleteFile(context, it.fileName) } }
            }
        }
    }

    fun requestExit() {
        if (loaded && snapshot() != initial) confirmExit = true else onBack()
    }
    BackHandler { requestExit() }

    val importer = rememberHealthImporter(type, reportExam) { file, data ->
        pending += file
        val eye = data.eye
        val report = data.report
        val parsed = data.exam
        if (report != null && !report.isEmpty) {
            // Laudo de imagem reconhecido: muda o formulário para o modo laudo.
            extras[HealthFields.EXAM_KIND] = HealthFields.EXAM_KIND_REPORT
        }
        // Campos de texto: preenche sozinho o que estiver vazio.
        val filled = applyFill(data.fill ?: AutoFill())
        when {
            eye != null && !eye.isEmpty -> eyeSuggestion = eye
            parsed != null && parsed.values.isNotEmpty() && spec.hasValues -> suggestions = parsed
        }
        offerPrescription(data.fill?.prescription)
        if (filled > 0) {
            Toast.makeText(context, "Preenchemos $filled ${if (filled == 1) "campo" else "campos"} com o arquivo. Confira.", Toast.LENGTH_SHORT).show()
        }
    }

    fun readWithAi() {
        if (aiBusy) return
        val sources = pending.map { AiSource(Attachments.file(context, it.fileName), it.mimeType, it.ocrText) } +
            savedFiles.map { AiSource(Attachments.file(context, it.fileName), it.mimeType, it.ocrText) }
        if (sources.isEmpty()) {
            Toast.makeText(context, "Anexe um arquivo primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        aiBusy = true
        scope.launch {
            try {
                AiFormReader.read(context, type, reportExam, sources)
                    .onSuccess { fill ->
                        val n = applyFill(fill)
                        offerPrescription(fill.prescription)
                        Toast.makeText(
                            context,
                            if (n > 0) "Sua IA preencheu $n ${if (n == 1) "campo" else "campos"}. Confira." else "Sua IA não achou mais nada para preencher.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: "Não deu para ler com a IA agora.", Toast.LENGTH_LONG).show()
                    }
            } finally {
                aiBusy = false
            }
        }
    }

    fun save() {
        if (saving) return
        if (spec.placeRequired && place.isBlank()) {
            placeError = true
            return
        }
        saving = true
        val record = HealthRecordEntity(
            id = original?.id ?: 0L,
            type = type.name,
            epochDay = day,
            title = title.trim().ifBlank { type.singular },
            professional = professional.trim().ifBlank { null },
            place = place.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
            status = status.ifBlank { null },
            extra = HealthRepository.encodeExtra(extras.toMap()),
            createdMillis = original?.createdMillis ?: 0L,
            updatedMillis = 0L,
        )
        val results = if (spec.hasValues) {
            rows.mapNotNull { row ->
                val v = row.value.toDecimal() ?: return@mapNotNull null
                LabResultEntity(
                    recordId = 0, epochDay = day, analyte = row.analyte.trim(), value = v,
                    unit = row.unit.trim().ifBlank { null },
                    refLow = row.low.toDecimal(), refHigh = row.high.toDecimal(),
                    refText = row.refText.trim().ifBlank { null },
                )
            }
        } else null
        val files = pending.toList()
        val meds = extraMeds.toList()
        val medsReminders = extraMedsReminders
        val remindThis = remindOnSave && original == null && (type == HealthType.MEDICACAO || type == HealthType.SUPLEMENTACAO)
        val rx = rxSource
        scope.launch {
            val id = repo.save(record, results, files)
            saved[0] = true
            var alarms = 0
            val dao = container.database.healthEventDao()
            if (remindThis) {
                runCatching { alarms += TakeReminders.createFor(context.applicationContext, dao, record.copy(id = id)) }
            }
            var created = 0
            if (meds.isNotEmpty()) {
                val source = rx ?: Prescription(date = LocalDate.ofEpochDay(day), doctor = professional.trim().ifBlank { null })
                runCatching {
                    val (n, a) = TakeReminders.saveMedicines(context.applicationContext, repo, dao, source, meds, medsReminders)
                    created = n
                    alarms += a
                }
            }
            val msg = listOfNotNull(
                if (created > 0) "$created ${if (created == 1) "remédio salvo" else "remédios salvos"} em Medicação" else null,
                if (alarms > 0) "$alarms ${if (alarms == 1) "lembrete criado" else "lembretes criados"}" else null,
            ).joinToString(" · ")
            if (msg.isNotBlank()) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            saving = false
            onSaved(id)
        }
    }

    if (!loaded) return

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            BackHeader(if (original == null) "Novo registro" else "Editar", type.singular, onBack = { requestExit() }) {
                IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, "Salvar") }
            }
        }
        if (type == HealthType.EXAME) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tipo de exame", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !reportExam, onClick = { extras.remove(HealthFields.EXAM_KIND) }, label = { Text("Sangue, urina... (valores)") })
                        FilterChip(
                            selected = reportExam,
                            onClick = { extras[HealthFields.EXAM_KIND] = HealthFields.EXAM_KIND_REPORT },
                            label = { Text("Imagem ou laudo") },
                        )
                    }
                    Text(
                        if (reportExam) "Ultrassom, ressonância, tomografia, raio-X, ecocardiograma, endoscopia, biópsia, mamografia..."
                        else "Exames com tabela de resultados, como hemograma e colesterol.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Ler de arquivo.
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                reportExam -> "Anexe o laudo"
                                type == HealthType.OFTALMOLOGIA -> "Ler a receita de óculos"
                                spec.hasValues -> "Ler dados do laudo"
                                type == HealthType.MEDICACAO -> "Ler a receita"
                                type == HealthType.SUPLEMENTACAO -> "Foto do rótulo"
                                else -> "Anexar e ler arquivo"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                reportExam -> "Escolha o PDF ou a foto do laudo. O app guarda só o essencial (exame, data, local, médico e a conclusão) e o arquivo completo fica anexado."
                                type == HealthType.OFTALMOLOGIA -> "Escolha o PDF ou a foto da receita. O app lê o grau de cada olho (OD e OE), a acuidade, a pressão do olho e os diagnósticos para você conferir."
                                type == HealthType.BIOIMPEDANCIA -> "Escolha o PDF ou a foto do resultado. O app pega só o essencial: peso, % de gordura, massa muscular, água, gordura visceral, TMB, idade metabólica e IMC."
                                spec.hasValues -> "Escolha o PDF ou a foto do laudo. O app lê o texto no próprio celular e sugere a data, o laboratório e os valores para você conferir."
                                type == HealthType.MEDICACAO -> "Escolha a foto ou o PDF da receita. O app lê o médico, a data e cada remédio com a dose, como tomar e por quantos dias, e oferece lembretes."
                                type == HealthType.CONSULTA -> "Receita, pedido ou relatório em PDF ou foto. O app lê o médico, a especialidade, a data e os remédios receitados para você conferir."
                                type == HealthType.SUPLEMENTACAO -> "Opcional. Tire foto do rótulo: o app tenta ler o nome, a marca e a dose."
                                else -> "Receita, pedido, relatório... em PDF ou foto (JPG, PNG, WEBP, HEIC). O app preenche o que conseguir ler. Fica guardado só no celular."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedButton(onClick = { if (!importer.busy) importer.launch() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (importer.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Text("  Lendo o arquivo…")
                    } else {
                        Icon(Icons.Filled.AttachFile, null)
                        Text(if (reportExam) "  Anexar o laudo (PDF ou foto)" else "  Escolher PDF ou foto")
                    }
                }
                pending.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (f.originalName ?: f.fileName) + if (f.ocrText.isNullOrBlank()) "" else " · texto lido",
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = {
                            pending.remove(f)
                            scope.launch { Attachments.deleteFile(context, f.fileName) }
                        }) { Icon(Icons.Filled.Close, "Tirar arquivo") }
                    }
                }
                // Com a IA do usuário conectada: leitura mais completa dos campos vazios.
                if (aiConnected && (pending.isNotEmpty() || savedFiles.isNotEmpty())) {
                    OutlinedButton(onClick = { readWithAi() }, enabled = !aiBusy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (aiBusy) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text("  Lendo com sua IA…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Text("  Ler com minha IA")
                        }
                    }
                    Text(
                        "Envia as páginas do arquivo para a IA que você conectou e preenche os campos que ficaram vazios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (missing.isNotEmpty()) {
                    Text(
                        "Os campos destacados com \"Preencher\" não foram lidos do arquivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        // Suplementos já cadastrados: escolher rápido.
        if (type == HealthType.SUPLEMENTACAO && original == null) {
            val quick = supplements.distinctBy { Repository.normalize(it.title) }.take(12)
            if (quick.isNotEmpty()) {
                item(key = "quick") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Escolher rápido", fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quick.forEach { r ->
                                val e = HealthRepository.decodeExtra(r.extra)
                                AssistChip(
                                    onClick = {
                                        title = r.title
                                        listOf("marca", "dose", "horario").forEach { k -> e[k]?.let { extras[k] = it } }
                                        if (professional.isBlank()) r.professional?.let { professional = it }
                                        if (place.isBlank()) r.place?.let { place = it }
                                        if (extras["inicio"].isNullOrBlank()) extras["inicio"] = LocalDate.now().toString()
                                        status = "Em uso"
                                    },
                                    label = { Text(listOfNotNull(r.title, e["marca"]).joinToString(" · ")) },
                                )
                            }
                        }
                        Text("Toque para usar os dados de um suplemento que você já cadastrou.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            DateField(
                label = if (type == HealthType.MEDICACAO) "Data do registro" else "Data",
                epochDay = day,
                onChange = { it?.let { d -> day = d; dateTouched = true; missing.remove(DocumentAutoFill.DATE) } },
                highlight = marked(DocumentAutoFill.DATE),
                help = when (type) {
                    HealthType.EXAME -> if (reportExam) "Dia em que o exame foi feito" else "Dia da coleta do exame"
                    HealthType.OFTALMOLOGIA -> "Dia da consulta ou da receita"
                    HealthType.MEDICACAO -> "Dia em que você anotou ou recebeu a receita"
                    HealthType.SUPLEMENTACAO -> "Dia em que você cadastrou"
                    else -> "Dia do atendimento"
                },
            )
        }
        item {
            HelpField(
                title, { title = it }, spec.titleLabel,
                if (reportExam) "Ex.: Ultrassonografia de abdome total, ressonância do joelho" else spec.titleHelp,
                highlight = marked(DocumentAutoFill.TITLE),
            )
        }
        item {
            Column {
                HelpField(
                    place,
                    { place = it; placeError = false },
                    if (reportExam) "Clínica ou laboratório *" else spec.placeLabel,
                    if (reportExam) "Onde o exame foi feito. Ex.: Clínica Imagem Total" else spec.placeHelp,
                    error = if (placeError) "Diga em que ${if (reportExam) "clínica ou laboratório" else "laboratório"} o exame foi feito" else null,
                    highlight = marked(DocumentAutoFill.PLACE),
                )
                Suggestions(places, place) { place = it; placeError = false }
            }
        }
        item {
            Column {
                HelpField(professional, { professional = it }, spec.professionalLabel, spec.professionalHelp, highlight = marked(DocumentAutoFill.PROFESSIONAL))
                Suggestions(professionals, professional) { professional = it }
            }
        }
        if (type == HealthType.OFTALMOLOGIA) {
            item(key = "eyes") {
                EyeFields(values = extras, missing = missing.toSet(), onChange = { k, v -> if (v.isBlank()) { extras.remove(k) } else { extras[k] = v } })
            }
            item(key = "eye_diagnoses") {
                EyeDiagnoses(
                    value = extras[HealthFields.EYE_DIAGNOSES].orEmpty(),
                    onChange = { v ->
                        if (v.isBlank()) {
                            extras.remove(HealthFields.EYE_DIAGNOSES)
                        } else {
                            extras[HealthFields.EYE_DIAGNOSES] = v
                        }
                    },
                )
            }
        }
        val formExtras = spec.extras.filter { !HealthFields.isEyeKey(it.key) && it.key != HealthFields.EYE_DIAGNOSES } +
            if (reportExam) HealthFields.reportFields else emptyList()
        items(formExtras, key = { it.key }) { f ->
            val v = extras[f.key].orEmpty()
            when (f.kind) {
                FieldKind.DATE -> DateField(
                    label = f.label,
                    epochDay = HealthRepository.parseDate(v)?.toEpochDay(),
                    onChange = { d ->
                        if (d == null) {
                            extras.remove(f.key)
                        } else {
                            extras[f.key] = LocalDate.ofEpochDay(d).toString()
                        }
                    },
                    help = f.help,
                    clearable = true,
                    highlight = marked(f.key) && v.isBlank(),
                )
                FieldKind.LONG_TEXT -> HelpField(v, { extras[f.key] = it }, f.label, f.help, multiline = true, highlight = marked(f.key))
                FieldKind.NUMBER -> HelpField(v, { new -> if (new.all { c -> c.isDigit() }) { extras[f.key] = new } }, f.label, f.help, number = true, highlight = marked(f.key))
                FieldKind.TEXT -> HelpField(v, { extras[f.key] = it }, f.label, f.help, highlight = marked(f.key))
            }
        }
        // Lembretes de tomar (só ao cadastrar).
        if (original == null && (type == HealthType.MEDICACAO || type == HealthType.SUPLEMENTACAO)) {
            item(key = "remind") {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lembrar de tomar", fontWeight = FontWeight.Bold)
                            val plan = TakeReminders.planFor(type, day, extras.toMap())
                            Text(
                                if (remindOnSave) "Ao salvar, cria avisos ${plan.describe()}. Dá para mudar no Calendário."
                                else "Cria avisos no calendário nos horários da ${if (type == HealthType.SUPLEMENTACAO) "frequência" else "receita"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = remindOnSave, onCheckedChange = { remindOnSave = it; if (it) askNotifications() })
                    }
                }
            }
        }
        // Remédios da receita que serão salvos junto.
        if (extraMeds.isNotEmpty()) {
            item(key = "extra_meds") {
                Panel {
                    Overline("Também vão para Medicação")
                    extraMeds.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.Bold)
                                Text(medicineSummary(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { extraMeds.remove(m) }) { Icon(Icons.Filled.Close, "Tirar ${m.name}") }
                        }
                    }
                    Text(
                        if (extraMedsReminders) "Serão salvos ao tocar em Salvar, com lembretes." else "Serão salvos ao tocar em Salvar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (spec.statuses.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Situação", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spec.statuses.forEach { s ->
                            FilterChip(selected = status == s, onClick = { status = if (status == s) "" else s }, label = { Text(s) })
                        }
                    }
                    Text("Toque de novo para desmarcar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { HelpField(description, { description = it }, spec.descriptionLabel, spec.descriptionHelp, multiline = true) }

        if (spec.hasValues && !reportExam) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Overline("Valores")
                    Text(
                        if (type == HealthType.EXAME) "Preencha só os itens do seu laudo. Itens vazios não são salvos."
                        else "Preencha o que a sua bioimpedância mostra. Itens vazios não são salvos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (type == HealthType.EXAME) {
                        Button(
                            onClick = {
                                val present = rows.map { it.analyte }.toSet()
                                LabCatalog.hemogram.filter { it.name !in present }.forEach { rows += newRow(it, it.name) }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Filled.Bloodtype, null)
                            Text("  Hemograma completo", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = { addItem = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.Add, null)
                        Text("  Adicionar outro item")
                    }
                }
            }
            items(rows, key = { it.key }) { row ->
                LabRowEditor(
                    row = row,
                    sex = profile?.sex,
                    onChange = { changed -> update(row.key) { changed } },
                    onRemove = { rows.removeAll { it.key == row.key } },
                )
            }
        }
        item {
            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Sair sem salvar?") },
            text = { Text("O que você preencheu será perdido.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Sair sem salvar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmExit = false; save() }) { Text("Salvar") } },
        )
    }
    prescriptionAsk?.let { rx ->
        PrescriptionDialog(
            prescription = rx,
            note = if (type == HealthType.MEDICACAO) "O primeiro remédio da receita já está no formulário. Escolha os outros que também quer salvar em Medicação."
            else "Escolha os remédios que quer salvar em Medicação. Eles são salvos quando você tocar em Salvar.",
            confirmLabel = "Salvar junto",
            onConfirm = { meds, reminders ->
                extraMeds.clear()
                extraMeds.addAll(meds)
                extraMedsReminders = reminders
                if (type == HealthType.MEDICACAO && reminders && rxSource?.medicines?.firstOrNull()?.asNeeded != true) remindOnSave = true
            },
            onDismiss = { prescriptionAsk = null },
            askNotifications = askNotifications,
        )
    }
    eyeSuggestion?.let { eye ->
        EyeSuggestionsDialog(
            eye = eye,
            onApply = { date, values ->
                date?.let { day = it.toEpochDay() }
                extras.putAll(values)
            },
            onDismiss = { eyeSuggestion = null },
        )
    }
    reportSuggestion?.let { rep ->
        ReportSuggestionsDialog(
            summary = rep,
            onApply = { s ->
                s.date?.let { day = it.toEpochDay() }
                s.examName?.let { if (title.isBlank() || title == type.singular) title = it }
                s.place?.let { if (place.isBlank()) { place = it; placeError = false } }
                s.requestingDoctor?.let { if (professional.isBlank()) professional = it }
                s.reportingDoctor?.let { extras[HealthFields.REPORT_DOCTOR] = it }
                s.conclusion?.let { extras[HealthFields.REPORT_CONCLUSION] = it }
            },
            onDismiss = { reportSuggestion = null },
        )
    }
    if (addItem) {
        AddItemDialog(
            items = catalogFor(type).ifEmpty { LabCatalog.labItems },
            present = rows.map { it.analyte }.toSet(),
            onPick = { item, name -> rows += newRow(item, name) },
            onDismiss = { addItem = false },
        )
    }
    suggestions?.let { parsed ->
        SuggestionsDialog(
            parsed = parsed,
            showLab = type == HealthType.EXAME,
            onApply = { date, lab, values ->
                date?.let { day = it.toEpochDay() }
                lab?.let { place = it; placeError = false }
                values.forEach { v ->
                    val text = numText(v.value)
                    val existing = rows.firstOrNull { it.analyte.equals(v.item.name, ignoreCase = true) }
                    if (existing != null) update(existing.key) { it.copy(value = text) } else rows += newRow(v.item, v.item.name, text)
                }
            },
            onDismiss = { suggestions = null },
        )
    }
}

/** Sugestões tocáveis do que já foi usado antes (laboratórios, médicos). */
@Composable
private fun Suggestions(options: List<String>, current: String, onPick: (String) -> Unit) {
    val q = Repository.normalize(current)
    val shown = options.filter { it != current && (q.isEmpty() || Repository.normalize(it).contains(q)) }.take(8)
    if (shown.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { s -> AssistChip(onClick = { onPick(s) }, label = { Text(s) }) }
    }
}

@Composable
private fun LabRowEditor(row: LabRow, sex: Sex?, onChange: (LabRow) -> Unit, onRemove: () -> Unit) {
    val item = remember(row.analyte) { LabCatalog.find(row.analyte) }
    val typical = item?.let { LabCatalog.rangeText(it.refLow(sex), it.refHigh(sex), it.unit) ?: it.refText }
    val lo = row.low.toDecimal()
    val hi = row.high.toDecimal()
    val v = row.value.toDecimal()
    val useLo = if (row.low.isNotBlank() || row.high.isNotBlank()) lo else item?.refLow(sex)
    val useHi = if (row.low.isNotBlank() || row.high.isNotBlank()) hi else item?.refHigh(sex)
    val out = v != null && ((useLo != null && v < useLo) || (useHi != null && v > useHi))
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.analyte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Tirar ${row.analyte}") }
        }
        NumberField(
            value = row.value,
            onValueChange = { onChange(row.copy(value = it)) },
            label = "Resultado",
            suffix = row.unit.ifBlank { null },
            help = when {
                out -> "Fora da faixa de referência"
                row.refText.isNotBlank() -> "Referência do laudo: ${row.refText}"
                row.low.isNotBlank() || row.high.isNotBlank() -> "Referência do laudo: ${LabCatalog.rangeText(lo, hi, row.unit) ?: "-"}"
                typical != null -> "Referência típica de adulto: $typical"
                else -> "Ex.: 14,2"
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (row.showRef) "Fechar unidade e referência" else "Editar unidade e referência do laudo",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onChange(row.copy(showRef = !row.showRef)) }.padding(vertical = 4.dp),
        )
        if (row.showRef) {
            HelpField(row.unit, { onChange(row.copy(unit = it)) }, "Unidade", "Ex.: g/dL, mg/dL, %")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(row.low, { onChange(row.copy(low = it)) }, "Mínimo", Modifier.weight(1f), help = "Do laudo")
                NumberField(row.high, { onChange(row.copy(high = it)) }, "Máximo", Modifier.weight(1f), help = "Do laudo")
            }
            HelpField(row.refText, { onChange(row.copy(refText = it)) }, "Referência em texto", "Opcional. Ex.: inferior a 190 mg/dL")
            Text(
                "A referência do laudo tem prioridade. Deixe vazio para usar a faixa típica de adulto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Escolher um item do catálogo (ou criar um com outro nome). */
@Composable
private fun AddItemDialog(items: List<LabItem>, present: Set<String>, onPick: (LabItem?, String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = Repository.normalize(query)
    val shown = items.filter { it.name !in present && (q.isEmpty() || Repository.normalize(it.name).contains(q) || it.synonyms.any { s -> s.contains(q) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, "Ex.: glicose, TSH")
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (query.isNotBlank() && items.none { Repository.normalize(it.name) == q }) {
                        Text(
                            "+ Criar \"${query.trim()}\"",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null, query.trim()); onDismiss() }.padding(vertical = 12.dp),
                        )
                    }
                    var lastGroup: String? = null
                    shown.forEach { item ->
                        if (item.group != lastGroup) {
                            Overline(item.group, Modifier.padding(top = 8.dp))
                            lastGroup = item.group
                        }
                        Column(Modifier.fillMaxWidth().clickable { onPick(item, item.name); onDismiss() }.padding(vertical = 10.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            if (item.unit.isNotBlank()) Text(item.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/** Grau e medidas de cada olho, lado a lado: OD (direito) e OE (esquerdo). */
@Composable
private fun EyeFields(values: Map<String, String>, missing: Set<String>, onChange: (String, String) -> Unit) {
    Panel {
        Overline("Receita e exame dos olhos")
        Text(
            "OD = olho direito, OE = olho esquerdo. Use vírgula e o sinal: -2,00 ou +1,25. Deixe vazio o que não tiver.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        HealthFields.eyeMeasures.forEach { m ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(m.label + (m.unit?.let { " ($it)" } ?: ""), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("od" to "OD", "oe" to "OE").forEach { (eye, label) ->
                        val key = HealthFields.eyeKey(eye, m.key)
                        val mark = key in missing && values[key].isNullOrBlank()
                        OutlinedTextField(
                            value = values[key].orEmpty(),
                            onValueChange = { onChange(key, it) },
                            label = { Text(label) },
                            supportingText = if (mark) { { Text("Preencher") } } else null,
                            colors = if (mark) fillColors() else OutlinedTextFieldDefaults.colors(),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(m.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Diagnósticos dos olhos em chips (miopia, glaucoma...). Guardados como texto separado por vírgula. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EyeDiagnoses(value: String, onChange: (String) -> Unit) {
    val selected = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Diagnósticos", fontWeight = FontWeight.Bold)
        Text(
            "Toque nos que o médico citou. Outros vão no campo \"Outros diagnósticos e orientações\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val names = EyeParser.knownDiagnoses.map { it.first }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (names + selected.filter { it !in names }).forEach { d ->
                val on = d in selected
                FilterChip(
                    selected = on,
                    onClick = { onChange((if (on) selected - d else selected + d).joinToString(", ")) },
                    label = { Text(d) },
                )
            }
        }
    }
}
