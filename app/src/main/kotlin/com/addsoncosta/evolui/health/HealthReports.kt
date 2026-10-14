package com.addsoncosta.evolui.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.addsoncosta.evolui.EvoluiApp
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.Profile
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
import com.addsoncosta.evolui.data.ToothStateEntity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Partes da seção Saúde que têm relatório próprio. */
enum class ReportArea(val label: String) {
    GERAL("Relatório geral de saúde"),
    CONSULTA("Consultas"),
    EXAME("Exames"),
    MEDICACAO("Medicação"),
    ODONTOLOGIA("Odontologia"),
    NUTRICAO("Nutrição"),
    FISIOTERAPIA("Fisioterapia"),
    TERAPIA("Terapia"),
    TRATAMENTO("Tratamentos"),
    BIOIMPEDANCIA("Bioimpedância"),
    ANAMNESE("Anamnese"),
    CALENDARIO("Calendário de saúde"),
    CICLO("Ciclo menstrual"),
}

// ---------- Modelo do relatório (o mesmo conteúdo vira PDF, texto e imagem) ----------

private sealed interface Item
private class Para(val text: String, val warn: Boolean = false, val muted: Boolean = false) : Item
private class Sub(val text: String) : Item
private class KV(val key: String, val value: String) : Item
private enum class TableKind { VALUES, COMPARE, PLAIN }
private class Table(
    val header: List<String>,
    val rows: List<List<String>>,
    val warn: List<List<Boolean>>,
    val kind: TableKind,
    val firstWeight: Float = 2f,
) : Item

private class Section(val heading: String, val items: List<Item>)
private class Report(val title: String, val patient: String, val sections: List<Section>)

private class Data(
    val profile: Profile?,
    val weight: Double?,
    val records: List<HealthRecordEntity>,
    val results: List<LabResultEntity>,
    val attachments: List<HealthAttachmentEntity>,
    val conditions: List<HealthConditionEntity>,
    val teeth: List<ToothStateEntity>,
    val events: List<HealthEventEntity>,
    val cycles: List<MenstrualCycleEntity>,
) {
    val sex: Sex? get() = profile?.sex
    fun of(type: HealthType) = records.filter { it.type == type.name }.sortedWith(compareByDescending<HealthRecordEntity> { it.epochDay }.thenByDescending { it.id })
    fun resultsOf(recordId: Long) = results.filter { it.recordId == recordId }.sortedBy { LabCatalog.order(it.analyte) }
}

private val Br: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val BrShort: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
private fun day(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(Br)
private const val DISCLAIMER = "Este relatório foi gerado pelo próprio paciente no app Evolui e não substitui avaliação profissional."

/** Quebra o texto em linhas que cabem na largura. */
private fun wrap(text: String, p: Paint, width: Float): List<String> {
    val out = mutableListOf<String>()
    for (raw in text.split('\n')) {
        if (raw.isBlank()) { out += ""; continue }
        var line = ""
        for (word in raw.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= width) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) out += line
            var w = word
            while (p.measureText(w) > width && w.isNotEmpty()) {
                val n = p.breakText(w, true, width, null).coerceAtLeast(1)
                out += w.substring(0, n)
                w = w.substring(n)
            }
            line = w
        }
        if (line.isNotEmpty()) out += line
    }
    return out
}

/** Relatórios da seção Saúde em PDF, texto e imagem. */
object HealthReports {
    /** Gera o PDF da área (na pasta de compartilhamento) e devolve o arquivo. */
    suspend fun pdf(context: Context, area: ReportArea): File {
        val report = build(load(context), area)
        return withContext(Dispatchers.IO) { writePdf(context, report, "evolui_saude_${area.name.lowercase()}.pdf") }
    }

    /** Resumo em texto simples da área, para WhatsApp ou copiar. */
    suspend fun text(context: Context, area: ReportArea): String = renderText(build(load(context), area))

    /** Cartão-resumo da área em imagem (1080 px de largura). */
    suspend fun image(context: Context, area: ReportArea): Bitmap {
        val report = build(load(context), area)
        return withContext(Dispatchers.Default) { renderImage(report) }
    }

    /** PDF de um registro só (uma consulta, um exame...). */
    suspend fun recordPdf(context: Context, recordId: Long): File {
        val report = buildRecord(load(context), recordId)
        return withContext(Dispatchers.IO) { writePdf(context, report, "evolui_registro_$recordId.pdf") }
    }

