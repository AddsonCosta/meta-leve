package com.addsoncosta.evolui.ui.health.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.ToothStateEntity
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Situações de um dente. O [name] é gravado em ToothStateEntity.status; não renomear. */
internal enum class ToothStatus(val label: String) {
    SAUDAVEL("Saudável"),
    CARIE("Cárie"),
    RESTAURADO("Restaurado"),
    CANAL("Canal"),
    COROA("Coroa"),
    IMPLANTE("Implante"),
    PROTESE("Prótese"),
    AUSENTE("Ausente"),
    EXTRAIR("Extrair"),
    EM_TRATAMENTO("Em tratamento"),
    SENSIBILIDADE("Sensibilidade"),
    OUTRO("Outro"),
}

internal fun toothStatusOf(name: String?): ToothStatus = ToothStatus.entries.firstOrNull { it.name == name } ?: ToothStatus.OUTRO

/** Como cada situação aparece no desenho. */
private data class ToothLook(val fill: Color?, val stroke: Color, val dashed: Boolean = false, val cross: Boolean = false, val text: Color)

@Composable
private fun toothLooks(): Map<ToothStatus, ToothLook> {
    val scheme = MaterialTheme.colorScheme
    val onSolid = scheme.background
    val on = scheme.onSurface
    return mapOf(
        ToothStatus.SAUDAVEL to ToothLook(scheme.surfaceVariant, scheme.outline, text = on),
        ToothStatus.CARIE to ToothLook(Evolui.Red, Evolui.Red, text = onSolid),
        ToothStatus.RESTAURADO to ToothLook(Evolui.Cyan, Evolui.Cyan, text = onSolid),
        ToothStatus.CANAL to ToothLook(Evolui.Purple, Evolui.Purple, text = onSolid),
        ToothStatus.COROA to ToothLook(Evolui.Orange, Evolui.Orange, text = onSolid),
        ToothStatus.IMPLANTE to ToothLook(Evolui.Neon, Evolui.Neon, text = onSolid),
        ToothStatus.PROTESE to ToothLook(Evolui.TextMuted, Evolui.TextMuted, text = onSolid),
        ToothStatus.AUSENTE to ToothLook(null, Evolui.TextMuted, dashed = true, text = Evolui.TextMuted),
        ToothStatus.EXTRAIR to ToothLook(Evolui.Red.copy(alpha = 0.22f), Evolui.Red, cross = true, text = on),
        ToothStatus.EM_TRATAMENTO to ToothLook(Evolui.Orange.copy(alpha = 0.30f), Evolui.Orange, text = on),
        ToothStatus.SENSIBILIDADE to ToothLook(Evolui.Cyan.copy(alpha = 0.30f), Evolui.Cyan, text = on),
        ToothStatus.OUTRO to ToothLook(Evolui.Purple.copy(alpha = 0.30f), Evolui.Purple, text = on),
    )
}

// Numeração FDI, na ordem em que aparece na tela (o lado direito do paciente fica à esquerda).
private val PermanentUpper = (18 downTo 11).toList() + (21..28).toList()
private val PermanentLower = (48 downTo 41).toList() + (31..38).toList()
private val DeciduousUpper = (55 downTo 51).toList() + (61..65).toList()
private val DeciduousLower = (85 downTo 81).toList() + (71..75).toList()

