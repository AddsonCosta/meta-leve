package com.addsoncosta.evolui.ui.health

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.ShortDateFormat
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Qual comparação mostrar. */
object CompareMode {
    const val HEMO = "HEMO"
    const val EXAMS = "EXAME"
    const val BIO = "BIO"
}

private val ColDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

/** Uma linha da tabela comparativa: um grupo (título) ou um item com seus valores por data. */
private class CompareRow(val name: String, val group: Boolean, val cells: List<LabResultEntity?>)

/**
 * Comparar exames: itens nas linhas e datas nas colunas, valores fora da faixa em destaque,
 * setas em relação ao exame anterior. Tocar no item abre o gráfico.
 */
@Composable
fun LabCompareScreen(initialMode: String, onBack: () -> Unit, onOpenRecord: (Long) -> Unit) {
    val repo = rememberHealthRepository()
    val container = appContainer()
    val profile by remember { container.repository.profile }.collectAsStateWithLifecycle(initialValue = null)
    val records by remember { repo.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthRecordEntity>())
    val results by remember { repo.observeAllResults() }.collectAsStateWithLifecycle(initialValue = emptyList<LabResultEntity>())
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var query by rememberSaveable { mutableStateOf("") }
    var chart by remember { mutableStateOf<String?>(null) }
    val sex = profile?.sex

    val type = if (mode == CompareMode.BIO) HealthType.BIOIMPEDANCIA else HealthType.EXAME
    val q = Repository.normalize(query)
    val table = remember(records, results, mode, q) { buildTable(records, results, mode, q) }
    val columns = table.first
    val rows = table.second

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            BackHeader("Comparar", if (mode == CompareMode.BIO) "Bioimpedância" else if (mode == CompareMode.HEMO) "Hemograma" else "Exames", onBack) {
                HealthShareButton(if (mode == CompareMode.BIO) ReportArea.BIOIMPEDANCIA else ReportArea.EXAME)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == CompareMode.HEMO, onClick = { mode = CompareMode.HEMO }, label = { Text("Hemograma completo") })
                FilterChip(selected = mode == CompareMode.EXAMS, onClick = { mode = CompareMode.EXAMS }, label = { Text("Todos os exames") })
                FilterChip(selected = mode == CompareMode.BIO, onClick = { mode = CompareMode.BIO }, label = { Text("Bioimpedância") })
            }
        }
        if (columns.isEmpty()) {
            item {
                Panel {
                    EmptyNote(
                        "Ainda não há valores para comparar",
                        if (mode == CompareMode.BIO) "Cadastre uma bioimpedância com os valores (peso, % de gordura...). Com duas ou mais, você vê a evolução aqui."
                        else "Cadastre um exame em Exames e toque em \"Hemograma completo\" para preencher os valores. Com dois ou mais exames, a comparação aparece aqui.",
                    )
                }
            }
            return@LazyColumn
        }
        item { SearchField(query, { query = it }, "Buscar item (ex.: hemoglobina)") }
        item {
            Text(
                "Arraste a tabela para o lado para ver todas as datas. Toque no nome do item para ver o gráfico, ou na data para abrir o exame.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            CompareTable(columns, rows, sex, onItem = { chart = it }, onColumn = onOpenRecord)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Como ler", fontWeight = FontWeight.Bold)
                Text("Valor em vermelho: fora da faixa de referência do laudo (ou da faixa típica de adulto).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("↑ subiu e ↓ desceu em relação ao exame anterior.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Só o seu médico pode interpretar os resultados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    chart?.let { name ->
        val points = results.filter { it.analyte == name && records.any { r -> r.id == it.recordId && r.type == type.name } }.sortedBy { it.epochDay }
        AnalyteChartDialog(name, points, sex, onDismiss = { chart = null })
    }
}

private fun buildTable(
    records: List<HealthRecordEntity>,
    results: List<LabResultEntity>,
    mode: String,
    q: String,
): Pair<List<HealthRecordEntity>, List<CompareRow>> {
    val type = if (mode == CompareMode.BIO) HealthType.BIOIMPEDANCIA.name else HealthType.EXAME.name
    val recs = records.filter { it.type == type }.associateBy { it.id }
    val res = results.filter { r ->
        recs.containsKey(r.recordId) && (mode != CompareMode.HEMO || r.analyte in LabCatalog.hemogramNames)
    }
    val byRecord = res.groupBy { it.recordId }
    val columns = byRecord.keys.mapNotNull { recs[it] }.sortedWith(compareBy({ it.epochDay }, { it.id }))
    val names = res.map { it.analyte }.distinct()
        .filter { q.isEmpty() || Repository.normalize(it).contains(q) }
        .sortedWith(compareBy({ LabCatalog.order(it) }, { it }))
    val rows = mutableListOf<CompareRow>()
    var lastGroup: String? = null
    for (n in names) {
        val group = LabCatalog.find(n)?.group ?: "Outros"
        if (group != lastGroup) {
            rows += CompareRow(group, true, emptyList())
            lastGroup = group
        }
        rows += CompareRow(n, false, columns.map { c -> byRecord[c.id]?.firstOrNull { it.analyte == n } })
    }
    return columns to rows
}

@Composable
private fun CompareTable(
    columns: List<HealthRecordEntity>,
    rows: List<CompareRow>,
    sex: Sex?,
    onItem: (String) -> Unit,
    onColumn: (Long) -> Unit,
) {
    val rowH = 60.dp
    val groupH = 36.dp
    val headH = 64.dp
    val firstW = 150.dp
    val colW = 104.dp
    val line = MaterialTheme.colorScheme.outline
    Panel {
        Row {
            // Coluna fixa com os nomes.
            Column(Modifier.width(firstW)) {
                Box(Modifier.height(headH), contentAlignment = Alignment.CenterStart) {
                    Text("Item", fontWeight = FontWeight.Bold)
                }
                rows.forEach { r ->
                    if (r.group) {
                        Box(Modifier.height(groupH), contentAlignment = Alignment.CenterStart) {
                            Overline(r.name, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Box(
                            Modifier.height(rowH).fillMaxWidth().clickable { onItem(r.name) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column {
                                Text(r.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                                LabCatalog.find(r.name)?.unit?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            // Datas, com rolagem para o lado (começa mostrando as mais recentes).
            val scroll = rememberScrollState(Int.MAX_VALUE)
            Column(Modifier.horizontalScroll(scroll)) {
                Row {
                    columns.forEach { c ->
                        Column(
                            Modifier.width(colW).height(headH).clickable { onColumn(c.id) }.padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(LocalDate.ofEpochDay(c.epochDay).format(ColDate), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(c.place ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                rows.forEach { r ->
                    if (r.group) {
                        Box(Modifier.height(groupH).width(colW * columns.size).background(line.copy(alpha = 0.25f)))
                    } else {
                        Row {
                            r.cells.forEachIndexed { i, cell ->
                                val prev = r.cells.subList(0, i).lastOrNull { it != null }
                                ValueCell(cell, prev, sex, Modifier.width(colW).height(rowH))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCell(cell: LabResultEntity?, prev: LabResultEntity?, sex: Sex?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (cell == null) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Box
        }
        val status = LabCatalog.status(cell, sex)
        val arrow = when {
            prev == null -> ""
            cell.value > prev.value -> " ↑"
            cell.value < prev.value -> " ↓"
            else -> ""
        }
        val color = if (status != 0) Evolui.Red else MaterialTheme.colorScheme.onSurface
        Text(
            LabCatalog.num(cell.value) + arrow,
            color = color,
            fontWeight = if (status != 0) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Tabela de valores de um exame (no detalhe). */
@Composable
fun LabValuesPanel(results: List<LabResultEntity>, sex: Sex?, onItem: (String) -> Unit) {
    val sorted = remember(results) { results.sortedBy { LabCatalog.order(it.analyte) } }
    val outCount = sorted.count { LabCatalog.status(it, sex) != 0 }
    Panel {
        Text("Valores", style = MaterialTheme.typography.titleMedium)
        Text(
            if (outCount == 0) "Todos os ${sorted.size} valores dentro da referência." else "$outCount ${if (outCount == 1) "valor fora" else "valores fora"} da referência (em vermelho).",
            color = if (outCount == 0) Evolui.Neon else Evolui.Red,
        )
        var lastGroup: String? = null
        sorted.forEach { r ->
            val group = LabCatalog.find(r.analyte)?.group ?: "Outros"
            if (group != lastGroup) {
                Overline(group, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary)
                lastGroup = group
            }
            val status = LabCatalog.status(r, sex)
            Row(Modifier.fillMaxWidth().clickable { onItem(r.analyte) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.analyte, fontWeight = FontWeight.Bold)
                    LabCatalog.refLabel(r, sex)?.let {
                        Text("Ref.: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    LabCatalog.num(r.value) + (r.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") + when (status) { 1 -> " ↑"; -1 -> " ↓"; else -> "" },
                    color = if (status != 0) Evolui.Red else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text("Toque num item para ver o gráfico ao longo do tempo. ↑ acima e ↓ abaixo da referência.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Gráfico de um item ao longo do tempo, com a faixa de referência sombreada. */
@Composable
fun AnalyteChartDialog(name: String, points: List<LabResultEntity>, sex: Sex?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (points.isEmpty()) {
                    Text("Sem valores ainda.")
                } else {
                    val last = points.last()
                    val (lo, hi) = LabCatalog.range(last, sex)
                    LabLineChart(points, lo, hi, Modifier.fillMaxWidth().height(220.dp))
                    LabCatalog.refLabel(last, sex)?.let { Text("Faixa de referência: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Column(Modifier.heightIn(max = 240.dp)) {
                        points.reversed().forEach { p ->
                            val s = LabCatalog.status(p, sex)
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(LocalDate.ofEpochDay(p.epochDay).format(com.addsoncosta.evolui.ui.components.DateFormat), Modifier.weight(1f))
                                Text(
                                    LabCatalog.num(p.value) + (p.unit?.let { " $it" } ?: ""),
                                    color = if (s != 0) Evolui.Red else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
fun LabLineChart(points: List<LabResultEntity>, refLow: Double?, refHigh: Double?, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = Evolui.Neon.copy(alpha = 0.14f)
    val outColor = Evolui.Red
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val padL = 8f
        val padR = 8f
        val padT = 36f
        val padB = 40f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val values = points.map { it.value }
        var minV = (values + listOfNotNull(refLow, refHigh)).min()
        var maxV = (values + listOfNotNull(refLow, refHigh)).max()
        if (maxV - minV < 1e-9) { minV -= 1.0; maxV += 1.0 }
        val span = maxV - minV
        minV -= span * 0.1
        maxV += span * 0.1
        val firstDay = points.first().epochDay
        val lastDay = points.last().epochDay
        fun x(i: Int, day: Long): Float =
            if (points.size == 1) padL + w / 2
            else if (lastDay == firstDay) padL + w * i / (points.size - 1)
            else padL + w * ((day - firstDay).toFloat() / (lastDay - firstDay).toFloat())
        fun y(v: Double): Float = padT + h * (1f - ((v - minV) / (maxV - minV)).toFloat())

        // Faixa de referência.
        if (refLow != null || refHigh != null) {
            val top = y(refHigh ?: maxV)
            val bottom = y(refLow ?: minV)
            drawRect(bandColor, topLeft = Offset(padL, top), size = Size(w, bottom - top))
        }
        drawLine(gridColor, Offset(padL, padT + h), Offset(padL + w, padT + h), strokeWidth = 2f)

        val path = Path()
        points.forEachIndexed { i, p ->
            val px = x(i, p.epochDay)
            val py = y(p.value)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, lineColor, style = Stroke(width = 5f))

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val showDates = points.size <= 6
        points.forEachIndexed { i, p ->
            val px = x(i, p.epochDay)
            val py = y(p.value)
            val out = (refLow != null && p.value < refLow) || (refHigh != null && p.value > refHigh)
            drawCircle(if (out) outColor else lineColor, radius = 10f, center = Offset(px, py))
            val tx = px.coerceIn(padL + 40f, padL + w - 40f)
            drawContext.canvas.nativeCanvas.drawText(LabCatalog.num(p.value), tx, py - 18f, label)
            if (showDates || i == 0 || i == points.lastIndex) {
                drawContext.canvas.nativeCanvas.drawText(LocalDate.ofEpochDay(p.epochDay).format(ShortDateFormat), tx, padT + h + 32f, label)
            }
        }
    }
}