    suspend fun recordText(context: Context, recordId: Long): String = renderText(buildRecord(load(context), recordId))

    suspend fun recordImage(context: Context, recordId: Long): Bitmap {
        val report = buildRecord(load(context), recordId)
        return withContext(Dispatchers.Default) { renderImage(report) }
    }

    // ---------- Dados ----------

    private suspend fun load(context: Context): Data = withContext(Dispatchers.IO) {
        val c = (context.applicationContext as EvoluiApp).container
        val db = c.database
        Data(
            profile = db.profileDao().get()?.toProfile(),
            weight = runCatching { c.repository.latestWeight(LocalDate.now()).first() }.getOrNull(),
            records = db.healthRecordDao().all(),
            results = db.labResultDao().all(),
            attachments = db.healthAttachmentDao().all(),
            conditions = db.healthConditionDao().all(),
            teeth = db.toothStateDao().all(),
            events = db.healthEventDao().all(),
            cycles = db.menstrualCycleDao().all(),
        )
    }

    private fun patientLine(d: Data): String {
        val p = d.profile ?: return "Emitido em ${LocalDate.now().format(Br)}"
        val age = EnergyCalculator.ageOn(p.birthDate, LocalDate.now())
        return "${p.name} · $age anos · emitido em ${LocalDate.now().format(Br)}"
    }

    private fun build(d: Data, area: ReportArea): Report {
        val sections: List<Section> = when (area) {
            ReportArea.GERAL -> general(d)
            ReportArea.ANAMNESE -> listOf(anamnesis(d))
            ReportArea.CALENDARIO -> calendar(d)
            ReportArea.CICLO -> listOf(cycle(d))
            ReportArea.ODONTOLOGIA -> listOf(teeth(d, onlyProblems = false)) + history(d, HealthType.ODONTOLOGIA)
            ReportArea.EXAME -> compareSections(d, HealthType.EXAME) + history(d, HealthType.EXAME)
            ReportArea.BIOIMPEDANCIA -> compareSections(d, HealthType.BIOIMPEDANCIA) + history(d, HealthType.BIOIMPEDANCIA)
            ReportArea.MEDICACAO -> listOf(medsInUse(d)) + history(d, HealthType.MEDICACAO)
            ReportArea.CONSULTA -> history(d, HealthType.CONSULTA)
            ReportArea.NUTRICAO -> history(d, HealthType.NUTRICAO)
            ReportArea.FISIOTERAPIA -> history(d, HealthType.FISIOTERAPIA)
            ReportArea.TERAPIA -> history(d, HealthType.TERAPIA)
            ReportArea.TRATAMENTO -> history(d, HealthType.TRATAMENTO)
        }
        return Report(area.label, patientLine(d), sections)
    }

    private fun buildRecord(d: Data, recordId: Long): Report {
        val r = d.records.firstOrNull { it.id == recordId }
            ?: return Report("Registro de saúde", patientLine(d), listOf(Section("Registro", listOf(Para("Registro não encontrado.")))))
        val type = HealthFields.typeOf(r.type)
        return Report(type?.singular ?: "Registro de saúde", patientLine(d), listOf(Section(type?.label ?: "Registro", recordItems(d, r))))
    }

    // ---------- Seções ----------

