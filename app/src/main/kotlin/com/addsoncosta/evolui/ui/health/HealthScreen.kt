package com.addsoncosta.evolui.ui.health

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.data.HealthAttachmentEntity
import com.addsoncosta.evolui.data.HealthConditionEntity
import com.addsoncosta.evolui.data.HealthEventEntity
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.data.MenstrualCycleEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.health.HealthReports
import com.addsoncosta.evolui.health.LabCatalog
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.health.care.AnamnesisScreen
import com.addsoncosta.evolui.ui.health.care.CycleScreen
import com.addsoncosta.evolui.ui.health.care.DentalScreen
import com.addsoncosta.evolui.ui.health.care.HealthCalendarScreen
import com.addsoncosta.evolui.ui.health.pressure.PressureScreen
import com.addsoncosta.evolui.ui.health.pressure.pressureHubColor
import com.addsoncosta.evolui.ui.health.pressure.pressureHubSubtitle
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Seção Saúde (aba "Saúde"). A navegação interna é uma pilha de telas guardada em texto
 * ("list:EXAME|detail:12"), para sobreviver a rotação sem mexer na navegação principal.
 */
@Composable
fun HealthScreen(onOpenProgress: () -> Unit, onOpenBadges: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf("") }
    fun push(route: String) { stack = if (stack.isEmpty()) route else "$stack|$route" }
    fun pop() { stack = stack.substringBeforeLast('|', "") }
    fun replaceTop(route: String) { pop(); push(route) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    val top = stack.substringAfterLast('|')
    val parts = top.split(':')
    when (parts[0]) {
        "" -> HealthHub(
            onOpen = { push(it) },
            onOpenProgress = onOpenProgress,
            onOpenBadges = onOpenBadges,
        )
        "calendar" -> HealthCalendarScreen(onBack = { pop() })
        "anamnesis" -> AnamnesisScreen(onBack = { pop() })
        "dental" -> DentalScreen(onBack = { pop() })
        "cycle" -> CycleScreen(onBack = { pop() })
        "pressure" -> PressureScreen(onBack = { pop() })
        "list" -> {
            val type = typeFrom(parts.getOrNull(1))
            RecordListScreen(
                type = type,
                onBack = { pop() },
                onOpen = { push("detail:$it") },
                onAdd = { push("form:${type.name}:0") },
                onCompare = { push("compare:$it") },
                onOpenDental = { push("dental") },
            )
        }
        "detail" -> RecordDetailScreen(
            recordId = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            onBack = { pop() },
            onEdit = { type, id -> push("form:${type.name}:$id") },
            onCompare = { push("compare:$it") },
        )
        "form" -> {
            val type = typeFrom(parts.getOrNull(1))
            val id = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            RecordFormScreen(
                type = type,
                recordId = id,
                onBack = { pop() },
                onSaved = { newId -> if (id == 0L) replaceTop("detail:$newId") else pop() },
            )
        }
        "compare" -> LabCompareScreen(
            initialMode = parts.getOrNull(1) ?: CompareMode.HEMO,
            onBack = { pop() },
            onOpenRecord = { push("detail:$it") },
        )
        else -> HealthHub(onOpen = { push(it) }, onOpenProgress = onOpenProgress, onOpenBadges = onOpenBadges)
    }
}

private fun typeFrom(name: String?): HealthType = HealthType.entries.firstOrNull { it.name == name } ?: HealthType.CONSULTA

/** Um cartão do hub. */
private class HubCard(val title: String, val icon: ImageVector, val color: Color, val subtitle: String, val route: String?, val onClick: (() -> Unit)? = null)

