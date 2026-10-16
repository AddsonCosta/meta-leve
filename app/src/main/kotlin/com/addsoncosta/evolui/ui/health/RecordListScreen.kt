package com.addsoncosta.evolui.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.HealthAttachmentEntity
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.health.HealthFields
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate

/** Histórico de uma área (consultas, exames, medicação...), com busca e filtros. */
@Composable
fun RecordListScreen(
    type: HealthType,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onCompare: (String) -> Unit,
    onOpenDental: () -> Unit,
) {
    val repo = rememberHealthRepository()
    val records by remember(type) { repo.observeType(type) }.collectAsStateWithLifecycle(initialValue = emptyList<HealthRecordEntity>())
    val attachments by remember { repo.observeAllAttachments() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthAttachmentEntity>())
    val spec = HealthFields.spec(type)

    var query by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf(0) }
    var status by rememberSaveable { mutableStateOf("") }

    val years = remember(records) { records.map { LocalDate.ofEpochDay(it.epochDay).year }.distinct().sortedDescending() }
    val statuses = remember(records) { records.mapNotNull { it.status?.takeIf { s -> s.isNotBlank() } }.distinct() }
    val filesByRecord = remember(attachments) { attachments.groupBy { it.recordId } }
    val q = Repository.normalize(query)
    val shown = remember(records, filesByRecord, q, year, status) {
        records.filter { r ->
            (year == 0 || LocalDate.ofEpochDay(r.epochDay).year == year) &&
                (status.isEmpty() || r.status == status) &&
                (q.isEmpty() || Repository.normalize(
                    listOfNotNull(r.title, r.professional, r.place, r.description, r.status).joinToString(" ") + " " +
                        HealthRepository.decodeExtra(r.extra).values.joinToString(" ") + " " +
                        filesByRecord[r.id].orEmpty().joinToString(" ") { (it.originalName ?: "") + " " + (it.ocrText ?: "") },
                ).let { text -> q.split(' ').filter { it.isNotBlank() }.all { text.contains(it) } })
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BackHeader("Saúde", type.label, onBack) {
                    ReportArea.entries.firstOrNull { it.name == type.name }?.let { HealthShareButton(it) }
                }
            }
            // Atalhos de cada área.
            when (type) {
                HealthType.ODONTOLOGIA -> item {
                    Button(onClick = onOpenDental, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Arcada dentária", fontWeight = FontWeight.Bold)
                    }
                }
                HealthType.EXAME -> item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onCompare(CompareMode.HEMO) }, modifier = Modifier.weight(1f).height(56.dp)) {
                            Icon(Icons.Filled.Bloodtype, null)
                            Text(" Hemograma", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { onCompare(CompareMode.EXAMS) }, modifier = Modifier.weight(1f).height(56.dp)) {
                            Icon(Icons.Filled.TableChart, null)
                            Text(" Comparar")
                        }
                    }
                }
                HealthType.BIOIMPEDANCIA -> item {
                    Button(onClick = { onCompare(CompareMode.BIO) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Filled.TableChart, null)
                        Text("  Comparar e ver gráficos", fontWeight = FontWeight.Bold)
                    }
                }
                HealthType.OFTALMOLOGIA -> item { LastEyeVisit(records) }
                else -> {}
            }
            if (records.isNotEmpty()) {
                item { SearchField(query, { query = it }, "Buscar em ${type.label.lowercase()}") }
                if (years.size > 1 || statuses.isNotEmpty()) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = year == 0 && status.isEmpty(), onClick = { year = 0; status = "" }, label = { Text("Todos") })
                            if (years.size > 1) years.forEach { y ->
                                FilterChip(selected = year == y, onClick = { year = if (year == y) 0 else y }, label = { Text(y.toString()) })
                            }
                            spec.statuses.filter { it in statuses }.plus(statuses.filter { it !in spec.statuses }).forEach { s ->
                                FilterChip(selected = status == s, onClick = { status = if (status == s) "" else s }, label = { Text(s) })
                            }
                        }
                    }
                }
            }
            if (records.isEmpty()) {
                item {
                    Panel {
                        EmptyNote("Nenhum registro de ${type.label.lowercase()} ainda", emptyHelp(type))
                        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Filled.Add, null)
                            Text("  Adicionar ${type.singular.lowercase()}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (shown.isEmpty()) {
                item { EmptyNote("Nada encontrado", "Mude a busca ou toque em \"Todos\" para ver tudo.") }
            } else {
                item { Overline("${shown.size} ${if (shown.size == 1) "registro" else "registros"} · mais recentes primeiro") }
            }
            items(shown, key = { it.id }) { r ->
                RecordRow(r, type, files = filesByRecord[r.id]?.size ?: 0, onClick = { onOpen(r.id) })
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Adicionar", fontWeight = FontWeight.Bold) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun emptyHelp(type: HealthType): String = when (type) {
    HealthType.CONSULTA -> "Guarde cada consulta: médico, especialidade, o que foi dito e o retorno. Você pode anexar a receita ou o pedido em foto ou PDF."
    HealthType.EXAME -> "Cadastre seus exames com o laboratório e os valores. Tire uma foto do laudo ou escolha o PDF: o app tenta ler os valores sozinho para você conferir."
    HealthType.MEDICACAO -> "Anote os remédios que toma ou já tomou: dose, horários, início e fim."
    HealthType.ODONTOLOGIA -> "Registre limpezas, restaurações e tratamentos. Use a arcada dentária para descrever cada dente."
    HealthType.OFTALMOLOGIA -> "Guarde cada consulta com o oftalmologista: o grau de cada olho, a acuidade, a pressão do olho e os diagnósticos. Tire uma foto da receita de óculos ou escolha o PDF: o app tenta ler os valores para você conferir."
    HealthType.BIOIMPEDANCIA -> "Registre os resultados da bioimpedância (peso, % de gordura, massa muscular...) para comparar ao longo do tempo."
    else -> "Registre as sessões, o objetivo e como está a evolução. Pode anexar documentos em foto ou PDF."
}

@Composable
private fun RecordRow(r: HealthRecordEntity, type: HealthType, files: Int, onClick: () -> Unit) {
    val extra = remember(r.extra) { HealthRepository.decodeExtra(r.extra) }
    val color = type.color()
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dayText(r.epochDay), color = color, style = MaterialTheme.typography.labelLarge)
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val line = when (type) {
                    HealthType.MEDICACAO -> listOfNotNull(extra["dose"], extra["frequencia"])
                    HealthType.CONSULTA -> listOfNotNull(r.professional, extra["especialidade"])
                    HealthType.EXAME -> listOfNotNull(r.place?.let { if (HealthFields.isReportExam(extra)) it else "Lab.: $it" })
                    HealthType.OFTALMOLOGIA -> listOfNotNull(
                        eyeLine("OD", extra, "od"),
                        eyeLine("OE", extra, "oe"),
                    )
                    HealthType.ODONTOLOGIA -> listOfNotNull(extra["procedimento"], extra["dentes"]?.let { "dentes $it" })
                    else -> listOfNotNull(r.professional, r.place)
                }.filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val conclusion = extra[HealthFields.REPORT_CONCLUSION]?.takeIf { type == HealthType.EXAME && it.isNotBlank() }
                if (conclusion != null) {
                    Text("Conclusão: $conclusion", style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (type == HealthType.OFTALMOLOGIA) extra[HealthFields.EYE_DIAGNOSES]?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                r.description?.takeIf { it.isNotBlank() && conclusion == null }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.status?.takeIf { it.isNotBlank() }?.let { StatusPill(it) }
                if (files > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, "Anexos", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** "OD -2,00 -0,75 x 180°", ou nulo se o olho não tem grau anotado. */
private fun eyeLine(label: String, extra: Map<String, String>, eye: String): String? {
    fun v(k: String) = extra[HealthFields.eyeKey(eye, k)]?.takeIf { it.isNotBlank() }
    val text = listOfNotNull(v("esferico"), v("cilindrico"), v("eixo")?.let { "x $it°" }).joinToString(" ")
    return if (text.isBlank()) null else "$label $text"
}

/** Destaque da Oftalmologia: data da última consulta, há quantos dias, e a próxima, se marcada. */
@Composable
private fun LastEyeVisit(records: List<HealthRecordEntity>) {
    val last = records.maxByOrNull { it.epochDay }
    val today = LocalDate.now().toEpochDay()
    Panel {
        Overline("Última consulta")
        if (last == null) {
            Text("Nenhuma consulta registrada", style = MaterialTheme.typography.titleLarge)
            Text("O ideal é ir ao oftalmologista uma vez por ano.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val days = today - last.epochDay
            Text(dayText(last.epochDay), style = MaterialTheme.typography.headlineSmall, color = HealthType.OFTALMOLOGIA.color(), fontWeight = FontWeight.Bold)
            Text(
                when {
                    days < 0 -> "Daqui a ${-days} ${if (days == -1L) "dia" else "dias"}"
                    days == 0L -> "Hoje"
                    days == 1L -> "Há 1 dia"
                    days < 60 -> "Há $days dias"
                    days < 730 -> "Há $days dias (cerca de ${days / 30} meses)"
                    else -> "Há $days dias (cerca de ${days / 365} anos)"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (days > 365) {
                Text("Já passou de um ano. Que tal marcar uma nova consulta?", color = Evolui.Orange)
            }
            val next = records.mapNotNull { HealthRepository.parseDate(HealthRepository.decodeExtra(it.extra)["proxima"]) }
                .filter { it.toEpochDay() >= today }.minOrNull()
            if (next != null) Text("Próxima consulta: ${dayText(next.toEpochDay())}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Etiqueta colorida da situação ("Em uso", "Concluído"...). */
@Composable
fun StatusPill(status: String) {
    val color = when (status) {
        "Em uso", "Em andamento", "Resultado pronto" -> Evolui.Neon
        "Suspenso", "Pausado", "Aguardando resultado", "Planejado" -> Evolui.Orange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        status,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 2.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