    private fun general(d: Data): List<Section> {
        val out = mutableListOf<Section>()
        val p = d.profile
        if (p != null) {
            val items = mutableListOf<Item>(
                KV("Nome", p.name),
                KV("Idade", "${EnergyCalculator.ageOn(p.birthDate, LocalDate.now())} anos"),
                KV("Sexo", if (p.sex == Sex.FEMALE) "Feminino" else "Masculino"),
                KV("Altura", "${LabCatalog.num(p.heightCm)} cm"),
            )
            d.weight?.let { w ->
                items += KV("Peso atual", "${LabCatalog.num(w)} kg")
                val bmi = EnergyCalculator.bmi(w, p.heightCm)
                items += KV("IMC", "${LabCatalog.num(bmi)} (${EnergyCalculator.bmiLabel(bmi)})")
            }
            out += Section("Dados pessoais", items)
        }
        out += anamnesis(d)
        out += medsInUse(d)

        val consults = d.of(HealthType.CONSULTA).take(5)
        out += Section("Últimas consultas", if (consults.isEmpty()) listOf(Para("Nenhuma consulta registrada.", muted = true)) else consults.flatMap { r ->
            val e = HealthRepository.decodeExtra(r.extra)
            buildList<Item> {
                add(Sub("${day(r.epochDay)} — ${r.title}"))
                val who = listOfNotNull(r.professional?.takeIf { it.isNotBlank() }, e["especialidade"], r.place?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (who.isNotBlank()) add(Para(who, muted = true))
                e["diagnostico"]?.let { add(KV("Diagnóstico e orientações", it)) }
            }
        })

        val exams = d.of(HealthType.EXAME).take(3)
        out += Section("Exames recentes", if (exams.isEmpty()) listOf(Para("Nenhum exame registrado.", muted = true)) else exams.flatMap { r ->
            val res = d.resultsOf(r.id)
            val bad = res.filter { LabCatalog.status(it, d.sex) != 0 }
            buildList<Item> {
                add(Sub("${day(r.epochDay)} — ${r.title}" + (r.place?.takeIf { it.isNotBlank() }?.let { " (laboratório $it)" } ?: "")))
                when {
                    res.isEmpty() -> add(Para("Sem valores cadastrados.", muted = true))
                    bad.isEmpty() -> add(Para("Todos os ${res.size} valores dentro da referência."))
                    else -> {
                        add(Para("Valores fora da faixa de referência:", warn = true))
                        add(valuesTable(bad, d.sex))
                    }
                }
            }
        })

        d.of(HealthType.BIOIMPEDANCIA).firstOrNull()?.let { r ->
            val res = d.resultsOf(r.id)
            if (res.isNotEmpty()) out += Section("Bioimpedância mais recente (${day(r.epochDay)})", listOf(valuesTable(res, d.sex)))
        }

        val ongoingTypes = listOf(HealthType.TRATAMENTO, HealthType.TERAPIA, HealthType.FISIOTERAPIA, HealthType.NUTRICAO)
        val ongoing = ongoingTypes.flatMap { t -> d.of(t).filter { it.status == null || it.status == "Em andamento" }.take(3).map { t to it } }
        out += Section("Tratamentos em andamento", if (ongoing.isEmpty()) listOf(Para("Nenhum tratamento em andamento.", muted = true)) else ongoing.map { (t, r) ->
            val e = HealthRepository.decodeExtra(r.extra)
            val extra = listOfNotNull(r.professional?.takeIf { it.isNotBlank() }, e["frequencia"], e["objetivo"]?.let { "objetivo: $it" }).joinToString(" · ")
            Para("${t.singular}: ${r.title} (desde ${day(r.epochDay)})" + if (extra.isNotBlank()) " — $extra" else "")
        })

        out += teeth(d, onlyProblems = true)

        val today = LocalDate.now().toEpochDay()
        val next = d.events.filter { it.epochDay >= today && !it.done }.sortedWith(compareBy({ it.epochDay }, { it.minuteOfDay ?: -1 })).take(10)
        out += Section("Próximos eventos", if (next.isEmpty()) listOf(Para("Nenhum evento marcado.", muted = true)) else next.map { eventPara(it) })

        if (d.cycles.isNotEmpty()) out += cycle(d)
        return out
    }

    private fun anamnesis(d: Data): Section {
        val items = mutableListOf<Item>()
        // Formulário da anamnese: a descrição já vem com "Rótulo: valor" em cada linha.
        d.of(HealthType.ANAMNESE).firstOrNull()?.description?.takeIf { it.isNotBlank() }?.let { text ->
            items += Sub("Formulário")
            text.lines().filter { it.isNotBlank() }.forEach { line ->
                val i = line.indexOf(": ")
                items += if (i > 0) KV(line.substring(0, i), line.substring(i + 2)) else Para(line)
            }
        }
        if (d.conditions.isEmpty()) {
            items += Para("Nenhuma doença ou condição registrada.", muted = true)
            return Section("Anamnese", items)
        }
        val groups = listOf("ATUAL" to "Condições atuais", "PRE_EXISTENTE" to "Doenças pré-existentes", "TRATADA" to "Já tratadas")
        groups.forEach { (key, label) ->
            val list = d.conditions.filter { it.status == key }.sortedBy { it.name }
            if (list.isNotEmpty()) {
                items += Sub(label)
                list.forEach { c ->
                    val more = listOfNotNull(c.sinceText?.takeIf { it.isNotBlank() }?.let { "desde $it" }, c.notes?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    items += Para("• ${c.name}" + if (more.isNotBlank()) " — $more" else "")
                }
            }
        }
        val others = d.conditions.filter { c -> groups.none { it.first == c.status } }
        if (others.isNotEmpty()) {
            items += Sub("Outras")
            others.forEach { items += Para("• ${it.name}") }
        }
        return Section("Anamnese", items)
    }

    private fun isInUse(r: HealthRecordEntity): Boolean {
        if (r.status != null) return r.status == "Em uso"
        val end = HealthRepository.parseDate(HealthRepository.decodeExtra(r.extra)["fim"])
        return end == null || !end.isBefore(LocalDate.now())
    }

    private fun medsInUse(d: Data): Section {
        val meds = d.of(HealthType.MEDICACAO).filter { isInUse(it) }
        return Section("Medicações em uso", if (meds.isEmpty()) listOf(Para("Nenhuma medicação em uso registrada.", muted = true)) else meds.map { r ->
            val e = HealthRepository.decodeExtra(r.extra)
            val dose = listOfNotNull(e["dose"], e["frequencia"]).joinToString(", ")
            Para("• ${r.title}" + (if (dose.isNotBlank()) " — $dose" else "") + (e["motivo"]?.let { " (para $it)" } ?: ""))
        })
    }

    private fun healthyTooth(status: String): Boolean {
        val s = Repository.normalize(status).replace('_', ' ')
        return s.isBlank() || listOf("saudavel", "higido", "normal", "ok", "healthy", "sadio", "sem alteracao").any { s == it || s.startsWith(it) }
    }

    private val accents = mapOf("CARIE" to "Cárie", "PROTESE" to "Prótese", "SAUDAVEL" to "Saudável", "MEDIO" to "Médio")

    private fun pretty(s: String): String = accents[s]
        ?: if (s == s.uppercase()) s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } else s

    private fun teeth(d: Data, onlyProblems: Boolean): Section {
        val list = d.teeth.filter { !onlyProblems || !healthyTooth(it.status) || !it.notes.isNullOrBlank() }.sortedBy { it.number }
        if (list.isEmpty()) return Section("Arcada dentária", listOf(Para(if (onlyProblems) "Nenhum dente com problema registrado." else "Nenhum dente descrito ainda.", muted = true)))
        return Section("Arcada dentária", listOf(
            Table(
                header = listOf("Dente", "Situação", "Observações"),
                rows = list.map { listOf(it.number.toString(), pretty(it.status), it.notes.orEmpty()) },
                warn = list.map { listOf(false, !healthyTooth(it.status), false) },
                kind = TableKind.PLAIN,
                firstWeight = 0.7f,
            ),
        ))
    }

    private fun eventPara(e: HealthEventEntity): Para {
        val time = e.minuteOfDay?.let { " às %02d:%02d".format(it / 60, it % 60) } ?: ""
        val type = HealthFields.typeOf(e.type)?.singular ?: pretty(e.type)
        val more = listOfNotNull(e.place?.takeIf { it.isNotBlank() }, e.notes?.takeIf { it.isNotBlank() }).joinToString(" · ")
        return Para("• ${day(e.epochDay)}$time — $type: ${e.title}" + (if (more.isNotBlank()) " ($more)" else "") + if (e.done) " ✓" else "")
    }

    private fun calendar(d: Data): List<Section> {
        val today = LocalDate.now().toEpochDay()
        val next = d.events.filter { it.epochDay >= today }.sortedWith(compareBy({ it.epochDay }, { it.minuteOfDay ?: -1 }))
        val past = d.events.filter { it.epochDay < today }.sortedWith(compareByDescending<HealthEventEntity> { it.epochDay }.thenByDescending { it.minuteOfDay ?: -1 }).take(60)
        return listOf(
            Section("Próximos eventos", if (next.isEmpty()) listOf(Para("Nenhum evento marcado.", muted = true)) else next.map { eventPara(it) }),
            Section("Eventos passados", if (past.isEmpty()) listOf(Para("Nenhum evento passado.", muted = true)) else past.map { eventPara(it) }),
        )
    }

    private fun cycle(d: Data): Section {
        val list = d.cycles.sortedBy { it.startEpochDay }
        if (list.isEmpty()) return Section("Ciclo menstrual", listOf(Para("Nenhum ciclo registrado.", muted = true)))
        val lengths = list.zipWithNext { a, b -> (b.startEpochDay - a.startEpochDay).toInt() }.filter { it in 15..60 }
        val durations = list.mapNotNull { c -> c.endEpochDay?.let { (it - c.startEpochDay + 1).toInt() } }.filter { it in 1..15 }
        val items = mutableListOf<Item>()
        items += KV("Ciclos registrados", list.size.toString())
        if (lengths.isNotEmpty()) {
            val avg = lengths.average()
            items += KV("Duração média do ciclo", "${avg.roundToInt()} dias (de ${lengths.min()} a ${lengths.max()})")
            items += KV("Próxima menstruação prevista", LocalDate.ofEpochDay(list.last().startEpochDay + avg.roundToInt()).format(Br))
        }
        if (durations.isNotEmpty()) items += KV("Duração média da menstruação", "${durations.average().roundToInt()} dias")
        items += Sub("Histórico")
        list.reversed().take(24).forEach { c ->
            val end = c.endEpochDay?.let { " a ${day(it)}" } ?: ""
            val more = listOfNotNull(c.flow?.let { "fluxo ${pretty(it).lowercase()}" }, c.symptoms?.takeIf { it.isNotBlank() }, c.notes?.takeIf { it.isNotBlank() }).joinToString(" · ")
            items += Para("• ${day(c.startEpochDay)}$end" + if (more.isNotBlank()) " — $more" else "")
        }
        return Section("Ciclo menstrual", items)
    }

    private fun history(d: Data, type: HealthType, includeEmpty: Boolean = true): List<Section> {
        val list = d.of(type)
        if (list.isEmpty()) return if (includeEmpty) listOf(Section("Histórico", listOf(Para("Nenhum registro ainda.", muted = true)))) else emptyList()
        val first = list.last().epochDay
        val last = list.first().epochDay
        val summary = Para("${list.size} ${if (list.size == 1) "registro" else "registros"}" + if (first != last) ", de ${day(first)} a ${day(last)}" else ", em ${day(last)}", muted = true)
        return listOf(Section("Histórico de ${type.label.lowercase()}", listOf(summary) + list.flatMap { recordItems(d, it) }))
    }

    private fun recordItems(d: Data, r: HealthRecordEntity): List<Item> {
        val type = HealthFields.typeOf(r.type)
        val spec = type?.let { HealthFields.spec(it) }
        val items = mutableListOf<Item>()
        items += Sub("${day(r.epochDay)} — ${r.title}")
        r.professional?.takeIf { it.isNotBlank() }?.let { items += KV(spec?.professionalLabel ?: "Profissional", it) }
        r.place?.takeIf { it.isNotBlank() }?.let { items += KV(spec?.placeLabel?.removeSuffix(" *") ?: "Local", it) }
        r.status?.takeIf { it.isNotBlank() }?.let { items += KV("Situação", it) }
        if (type != HealthType.ANAMNESE) HealthRepository.decodeExtra(r.extra).forEach { (k, v) ->
            val value = if (HealthFields.kind(type, k) == FieldKind.DATE) HealthRepository.parseDate(v)?.format(Br) ?: v else v
            items += KV(HealthFields.label(type, k), value)
        }
        r.description?.takeIf { it.isNotBlank() }?.let { items += Para(it) }
        val res = d.resultsOf(r.id)
        if (res.isNotEmpty()) items += valuesTable(res, d.sex)
        val files = d.attachments.filter { it.recordId == r.id }
        if (files.isNotEmpty()) items += Para("Anexos: " + files.joinToString(", ") { it.originalName ?: it.fileName }, muted = true)
        return items
    }

    private fun valuesTable(res: List<LabResultEntity>, sex: Sex?): Table = Table(
        header = listOf("Item", "Resultado", "Referência"),
        rows = res.map { r ->
            val s = LabCatalog.status(r, sex)
            val mark = when (s) { 1 -> " ↑"; -1 -> " ↓"; else -> "" }
            listOf(r.analyte, LabCatalog.num(r.value) + (r.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "") + mark, LabCatalog.refLabel(r, sex) ?: "—")
        },
        warn = res.map { listOf(false, LabCatalog.status(it, sex) != 0, false) },
        kind = TableKind.VALUES,
    )

    /** Tabelas comparativas: itens nas linhas, datas nas colunas (de 5 em 5 datas). */
    private fun compareSections(d: Data, type: HealthType): List<Section> {
        val recs = d.of(type).reversed()
        val ids = recs.map { it.id }.toSet()
        val res = d.results.filter { it.recordId in ids }
        if (res.isEmpty()) return emptyList()
        val sets = if (type == HealthType.EXAME) {
            listOf(
                "Hemograma completo — comparação" to res.filter { it.analyte in LabCatalog.hemogramNames },
                "Outros exames — comparação" to res.filter { it.analyte !in LabCatalog.hemogramNames },
            )
        } else listOf("Comparação entre as medições" to res)
        return sets.filter { it.second.isNotEmpty() }.map { (title, list) -> Section(title, compareTables(recs, list, d.sex)) }
    }

    private fun compareTables(recs: List<HealthRecordEntity>, res: List<LabResultEntity>, sex: Sex?): List<Item> {
        val byRecord = res.groupBy { it.recordId }
        val cols = recs.filter { byRecord.containsKey(it.id) }
        val analytes = res.map { it.analyte }.distinct().sortedWith(compareBy({ LabCatalog.order(it) }, { it }))
        val items = mutableListOf<Item>()
        val chunks = cols.chunked(5)
        chunks.forEachIndexed { ci, chunk ->
            val rows = mutableListOf<List<String>>()
            val warns = mutableListOf<List<Boolean>>()
            for (a in analytes) {
                val cells = mutableListOf(a + (LabCatalog.find(a)?.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
                val w = mutableListOf(false)
                var any = false
                for (rec in chunk) {
                    val r = byRecord[rec.id]?.firstOrNull { it.analyte == a }
                    if (r == null) { cells += "—"; w += false; continue }
                    any = true
                    val prev = cols.takeWhile { it.id != rec.id }.reversed().firstNotNullOfOrNull { p -> byRecord[p.id]?.firstOrNull { it.analyte == a } }
                    val arrow = when {
                        prev == null -> ""
                        r.value > prev.value -> " ↑"
                        r.value < prev.value -> " ↓"
                        else -> " ="
                    }
                    cells += LabCatalog.num(r.value) + arrow
                    w += LabCatalog.status(r, sex) != 0
                }
                if (any) { rows += cells; warns += w }
            }
            if (chunks.size > 1) items += Para("Datas ${ci * 5 + 1} a ${ci * 5 + chunk.size} de ${cols.size}", muted = true)
            items += Table(listOf("Item") + chunk.map { LocalDate.ofEpochDay(it.epochDay).format(BrShort) }, rows, warns, TableKind.COMPARE, firstWeight = 2.2f)
        }
        items += Para("Valores em vermelho estão fora da faixa de referência. ↑ subiu e ↓ desceu em relação ao exame anterior.", muted = true)
        return items
    }

    // ---------- Texto ----------

    private fun renderText(r: Report): String = buildString {
        appendLine("🩺 Evolui — ${r.title}")
        appendLine(r.patient)
        r.sections.forEach { s ->
            appendLine()
            appendLine("▸ ${s.heading.uppercase()}")
            s.items.forEach { item ->
                when (item) {
                    is Sub -> appendLine("*${item.text}*")
                    is Para -> appendLine(item.text + if (item.warn) " ⚠️" else "")
                    is KV -> appendLine("${item.key}: ${item.value}")
                    is Table -> item.rows.forEachIndexed { i, row ->
                        val warn = item.warn.getOrNull(i)?.any { it } == true
                        val line = when (item.kind) {
                            TableKind.VALUES -> "• ${row[0]}: ${row.getOrElse(1) { "" }}" + (row.getOrNull(2)?.takeIf { it != "—" }?.let { " (ref. $it)" } ?: "")
                            TableKind.COMPARE -> "• ${row[0]}: " + (1 until row.size).filter { row[it] != "—" }.joinToString(" → ") { "${row[it]} (${item.header[it]})" }
                            TableKind.PLAIN -> "• " + row.filter { it.isNotBlank() }.joinToString(" · ")
                        }
                        appendLine(line + if (warn) " ⚠️" else "")
                    }
                }
            }
        }
        appendLine()
        append(DISCLAIMER)
    }

    // ---------- PDF ----------

    private fun writePdf(context: Context, report: Report, fileName: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName)
        val doc = PdfDocument()
        try {
            val w = PdfWriter(doc, report)
            w.start()
            report.sections.forEach { w.section(it) }
            w.ensure(40f)
            w.y += 8f
            w.paragraph(DISCLAIMER, w.muted)
            w.finish()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    private class PdfWriter(val doc: PdfDocument, val report: Report) {
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val contentW = pageW - margin * 2
        private var page: PdfDocument.Page? = null
        private var number = 0
        lateinit var canvas: Canvas
        var y = 0f

        private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        val accent = 0xFF0E7A45.toInt()
        val titleP = paint(18f, 0xFF111816.toInt(), true)
        val h1 = paint(13f, accent, true)
        val subP = paint(10.5f, 0xFF111816.toInt(), true)
        val body = paint(10f, 0xFF222222.toInt())
        val bodyBold = paint(10f, 0xFF222222.toInt(), true)
        val muted = paint(8.5f, 0xFF666666.toInt())
        val warnP = paint(10f, 0xFFC62828.toInt(), true)
        val headerP = paint(9f, accent, true)
        private val fill = Paint().apply { style = Paint.Style.FILL }

        fun start() {
            newPage()
            canvas.drawText(report.title, margin, y + 18f, titleP)
            y += 26f
            canvas.drawText(report.patient, margin, y + 10f, body)
            y += 20f
        }

        private fun newPage() {
            finish()
            number++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, number).create())
            page = p
            canvas = p.canvas
            canvas.drawText("Evolui — Relatório de saúde", margin, margin, headerP)
            val line = Paint().apply { color = accent; strokeWidth = 1f }
            canvas.drawLine(margin, margin + 6f, pageW - margin, margin + 6f, line)
            y = margin + 20f
        }

        fun finish() {
            val p = page ?: return
            canvas.drawText("Página $number · Não substitui avaliação profissional", margin, pageH - 24f, muted)
            doc.finishPage(p)
            page = null
        }

        fun ensure(h: Float) {
            if (y + h > pageH - margin - 10f) newPage()
        }

        fun lineH(p: Paint) = p.textSize * 1.35f

        fun paragraph(text: String, p: Paint, indent: Float = 0f) {
            wrap(text, p, contentW - indent).forEach { l ->
                ensure(lineH(p))
                canvas.drawText(l, margin + indent, y + p.textSize, p)
                y += lineH(p)
            }
        }

        fun section(s: Section) {
            ensure(lineH(h1) + 30f)
            y += 10f
            canvas.drawText(s.heading, margin, y + h1.textSize, h1)
            y += lineH(h1) + 4f
            s.items.forEach { item ->
                when (item) {
                    is Sub -> { y += 4f; paragraph(item.text, subP) }
                    is Para -> paragraph(item.text, if (item.warn) warnP else if (item.muted) muted else body)
                    is KV -> keyValue(item)
                    is Table -> table(item)
                }
            }
        }

        private fun keyValue(kv: KV) {
            val keyText = "${kv.key}: "
            val kw = bodyBold.measureText(keyText)
            if (kw > contentW * 0.45f) {
                paragraph(keyText, bodyBold)
                paragraph(kv.value, body, 12f)
                return
            }
            val lines = wrap(kv.value, body, contentW - kw)
            lines.forEachIndexed { i, l ->
                ensure(lineH(body))
                if (i == 0) canvas.drawText(keyText, margin, y + body.textSize, bodyBold)
                canvas.drawText(l, margin + kw, y + body.textSize, body)
                y += lineH(body)
            }
        }

        private fun table(t: Table) {
            val n = t.header.size
            val total = t.firstWeight + (n - 1)
            val widths = List(n) { i -> contentW * (if (i == 0) t.firstWeight else 1f) / total }
            val pad = 4f
            val lh = lineH(body)
            fun cellLines(text: String, i: Int, p: Paint) = wrap(text, p, widths[i] - pad * 2).take(3)
            fun drawRow(cells: List<String>, warn: List<Boolean>?, header: Boolean, shade: Boolean) {
                val paints = cells.indices.map { i ->
                    when {
                        header -> bodyBold
                        warn?.getOrNull(i) == true -> warnP
                        else -> body
                    }
                }
                val lines = cells.mapIndexed { i, c -> cellLines(c, i, paints[i]) }
                val h = (lines.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1) * lh + pad * 2
                if (y + h > pageH - margin - 10f) {
                    newPage()
                    if (!header) drawRow(t.header, null, header = true, shade = false)
                }
                if (header || shade) {
                    fill.color = if (header) 0xFFDDF2E6.toInt() else 0xFFF3F5F4.toInt()
                    canvas.drawRect(margin, y, margin + contentW, y + h, fill)
                }
                var x = margin
                lines.forEachIndexed { i, ls ->
                    ls.forEachIndexed { j, l -> canvas.drawText(l, x + pad, y + pad + body.textSize + j * lh, paints[i]) }
                    x += widths[i]
                }
                y += h
            }
            y += 4f
            drawRow(t.header, null, header = true, shade = false)
            t.rows.forEachIndexed { i, row -> drawRow(row, t.warn.getOrNull(i), header = false, shade = i % 2 == 1) }
            y += 6f
        }
    }

    // ---------- Imagem ----------

    private fun renderImage(r: Report): Bitmap {
        val w = 1080
        val measured = drawImage(null, r, w, 0)
        val h = measured.coerceIn(1080, 3200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawImage(Canvas(bmp), r, w, h)
        return bmp
    }

    /** Desenha (ou só mede, com canvas nulo) o cartão-resumo. Devolve a altura usada. */
    private fun drawImage(c: Canvas?, r: Report, w: Int, h: Int): Int {
        val neon = 0xFF39FF88.toInt()
        val cyan = 0xFF22D3EE.toInt()
        val white = 0xFFF2F5F3.toInt()
        val mutedC = 0xFF9AA8A2.toInt()
        val red = 0xFFFF5C6C.toInt()
        fun p(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val maxH = 3200f - 140f
        if (c != null) {
            c.drawColor(0xFF0B0F0E.toInt())
            val glow = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h * 0.5f, 0x5539FF88, 0x000B0F0E, Shader.TileMode.CLAMP) }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)
        }
        val pad = 72f
        val width = w - pad * 2
        var y = pad + 50f
        var cut = false
        fun line(text: String, paint: Paint, indent: Float = 0f, maxLines: Int = 3) {
            if (cut) return
            val lines = wrap(text, paint, width - indent).take(maxLines)
            for (l in lines) {
                if (y + paint.textSize * 1.3f > maxH) { cut = true; return }
                c?.drawText(l, pad + indent, y + paint.textSize, paint)
                y += paint.textSize * 1.3f
            }
        }
        c?.drawText("EVOLUI · SAÚDE", pad, y, p(44f, neon, true).apply { letterSpacing = 0.15f })
        y += 30f
        line(r.title, p(68f, white, true), maxLines = 2)
        line(r.patient, p(32f, mutedC))
        y += 20f
        val body = p(34f, white)
        val mutedP = p(30f, mutedC)
        val warnP = p(34f, red, true)
        val subP = p(36f, white, true)
        val headP = p(40f, cyan, true)
        for (s in r.sections) {
            if (cut) break
            y += 24f
            if (c != null && !cut) {
                val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A18.toInt() }
                c.drawRoundRect(RectF(pad - 24f, y - 8f, w - pad + 24f, y + 4f), 4f, 4f, card)
            }
            line(s.heading, headP, maxLines = 2)
            var shown = 0
            for (item in s.items) {
                if (cut) break
                if (shown >= 12) { line("…", mutedP); break }
                when (item) {
                    is Sub -> line(item.text, subP, maxLines = 2)
                    is Para -> line(item.text, if (item.warn) warnP else if (item.muted) mutedP else body)
                    is KV -> line("${item.key}: ${item.value}", body)
                    is Table -> {
                        item.rows.take(10).forEachIndexed { i, row ->
                            val warn = item.warn.getOrNull(i)?.any { it } == true
                            val text = when (item.kind) {
                                TableKind.VALUES -> "${row[0]}: ${row.getOrElse(1) { "" }}"
                                TableKind.COMPARE -> "${row[0]}: " + (1 until row.size).filter { row[it] != "—" }.takeLast(3).joinToString(" → ") { row[it] }
                                TableKind.PLAIN -> row.filter { it.isNotBlank() }.joinToString(" · ")
                            }
                            line("• $text", if (warn) warnP else body, maxLines = 2)
                        }
                        if (item.rows.size > 10) line("… e mais ${item.rows.size - 10} itens", mutedP)
                    }
                }
                shown++
            }
        }
        if (cut) {
            y = min(y, maxH)
            line2(c, "… veja o relatório completo em PDF", pad, y + 40f, p(30f, mutedC))
            y += 50f
        }
        y += 60f
        val footY = max(y, (if (h > 0) h - 72f else y))
        c?.drawText("Feito com o app Evolui · não substitui avaliação profissional", pad, footY, p(28f, mutedC))
        return (y + 100f).toInt()
    }

    private fun line2(c: Canvas?, text: String, x: Float, y: Float, p: Paint) {
        c?.drawText(text, x, y, p)
    }
}