/** "1º molar superior direito" (FDI). */
internal fun toothName(number: Int): String {
    val q = number / 10
    val p = number % 10
    val deciduous = q >= 5
    val kind = if (deciduous) {
        when (p) {
            1 -> "Incisivo central"
            2 -> "Incisivo lateral"
            3 -> "Canino"
            4 -> "1º molar"
            else -> "2º molar"
        }
    } else {
        when (p) {
            1 -> "Incisivo central"
            2 -> "Incisivo lateral"
            3 -> "Canino"
            4 -> "1º pré-molar"
            5 -> "2º pré-molar"
            6 -> "1º molar"
            7 -> "2º molar"
            else -> "3º molar (siso)"
        }
    }
    val side = when (q) {
        1, 5 -> "superior direito"
        2, 6 -> "superior esquerdo"
        3, 7 -> "inferior esquerdo"
        else -> "inferior direito"
    }
    return "$kind $side" + if (deciduous) " (de leite)" else ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DentalScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val dao = container.database.toothStateDao()
    val teeth by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val byNumber = remember(teeth) { teeth.associateBy { it.number } }
    var deciduous by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    var clearing by remember { mutableStateOf<ToothStateEntity?>(null) }
    val looks = toothLooks()

    val upper = if (deciduous) DeciduousUpper else PermanentUpper
    val lower = if (deciduous) DeciduousLower else PermanentLower
    val allNumbers = PermanentUpper + PermanentLower + DeciduousUpper + DeciduousLower

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") { CareHeader("Saúde", "Odontologia", ReportArea.ODONTOLOGIA, onBack) }
        item(key = "intro") {
            Text(
                "Toque num dente para dizer como ele está. Dente sem marcação conta como saudável.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "tipo") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !deciduous, onClick = { deciduous = false }, label = { Text("Adulto (permanentes)") })
                FilterChip(selected = deciduous, onClick = { deciduous = true }, label = { Text("De leite") })
            }
        }
        item(key = "arcada") {
            Panel {
                Text("Arcada superior", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                DentalArch(upper, lower, byNumber, looks, onTap = { editing = it })
                Text("Arcada inferior", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth()) {
                    Text("← Direita do paciente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Esquerda →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "legenda") {
            Panel {
                Text("Legenda", style = MaterialTheme.typography.titleMedium)
                Text("Toque numa situação para buscar os dentes com ela.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToothStatus.entries.forEach { s ->
                        val count = if (s == ToothStatus.SAUDAVEL) null else teeth.count { toothStatusOf(it.status) == s }
                        Row(
                            Modifier.clip(MaterialTheme.shapes.small).clickable { query = s.label }.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(looks.getValue(s))
                            Text(" ${s.label}" + if (count != null && count > 0) " ($count)" else "", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        item(key = "busca") { SearchField(query, { query = it }, "Buscar dente (ex.: 16), situação ou observação") }

        if (query.isNotBlank()) {
            val q = query.trim()
            val found = if (q.all { it.isDigit() }) {
                // Número: dentes que começam com ele (ex.: "1" mostra 11 a 18).
                allNumbers.filter { it.toString().startsWith(q) }.sorted()
            } else {
                allNumbers.filter { n ->
                    val t = byNumber[n]
                    val status = t?.let { toothStatusOf(it.status) } ?: ToothStatus.SAUDAVEL
                    matches(q, toothName(n), status.label, t?.notes)
                }
            }
            item(key = "res") { Overline(if (found.size == 1) "1 dente" else "${found.size} dentes") }
            if (found.isEmpty()) item(key = "nada") { EmptyState("Nada encontrado. Busque pelo número do dente (11 a 48) ou por uma situação, como \"cárie\".") }
            items(found, key = { "f$it" }) { n -> ToothRow(n, byNumber[n], looks, onClick = { editing = n }, onClear = { byNumber[n]?.let { clearing = it } }) }
        } else {
            val marked = teeth.filter { toothStatusOf(it.status) != ToothStatus.SAUDAVEL || !it.notes.isNullOrBlank() }.sortedBy { it.number }
            item(key = "lista") { Overline("Dentes com alguma situação (${marked.size})") }
            if (marked.isEmpty()) {
                item(key = "vazio") { Panel { EmptyState("Todos os dentes estão como saudáveis. Toque num dente do desenho para marcar cárie, restauração, canal, implante...") } }
            } else {
                items(marked, key = { "t${it.number}" }) { t -> ToothRow(t.number, t, looks, onClick = { editing = t.number }, onClear = { clearing = t }) }
            }
        }
    }

    editing?.let { n ->
        ToothEditor(
            number = n,
            current = byNumber[n],
            looks = looks,
            onDismiss = { editing = null },
            onSave = { state ->
                editing = null
                container.scope.launch {
                    if (state.status == ToothStatus.SAUDAVEL.name && state.notes.isNullOrBlank()) {
                        // Saudável sem observação: não precisa guardar nada.
                        byNumber[n]?.let { dao.delete(it) }
                    } else {
                        dao.save(state)
                    }
                }
            },
            onClear = { byNumber[n]?.let { clearing = it }; editing = null },
        )
    }
    clearing?.let { t ->
        ConfirmDialog(
            title = "Limpar o dente ${t.number}?",
            text = "A marcação \"${toothStatusOf(t.status).label}\" e a observação saem, e o dente volta a contar como saudável.",
            confirmLabel = "Limpar",
            onConfirm = { container.scope.launch { dao.delete(t) } },
            onDismiss = { clearing = null },
        )
    }
}

@Composable
private fun StatusDot(look: ToothLook, size: Int = 16) {
    var m = Modifier.size(size.dp).clip(CircleShape)
    if (look.fill != null) m = m.background(look.fill)
    Box(m.border(2.dp, look.stroke, CircleShape))
}

@Composable
private fun ToothRow(number: Int, state: ToothStateEntity?, looks: Map<ToothStatus, ToothLook>, onClick: () -> Unit, onClear: () -> Unit) {
    val status = state?.let { toothStatusOf(it.status) } ?: ToothStatus.SAUDAVEL
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(looks.getValue(status), size = 22)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("$number · ${status.label}", fontWeight = FontWeight.Bold)
                Text(toothName(number), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                state?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                state?.updatedEpochDay?.let {
                    Text("Atualizado em ${dayText(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state != null) IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, "Limpar dente $number") }
        }
    }
}

/** Posições dos dentes no desenho, em pixels. */
private class ArchLayout(val centers: Map<Int, Offset>, val radius: Float, val height: Float)

/** Pontos igualmente espaçados ao longo de meia elipse (da esquerda para a direita, por cima). */
private fun pointsAlong(a: Float, b: Float, n: Int): Pair<List<Offset>, Float> {
    val steps = 360
    val samples = (0..steps).map { i ->
        val t = PI - PI * i / steps
        Offset((a * cos(t)).toFloat(), (b * sin(t)).toFloat())
    }
    val cumulative = FloatArray(samples.size)
    for (i in 1 until samples.size) {
        cumulative[i] = cumulative[i - 1] + hypot(samples[i].x - samples[i - 1].x, samples[i].y - samples[i - 1].y)
    }
    val total = cumulative.last()
    val result = ArrayList<Offset>(n)
    var j = 1
    for (k in 0 until n) {
        val target = (k + 0.5f) / n * total
        while (j < samples.lastIndex && cumulative[j] < target) j++
        val span = (cumulative[j] - cumulative[j - 1]).coerceAtLeast(0.0001f)
        val f = ((target - cumulative[j - 1]) / span).coerceIn(0f, 1f)
        val p0 = samples[j - 1]
        val p1 = samples[j]
        result.add(Offset(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f))
    }
    return result to total
}

private fun archLayout(width: Float, upper: List<Int>, lower: List<Int>, density: Float): ArchLayout {
    val pad = 4f * density
    val n = maxOf(upper.size, lower.size)
    var r = 14f * density
    // Ajusta o tamanho do dente ao espaço (duas passadas bastam).
    repeat(2) {
        val a = width / 2f - r - pad
        val (_, length) = pointsAlong(a, a * 1.1f, n)
        r = (length / n * 0.46f).coerceAtMost(24f * density)
    }
    val a = width / 2f - r - pad
    val b = a * 1.1f
    val cx = width / 2f
    val cyUpper = pad + r + b
    val cyLower = cyUpper + 2f * r + 20f * density
    val centers = HashMap<Int, Offset>()
    pointsAlong(a, b, upper.size).first.forEachIndexed { i, p -> centers[upper[i]] = Offset(cx + p.x, cyUpper - p.y) }
    pointsAlong(a, b, lower.size).first.forEachIndexed { i, p -> centers[lower[i]] = Offset(cx + p.x, cyLower + p.y) }
    return ArchLayout(centers, r, cyLower + b + r + pad)
}

/** Desenho das duas arcadas; cada dente é tocável. */
@Composable
private fun DentalArch(
    upper: List<Int>,
    lower: List<Int>,
    byNumber: Map<Int, ToothStateEntity>,
    looks: Map<ToothStatus, ToothLook>,
    onTap: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val layout = remember(widthPx, upper, density.density) { archLayout(widthPx, upper, lower, density.density) }
        val measurer = rememberTextMeasurer()
        // Tamanho do número fixo em relação ao dente (não cresce com a fonte do sistema, para caber).
        val style = TextStyle(fontSize = with(density) { (layout.radius * 0.78f).toSp() }, fontWeight = FontWeight.Bold)
        val labels: Map<Int, TextLayoutResult> = remember(upper, lower, style) {
            (upper + lower).associateWith { measurer.measure(it.toString(), style) }
        }
        val heightDp = with(density) { layout.height.toDp() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(layout) {
                    detectTapGestures { pos ->
                        val hit = layout.centers.minByOrNull { (_, c) -> (c - pos).getDistance() }
                        if (hit != null && (hit.value - pos).getDistance() <= layout.radius * 1.35f) onTap(hit.key)
                    }
                },
        ) {
            layout.centers.forEach { (number, center) ->
                val status = byNumber[number]?.let { toothStatusOf(it.status) } ?: ToothStatus.SAUDAVEL
                drawTooth(center, layout.radius, looks.getValue(status), labels[number])
            }
        }
    }
}

private fun DrawScope.drawTooth(center: Offset, radius: Float, look: ToothLook, label: TextLayoutResult?) {
    val strokeWidth = radius * 0.16f
    if (look.fill != null) drawCircle(look.fill, radius, center)
    drawCircle(
        look.stroke,
        radius - strokeWidth / 2,
        center,
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (look.dashed) PathEffect.dashPathEffect(floatArrayOf(radius * 0.35f, radius * 0.25f), 0f) else null,
        ),
    )
    if (look.cross) {
        val d = radius * 0.62f
        drawLine(look.stroke, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), strokeWidth)
        drawLine(look.stroke, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), strokeWidth)
    }
    if (label != null) {
        drawText(
            label,
            color = look.text,
            topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f),
        )
    }
}

/** Folha para escolher a situação de um dente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToothEditor(
    number: Int,
    current: ToothStateEntity?,
    looks: Map<ToothStatus, ToothLook>,
    onDismiss: () -> Unit,
    onSave: (ToothStateEntity) -> Unit,
    onClear: () -> Unit,
) {
    var status by remember { mutableStateOf(current?.let { toothStatusOf(it.status) } ?: ToothStatus.SAUDAVEL) }
    var notes by remember { mutableStateOf(current?.notes.orEmpty()) }
    var day by remember { mutableStateOf(current?.updatedEpochDay ?: LocalDate.now().toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }

    FormDialog(
        title = "Dente $number",
        onDismiss = onDismiss,
        onSave = { onSave(ToothStateEntity(number = number, status = status.name, notes = notes.trim().ifBlank { null }, updatedEpochDay = day)) },
    ) {
        Text(toothName(number), style = MaterialTheme.typography.titleMedium)
        Text("Situação", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ToothStatus.entries.forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(s.label) },
                    leadingIcon = { StatusDot(looks.getValue(s), size = 14) },
                )
            }
        }
        HelpField(notes, { notes = it }, "Observação", "Ex.: restauração de resina em 2024; dói com gelado (opcional)", singleLine = false)
        Text("Data", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DateRange, null)
            Text(" ${dayText(day)}")
        }
        Text("Quando isso foi visto ou feito. Ex.: o dia da consulta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (current != null) {
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(" Limpar (voltar a saudável)", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (pickDate) DatePick(day, onDismiss = { pickDate = false }, onPick = { day = it })
}
