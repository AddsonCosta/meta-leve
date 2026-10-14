package com.addsoncosta.evolui.ui.health

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.addsoncosta.evolui.data.HealthAttachmentEntity
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.health.Attachments
import com.addsoncosta.evolui.health.FieldKind
import com.addsoncosta.evolui.health.HealthFields
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.ParsedExam
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.KeyValue
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import kotlinx.coroutines.launch

/** Detalhe de um registro: dados, valores do exame, anexos, editar, apagar e compartilhar. */
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    onEdit: (HealthType, Long) -> Unit,
    onCompare: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = rememberHealthRepository()
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val profile by remember { container.repository.profile }.collectAsStateWithLifecycle(initialValue = null)
    val record by remember(recordId) { repo.observeRecord(recordId) }.collectAsStateWithLifecycle(initialValue = null)
    val files by remember(recordId) { repo.observeAttachments(recordId) }.collectAsStateWithLifecycle(initialValue = emptyList<HealthAttachmentEntity>())
    val results by remember(recordId) { repo.observeResults(recordId) }.collectAsStateWithLifecycle(initialValue = emptyList<LabResultEntity>())
    val allResults by remember { repo.observeAllResults() }.collectAsStateWithLifecycle(initialValue = emptyList<LabResultEntity>())

    var loadedOnce by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeFile by remember { mutableStateOf<HealthAttachmentEntity?>(null) }
    var showText by remember { mutableStateOf<HealthAttachmentEntity?>(null) }
    var chart by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<ParsedExam?>(null) }

    val r = record
    LaunchedEffect(r) {
        if (r != null) loadedOnce = true
        else if (loadedOnce) onBack() // foi apagado
    }
    if (r == null) return
    val type = HealthFields.typeOf(r.type) ?: HealthType.CONSULTA
    val spec = HealthFields.spec(type)
    val extra = remember(r.extra) { HealthRepository.decodeExtra(r.extra) }

    val importer = rememberFileImporter(type) { pending, parsed ->
        scope.launch {
            repo.addAttachment(r.id, pending)
            if (parsed != null && !parsed.isEmpty && (spec.hasValues || parsed.date != null)) suggestions = parsed
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            BackHeader(type.label, type.singular, onBack) {
                RecordShareButton(r.id, r.title)
                IconButton(onClick = { onEdit(type, r.id) }) { Icon(Icons.Filled.Edit, "Editar") }
            }
        }
        item {
            Panel {
                Text(dayText(r.epochDay), color = type.color(), style = MaterialTheme.typography.labelLarge)
                Text(r.title, style = MaterialTheme.typography.headlineSmall)
                r.status?.takeIf { it.isNotBlank() }?.let { StatusPill(it) }
                r.professional?.takeIf { it.isNotBlank() }?.let { KeyValue(spec.professionalLabel, it) }
                r.place?.takeIf { it.isNotBlank() }?.let { KeyValue(spec.placeLabel.removeSuffix(" *"), it) }
                if (type == HealthType.EXAME && r.place.isNullOrBlank()) {
                    Text("Falta dizer em que laboratório foi feito. Toque em editar.", color = MaterialTheme.colorScheme.error)
                }
                extra.forEach { (k, v) ->
                    val value = if (HealthFields.kind(type, k) == FieldKind.DATE) HealthRepository.parseDate(v)?.format(DateFormat) ?: v else v
                    Column {
                        Text(HealthFields.label(type, k), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                r.description?.takeIf { it.isNotBlank() }?.let {
                    Column {
                        Text(spec.descriptionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Button(onClick = { onEdit(type, r.id) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Edit, null)
                    Text("  Editar", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (spec.hasValues) {
            if (results.isNotEmpty()) {
                item { LabValuesPanel(results, profile?.sex, onItem = { chart = it }) }
            } else {
                item {
                    Panel {
                        EmptyNote("Nenhum valor cadastrado", "Toque em editar para preencher os valores, ou adicione o laudo abaixo para o app tentar ler.")
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onCompare(if (type == HealthType.BIOIMPEDANCIA) CompareMode.BIO else if (results.any { it.analyte in LabCatalog.hemogramNames }) CompareMode.HEMO else CompareMode.EXAMS) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.TableChart, null)
                    Text("  Comparar com outros ${if (type == HealthType.BIOIMPEDANCIA) "resultados" else "exames"}")
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Arquivos (${files.size})", Modifier.weight(1f))
            }
        }
        if (files.isEmpty()) {
            item {
                Text(
                    "Anexe a receita, o pedido ou o laudo em PDF ou foto (JPG, PNG, WEBP, HEIC). O arquivo fica guardado só no celular e o texto dele entra na busca.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(files, key = { it.id }) { f ->
            AttachmentRow(
                f,
                onOpen = { Attachments.open(context, f) },
                onShare = { Attachments.share(context, f) },
                onText = { showText = f },
                onRemove = { removeFile = f },
            )
        }
        item {
            OutlinedButton(
                onClick = { if (!importer.busy) importer.launch() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (importer.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                    Text("  Lendo o arquivo…")
                } else {
                    Icon(Icons.Filled.AttachFile, null)
                    Text("  Adicionar arquivo (PDF ou foto)")
                }
            }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text("  Apagar este registro", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Apagar este registro?",
            text = "\"${r.title}\" e seus ${files.size} arquivo(s) serão apagados do celular. Não dá para desfazer.",
            confirm = "Apagar",
            onConfirm = { container.scope.launch { repo.delete(r) } },
            onDismiss = { confirmDelete = false },
        )
    }
    removeFile?.let { f ->
        ConfirmDialog(
            title = "Remover o arquivo?",
            text = "\"${f.originalName ?: f.fileName}\" será apagado do app.",
            confirm = "Remover",
            onConfirm = { scope.launch { repo.removeAttachment(f) } },
            onDismiss = { removeFile = null },
        )
    }
    showText?.let { f ->
        AlertDialog(
            onDismissRequest = { showText = null },
            title = { Text("Texto lido do arquivo") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(f.ocrText ?: "Nenhum texto foi lido deste arquivo.")
                }
            },
            confirmButton = { TextButton(onClick = { showText = null }) { Text("Fechar") } },
        )
    }
    chart?.let { name ->
        val points = remember(allResults, name) { allResults.filter { it.analyte == name }.sortedBy { it.epochDay } }
        AnalyteChartDialog(name, points, profile?.sex, onDismiss = { chart = null })
    }
    suggestions?.let { parsed ->
        SuggestionsDialog(
            parsed = parsed,
            showLab = type == HealthType.EXAME,
            onApply = { date, lab, values ->
                scope.launch {
                    var updated: HealthRecordEntity = r
                    if (date != null) updated = updated.copy(epochDay = date.toEpochDay())
                    if (lab != null && updated.place.isNullOrBlank()) updated = updated.copy(place = lab)
                    if (updated != r) repo.update(updated)
                    if (values.isNotEmpty()) repo.mergeResults(updated, values.map { it.toResult() })
                    Toast.makeText(context, "Dados adicionados. Confira os valores.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { suggestions = null },
        )
    }
}

@Composable
private fun AttachmentRow(
    f: HealthAttachmentEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onText: () -> Unit,
    onRemove: () -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (Attachments.isPdf(f.mimeType)) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(f.originalName ?: f.fileName, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (f.ocrText.isNullOrBlank()) "Sem texto lido" else "Texto lido · entra na busca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onOpen) { Icon(Icons.Filled.Visibility, "Abrir") }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Enviar arquivo") }
            IconButton(onClick = onText, enabled = !f.ocrText.isNullOrBlank()) { Icon(Icons.Filled.Description, "Ver texto lido") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remover", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
