package com.addsoncosta.evolui.ui.health.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.HealthConditionEntity
import com.addsoncosta.evolui.data.HealthRecordEntity
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Grupos de doenças/condições. O [key] é gravado em HealthConditionEntity.status. */
internal enum class ConditionGroup(val key: String, val label: String, val singular: String, val about: String) {
    PRE_EXISTENTE("PRE_EXISTENTE", "Pré-existentes", "Pré-existente", "Condições que você já tinha antes (ex.: desde a infância)."),
    ATUAL("ATUAL", "Atuais", "Atual", "O que você tem hoje e está acompanhando."),
    TRATADA("TRATADA", "Tratadas", "Tratada", "Já curadas ou resolvidas."),
}

internal fun conditionGroupOf(status: String): ConditionGroup = ConditionGroup.entries.firstOrNull { it.key == status } ?: ConditionGroup.ATUAL

/** Sugestões ao digitar o nome da doença. */
private val CommonConditions = listOf(
    "Hipertensão", "Diabetes tipo 2", "Diabetes tipo 1", "Pré-diabetes", "Asma", "Bronquite", "Rinite alérgica", "Sinusite",
    "Hipotireoidismo", "Hipertireoidismo", "Colesterol alto", "Triglicerídeos altos", "Obesidade", "Ansiedade", "Depressão",
    "Insônia", "Enxaqueca", "Gastrite", "Refluxo", "Síndrome do intestino irritável", "Apneia do sono", "Artrose", "Artrite",
    "Hérnia de disco", "Dor lombar crônica", "Fibromialgia", "Osteoporose", "Anemia", "Gota", "Esteatose hepática (gordura no fígado)",
    "Cálculo renal (pedra no rim)", "Doença renal crônica", "Arritmia", "Insuficiência cardíaca", "Infarto", "AVC", "Varizes",
    "Psoríase", "Dermatite", "Glaucoma", "Catarata", "Covid-19", "Dengue", "Hepatite", "Endometriose", "Ovário policístico", "Próstata aumentada",
)

private data class AField(
    val key: String,
    val label: String,
    val help: String,
    val options: List<String> = emptyList(),
    val multiline: Boolean = false,
)

private data class ASection(val title: String, val fields: List<AField>)

