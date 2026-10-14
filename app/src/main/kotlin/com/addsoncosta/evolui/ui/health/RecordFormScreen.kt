package com.addsoncosta.evolui.ui.health

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.health.Attachments
import com.addsoncosta.evolui.health.FieldKind
import com.addsoncosta.evolui.health.HealthFields
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.LabItem
import com.addsoncosta.evolui.health.ParsedExam
import com.addsoncosta.evolui.health.PendingFile
import com.addsoncosta.evolui.ui.appContainer
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

    fun newRow(item: LabItem?, name: String, value: String = ""): LabRow {
        val row = LabRow(key = nextKey, analyte = item?.name ?: name, value = value, unit = item?.unit ?: "")
        nextKey += 1
        return row
    }
    fun update(key: Long, change: (LabRow) -> LabRow) {
        val i = rows.indexOfFirst { it.key == key }
        if (i >= 0) rows[i] = change(rows[i])
    }
    fun snapshot(): String = listOf(day, title, professional, place, description, status, extras.toMap(), rows.map { it.copy(key = 0, showRef = false) }, pending.size).toString()

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
        } else if (type == HealthType.MEDICACAO) {
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

    val importer = rememberFileImporter(type) { file, parsed ->
        pending += file
        if (parsed != null && !parsed.isEmpty) suggestions = parsed
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
        scope.launch {
            val id = repo.save(record, results, files)
            saved[0] = true
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
        // Ler de arquivo.
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (spec.hasValues) "Ler dados do laudo" else "Anexar arquivo", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (spec.hasValues) "Escolha o PDF ou a foto do laudo. O app lê o texto no próprio celular e sugere a data, o laboratório e os valores para você conferir."
                            else "Receita, pedido, relatório... em PDF ou foto (JPG, PNG, WEBP, HEIC). Fica guardado só no celular.",
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
                        Text("  Escolher PDF ou foto")
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
            }
        }
        item {
            DateField(
                label = if (type == HealthType.MEDICACAO) "Data do registro" else "Data",
                epochDay = day,
                onChange = { it?.let { d -> day = d } },
                help = when (type) {
                    HealthType.EXAME -> "Dia da coleta do exame"
                    HealthType.MEDICACAO -> "Dia em que você anotou ou recebeu a receita"
                    else -> "Dia do atendimento"
                },
            )
        }
        item { HelpField(title, { title = it }, spec.titleLabel, spec.titleHelp) }
        item {
            Column {
                HelpField(
                    place,
                    { place = it; placeError = false },
                    spec.placeLabel,
                    spec.placeHelp,
                    error = if (placeError) "Diga em que laboratório o exame foi feito" else null,
                )
                Suggestions(places, place) { place = it; placeError = false }
            }
        }
        item {
            Column {
                HelpField(professional, { professional = it }, spec.professionalLabel, spec.professionalHelp)
                Suggestions(professionals, professional) { professional = it }
            }
        }
        items(spec.extras, key = { it.key }) { f ->
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
                )
                FieldKind.LONG_TEXT -> HelpField(v, { extras[f.key] = it }, f.label, f.help, multiline = true)
                FieldKind.NUMBER -> HelpField(v, { new -> if (new.all { c -> c.isDigit() }) { extras[f.key] = new } }, f.label, f.help, number = true)
                FieldKind.TEXT -> HelpField(v, { extras[f.key] = it }, f.label, f.help)
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

        if (spec.hasValues) {
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