@Composable
private fun HealthHub(onOpen: (String) -> Unit, onOpenProgress: () -> Unit, onOpenBadges: () -> Unit) {
    val container = appContainer()
    val repo = rememberHealthRepository()
    val db = container.database
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profile by remember { container.repository.profile }.collectAsStateWithLifecycle(initialValue = null)
    val records by remember { repo.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthRecordEntity>())
    val attachments by remember { repo.observeAllAttachments() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthAttachmentEntity>())
    val results by remember { repo.observeAllResults() }.collectAsStateWithLifecycle(initialValue = emptyList<LabResultEntity>())
    val events by remember { db.healthEventDao().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthEventEntity>())
    val conditions by remember { db.healthConditionDao().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList<HealthConditionEntity>())
    val cycles by remember { db.menstrualCycleDao().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList<MenstrualCycleEntity>())
    val lastPressure by remember { db.bloodPressureDao().observeLatest() }.collectAsStateWithLifecycle(initialValue = null)

    var query by rememberSaveable { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }

    val byType = remember(records) { records.groupBy { it.type } }
    val today = LocalDate.now().toEpochDay()

    // Índice de busca: tudo o que foi escrito no registro e o texto lido dos anexos.
    val index = remember(records, attachments) {
        val ocr = attachments.groupBy { it.recordId }.mapValues { (_, list) -> list.joinToString(" ") { (it.originalName ?: "") + " " + (it.ocrText ?: "") } }
        records.map { r ->
            val extra = HealthRepository.decodeExtra(r.extra).values.joinToString(" ")
            r to Repository.normalize(listOfNotNull(r.title, r.professional, r.place, r.description, r.status, extra, ocr[r.id]).joinToString(" "))
        }
    }
    val q = Repository.normalize(query)
    val found = remember(index, q) {
        if (q.length < 2) emptyList<HealthRecordEntity>() else {
            val words = q.split(' ').filter { it.isNotBlank() }
            index.filter { (_, text) -> words.all { text.contains(it) } }.map { it.first }
        }
    }

    fun summary(type: HealthType): String {
        val list = byType[type.name].orEmpty()
        if (list.isEmpty()) return "Nenhum registro ainda"
        val last = list.first()
        return "${list.size} ${if (list.size == 1) "registro" else "registros"} · último ${dayText(last.epochDay)}"
    }

    val upcoming = events.count { it.epochDay >= today && !it.done }
    val nextEvent = events.filter { it.epochDay >= today && !it.done }.minByOrNull { it.epochDay }
    val cards = buildList {
        add(HubCard("Calendário", CalendarIcon, Evolui.Neon,
            if (upcoming == 0) "Marque consultas, exames e remédios" else "$upcoming ${if (upcoming == 1) "evento" else "eventos"} · próximo ${nextEvent?.let { dayText(it.epochDay) } ?: ""}",
            "calendar"))
        add(HubCard("Anamnese", HealthType.ANAMNESE.icon(), Evolui.Cyan,
            if (conditions.isEmpty()) "Doenças e histórico de saúde" else "${conditions.size} ${if (conditions.size == 1) "condição" else "condições"}",
            "anamnesis"))
        add(HubCard("Pressão arterial", Icons.Filled.Favorite, pressureHubColor(lastPressure) ?: Evolui.Red, pressureHubSubtitle(lastPressure), "pressure"))
        listOf(HealthType.CONSULTA, HealthType.EXAME, HealthType.BIOIMPEDANCIA, HealthType.MEDICACAO, HealthType.ODONTOLOGIA, HealthType.OFTALMOLOGIA,
            HealthType.NUTRICAO, HealthType.FISIOTERAPIA, HealthType.TERAPIA, HealthType.TRATAMENTO).forEach { t ->
            add(HubCard(t.label, t.icon(), Color.Unspecified, summary(t), "list:${t.name}"))
        }
        if (profile?.sex == Sex.FEMALE) {
            add(HubCard("Ciclo menstrual", Icons.Filled.WaterDrop, Evolui.Red,
                cycles.firstOrNull()?.let { "Última: ${dayText(it.startEpochDay)}" } ?: "Registre e acompanhe o ciclo", "cycle"))
        }
        add(HubCard("Peso e fotos", Icons.Filled.PhotoLibrary, Evolui.Orange, "Gráfico do peso e antes e depois", null, onOpenProgress))
        add(HubCard("Conquistas", Icons.Filled.EmojiEvents, Evolui.Purple, "Suas medalhas no Evolui", null, onOpenBadges))
    }

    val hemoDays = remember(results) { results.filter { it.analyte in LabCatalog.hemogramNames }.map { it.epochDay }.distinct().sorted() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Sua saúde em um só lugar")
                    Text("Saúde", style = MaterialTheme.typography.headlineMedium)
                }
                HealthShareButton(ReportArea.GERAL)
            }
        }
        item {
            SearchField(query, { query = it }, "Buscar em toda a saúde")
            Text(
                "Procura em títulos, médicos, laboratórios, anotações e no texto dos arquivos anexados. Ex.: \"glicose\", \"Sabin\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        if (q.length >= 2) {
            item { Overline("${found.size} ${if (found.size == 1) "resultado" else "resultados"}") }
            if (found.isEmpty()) {
                item { EmptyNote("Nada encontrado", "Tente outra palavra, sem acento ou só o começo dela.") }
            }
            items(found, key = { it.id }) { r ->
                SearchResult(r, onClick = { onOpen(if (r.type == HealthType.ANAMNESE.name) "anamnesis" else "detail:${r.id}") })
            }
            return@LazyColumn
        }

        // Atalho do hemograma, sempre fácil de achar.
        item {
            Panel(Modifier.clickable { onOpen("compare:${CompareMode.HEMO}") }) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Filled.Bloodtype, null, tint = Evolui.Red, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Histórico do hemograma", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hemoDays.isEmpty()) "Cadastre um exame com o hemograma completo para comparar"
                            else "${hemoDays.size} ${if (hemoDays.size == 1) "hemograma" else "hemogramas"} · último ${dayText(hemoDays.last())}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.TableChart, "Comparar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(cards.chunked(2), key = { row -> row.joinToString { it.title } }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    val color = if (c.color == Color.Unspecified) typeFrom(c.route?.substringAfter(':')).color() else c.color
                    AreaCard(c, color, Modifier.weight(1f)) { c.onClick?.invoke() ?: c.route?.let(onOpen) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            Panel {
                Text("Relatórios", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gere um PDF com o resumo da sua saúde: dados, doenças, remédios em uso, consultas, exames com valores fora da faixa, bioimpedância, tratamentos, dentes e próximos eventos. Cada área também tem o próprio relatório (botão de compartilhar no topo da área).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (generating) return@Button
                        generating = true
                        scope.launch {
                            try {
                                sharePdf(context, HealthReports.pdf(context, ReportArea.GERAL))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Não foi possível gerar o relatório agora.", Toast.LENGTH_LONG).show()
                            } finally {
                                generating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (generating) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Filled.PictureAsPdf, null)
                    Text("  Relatório geral (PDF)", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { openPdf(context, HealthReports.pdf(context, ReportArea.GERAL)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Ver o relatório no celular") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    "Privacidade: tudo o que você registra aqui, inclusive os arquivos, fica só neste celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AreaCard(card: HubCard, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 150.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(card.icon, null, tint = color, modifier = Modifier.size(34.dp))
            Text(card.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchResult(r: HealthRecordEntity, onClick: () -> Unit) {
    val type = typeFrom(r.type)
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(type.icon(), null, tint = type.color(), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${type.singular} · ${dayText(r.epochDay)}" + (r.place?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