/** Formulário da anamnese. As chaves são gravadas no JSON do registro; não renomear. */
private val Sections = listOf(
    ASection(
        "Dados básicos",
        listOf(AField("tipo_sanguineo", "Tipo sanguíneo", "Toque para escolher. Está na carteira de doador ou em exames antigos.", listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Não sei"))),
    ),
    ASection(
        "Alergias",
        listOf(
            AField("alergia_medicamentos", "Medicamentos", "Ex.: dipirona, penicilina. Deixe vazio se não tem.", multiline = true),
            AField("alergia_alimentos", "Alimentos", "Ex.: camarão, amendoim, lactose.", multiline = true),
            AField("alergia_outras", "Outras", "Ex.: poeira, látex, picada de abelha.", multiline = true),
        ),
    ),
    ASection(
        "Cirurgias e internações",
        listOf(
            AField("cirurgias", "Cirurgias anteriores", "Ex.: apendicite (2010), vesícula (2018).", multiline = true),
            AField("internacoes", "Internações", "Ex.: pneumonia, 5 dias (2021).", multiline = true),
        ),
    ),
    ASection(
        "Histórico familiar",
        listOf(
            AField("fam_diabetes", "Diabetes na família", "Quem? Ex.: mãe e avó materna."),
            AField("fam_hipertensao", "Pressão alta na família", "Ex.: pai."),
            AField("fam_cancer", "Câncer na família", "Ex.: tio (próstata)."),
            AField("fam_cardiaco", "Doença do coração na família", "Ex.: avô teve infarto aos 60."),
            AField("fam_outros", "Outros casos na família", "Ex.: AVC, Alzheimer, depressão.", multiline = true),
        ),
    ),
    ASection(
        "Hábitos",
        listOf(
            AField("fumo", "Cigarro", "Toque para escolher.", listOf("Nunca fumei", "Ex-fumante", "Fumo")),
            AField("fumo_detalhe", "Detalhes do cigarro", "Ex.: parei em 2015; 10 cigarros por dia."),
            AField("alcool", "Bebida alcoólica", "Toque para escolher.", listOf("Não bebo", "Socialmente", "Toda semana", "Todo dia")),
            AField("atividade", "Atividade física", "Toque para escolher.", listOf("Sedentário", "1–2x por semana", "3–4x por semana", "5x ou mais")),
            AField("sono", "Sono", "Ex.: durmo 6 h, acordo cansado, ronco."),
            AField("alimentacao", "Alimentação", "Ex.: como pouca verdura, muito doce à noite.", multiline = true),
        ),
    ),
    ASection(
        "Medicamentos de uso contínuo",
        listOf(AField("medicamentos", "Remédios que usa sempre", "Ex.: losartana 50 mg de manhã; omeprazol 20 mg.", multiline = true)),
    ),
    ASection(
        "Vacinas",
        listOf(AField("vacinas", "Vacinas", "Ex.: gripe (2026), covid (3 doses), tétano (2020).", multiline = true)),
    ),
    ASection(
        "Observações",
        listOf(AField("observacoes", "Outras informações", "Qualquer coisa que o médico deva saber.", multiline = true)),
    ),
)

private val MapJson = MapSerializer(String.serializer(), String.serializer())

private fun decodeMap(text: String?): Map<String, String> =
    if (text.isNullOrBlank()) emptyMap() else runCatching { Json.decodeFromString(MapJson, text) }.getOrDefault(emptyMap())

/** Resumo legível, gravado na descrição do registro (para relatórios). */
private fun summary(map: Map<String, String>): String = Sections.flatMap { s ->
    s.fields.mapNotNull { f -> map[f.key]?.takeIf { it.isNotBlank() }?.let { "${f.label}: $it" } }
}.joinToString("\n")

@Composable
fun AnamnesisScreen(onBack: () -> Unit) {
    val container = appContainer()
    val recordDao = container.database.healthRecordDao()
    val conditionDao = container.database.healthConditionDao()
    val conditions by remember { conditionDao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())

    val values = remember { mutableStateMapOf<String, String>() }
    var base by remember { mutableStateOf<HealthRecordEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var savedVersion by remember { mutableIntStateOf(0) }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    val mutex = remember { Mutex() }

    var query by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HealthConditionEntity?>(null) }
    var deleting by remember { mutableStateOf<HealthConditionEntity?>(null) }

    // Carrega a anamnese mais recente uma vez.
    LaunchedEffect(Unit) {
        val record = recordDao.observeByType(HealthType.ANAMNESE.name).first().firstOrNull()
        base = record
        values.putAll(decodeMap(record?.extra))
        savedAt = record?.updatedMillis
        loaded = true
    }

    /** Grava o formulário no registro único de anamnese (cria na primeira vez). */
    suspend fun persist(map: Map<String, String>, v: Int) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val clean = map.filterValues { it.isNotBlank() }
            val json = Json.encodeToString(MapJson, clean)
            val record = base?.copy(extra = json, description = summary(clean), epochDay = LocalDate.now().toEpochDay(), updatedMillis = now)
                ?: HealthRecordEntity(
                    type = HealthType.ANAMNESE.name,
                    epochDay = LocalDate.now().toEpochDay(),
                    title = "Anamnese",
                    description = summary(clean),
                    extra = json,
                    createdMillis = now,
                    updatedMillis = now,
                )
            val id = recordDao.save(record)
            base = if (record.id == 0L) record.copy(id = id) else record
            savedAt = now
            if (v > savedVersion) savedVersion = v
        }
    }

    // Salva sozinho, pouco depois de parar de digitar.
    LaunchedEffect(version) {
        if (version > savedVersion) {
            delay(900)
            persist(values.toMap(), version)
        }
    }
    // Ao sair da tela com algo não salvo, salva em segundo plano.
    DisposableEffect(Unit) {
        onDispose {
            if (loaded && version > savedVersion) {
                val snapshot = values.toMap()
                val v = version
                container.scope.launch { persist(snapshot, v) }
            }
        }
    }
    BackHandler(onBack = onBack)

    val updateField: (String, String) -> Unit = { k, v ->
        values[k] = v
        version++
    }
    val searching = query.isNotBlank()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { CareHeader("Saúde", "Anamnese", ReportArea.ANAMNESE, onBack) }
            item(key = "intro") {
                Text(
                    "Seu histórico de saúde num lugar só, para mostrar ao médico. Preencha só o que souber; tudo é salvo sozinho.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "busca") { SearchField(query, { query = it }, "Buscar doença, alergia, remédio...") }
            if (!searching) {
                item(key = "abas") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !showForm, onClick = { showForm = false }, label = { Text("Doenças (${conditions.size})") })
                        FilterChip(selected = showForm, onClick = { showForm = true }, label = { Text("Formulário") })
                    }
                }
            }

            // Doenças e condições.
            if (searching || !showForm) {
                val filtered = conditions.filter { matches(query, it.name, it.notes, it.sinceText, conditionGroupOf(it.status).label) }
                if (searching) item(key = "cond_res") { Overline("Doenças e condições (${filtered.size})") }
                if (!searching && conditions.isEmpty()) {
                    item(key = "cond_vazio") {
                        Panel {
                            EmptyState("Nenhuma doença registrada. Toque em + para adicionar, por exemplo, \"Hipertensão\" ou \"Asma\". Se não tem nenhuma, ótimo — deixe assim.")
                        }
                    }
                }
                ConditionGroup.entries.forEach { group ->
                    val list = filtered.filter { conditionGroupOf(it.status) == group }
                    if (!searching && conditions.isNotEmpty()) {
                        item(key = "g_${group.key}") {
                            Column {
                                Overline("${group.label} (${list.size})")
                                Text(group.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(list, key = { "c${it.id}" }) { c ->
                        ConditionRow(
                            condition = c,
                            onClick = { editing = c },
                            onMove = { g -> container.scope.launch { conditionDao.save(c.copy(status = g.key)) } },
                            onDelete = { deleting = c },
                        )
                    }
                }
            }

            // Formulário.
            if (loaded && (searching || showForm)) {
                Sections.forEach { section ->
                    val fields = if (searching) section.fields.filter { f -> matches(query, section.title, f.label, values[f.key]) } else section.fields
                    if (fields.isNotEmpty()) {
                        item(key = "s_${section.title}") {
                            Panel {
                                Text(section.title, style = MaterialTheme.typography.titleMedium)
                                fields.forEach { f -> FormField(f, values[f.key].orEmpty()) { updateField(f.key, it) } }
                            }
                        }
                    }
                }
                item(key = "salvo") {
                    val at = savedAt
                    Text(
                        when {
                            version > savedVersion -> "Salvando..."
                            at != null -> "Salvo automaticamente às " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(TimeFormat)
                            else -> "As respostas são salvas sozinhas enquanto você preenche."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!showForm || searching) {
            ExtendedFloatingActionButton(
                onClick = { editing = HealthConditionEntity(name = "", status = ConditionGroup.ATUAL.key) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Doença") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    editing?.let { c ->
        ConditionEditor(
            initial = c,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                container.scope.launch { conditionDao.save(updated) }
            },
            onDelete = { editing = null; deleting = c },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "Apagar \"${c.name}\"?",
            text = "A doença sai da sua anamnese. Isso não pode ser desfeito.",
            confirmLabel = "Apagar",
            onConfirm = { container.scope.launch { conditionDao.delete(c) } },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormField(field: AField, value: String, onChange: (String) -> Unit) {
    if (field.options.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(field.label, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                field.options.forEach { o ->
                    // Tocar de novo desmarca.
                    FilterChip(selected = value == o, onClick = { onChange(if (value == o) "" else o) }, label = { Text(o) })
                }
            }
            Text(field.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        HelpField(value, onChange, field.label, field.help, singleLine = !field.multiline)
    }
}

@Composable
private fun ConditionRow(
    condition: HealthConditionEntity,
    onClick: () -> Unit,
    onMove: (ConditionGroup) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val group = conditionGroupOf(condition.status)
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(condition.name, fontWeight = FontWeight.Bold)
                val details = listOfNotNull(
                    group.singular,
                    condition.sinceText?.takeIf { it.isNotBlank() }?.let { "desde $it" },
                ).joinToString(" · ")
                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                condition.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais opções") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ConditionGroup.entries.filter { it != group }.forEach { g ->
                        DropdownMenuItem(text = { Text("Mover para ${g.label}") }, onClick = { menu = false; onMove(g) })
                    }
                    DropdownMenuItem(
                        text = { Text("Apagar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionEditor(
    initial: HealthConditionEntity,
    onDismiss: () -> Unit,
    onSave: (HealthConditionEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var group by remember { mutableStateOf(conditionGroupOf(initial.status)) }
    var since by remember { mutableStateOf(initial.sinceText.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    val valid = name.isNotBlank()
    val save = {
        if (valid) {
            onSave(
                initial.copy(
                    name = name.trim(),
                    status = group.key,
                    sinceText = since.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                ),
            )
        }
    }

    FormDialog(title = if (isNew) "Nova doença ou condição" else "Editar", onDismiss = onDismiss, onSave = save, saveEnabled = valid) {
        HelpField(name, { name = it }, "Nome", "Ex.: Hipertensão. Comece a digitar para ver sugestões.")
        val suggestions = if (name.isBlank()) CommonConditions.take(10) else CommonConditions.filter { it != name && matches(name, it) }.take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> AssistChip(onClick = { name = s }, label = { Text(s) }) }
            }
        }
        Text("Situação", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionGroup.entries.forEach { g ->
                FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) })
            }
        }
        Text(group.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpField(since, { since = it }, "Desde quando", "Ex.: 2019, ou \"desde criança\" (opcional)")
        HelpField(notes, { notes = it }, "Observações", "Ex.: controlada com remédio; tratada em 2022 (opcional)", singleLine = false)
        if (!isNew) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(" Apagar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
