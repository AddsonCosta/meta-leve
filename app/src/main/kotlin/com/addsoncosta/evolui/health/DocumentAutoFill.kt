package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.data.HealthType
import java.time.LocalDate

/**
 * O que a leitura de um documento conseguiu achar para preencher o formulário.
 * Os campos fixos usam as chaves [DocumentAutoFill.DATE], [DocumentAutoFill.TITLE],
 * [DocumentAutoFill.PROFESSIONAL] e [DocumentAutoFill.PLACE]; os outros são as chaves do JSON `extra`.
 */
data class AutoFill(
    val date: LocalDate? = null,
    val title: String? = null,
    val professional: String? = null,
    val place: String? = null,
    val description: String? = null,
    val extras: Map<String, String> = emptyMap(),
    /** Receita médica com remédios, quando o documento é uma receita. */
    val prescription: Prescription? = null,
) {
    val isEmpty get() = date == null && title == null && professional == null && place == null && description == null &&
        extras.isEmpty() && (prescription == null || prescription.medicines.isEmpty())

    /** Junta duas leituras: o que esta não achou vem da [other]. */
    fun orElse(other: AutoFill): AutoFill = AutoFill(
        date = date ?: other.date,
        title = title ?: other.title,
        professional = professional ?: other.professional,
        place = place ?: other.place,
        description = description ?: other.description,
        extras = other.extras + extras,
        prescription = prescription?.takeIf { it.medicines.isNotEmpty() } ?: other.prescription ?: prescription,
    )
}

/**
 * Leitura automática dos dados essenciais de qualquer documento da Saúde (consulta, medicação,
 * odontologia, nutrição, fisioterapia, terapia, tratamentos, suplementos...). Puro, testado na JVM.
 * Exames, óculos e bioimpedância continuam com os leitores próprios; aqui entram os campos de texto.
 */
object DocumentAutoFill {
    const val DATE = "@data"
    const val TITLE = "@titulo"
    const val PROFESSIONAL = "@profissional"
    const val PLACE = "@local"

    /** Campos essenciais de cada área: os que ficam destacados com "Preencher" se a leitura não achar. */
    fun essentialKeys(type: HealthType, reportExam: Boolean = false): List<String> = when (type) {
        HealthType.CONSULTA -> listOf(DATE, TITLE, PROFESSIONAL, PLACE, "especialidade")
        HealthType.EXAME -> if (reportExam) listOf(DATE, TITLE, PLACE, HealthFields.REPORT_CONCLUSION) else listOf(DATE, TITLE, PLACE)
        HealthType.MEDICACAO -> listOf(TITLE, PROFESSIONAL, "dose", "frequencia", "inicio")
        HealthType.ODONTOLOGIA -> listOf(DATE, TITLE, PROFESSIONAL, PLACE, "procedimento")
        HealthType.NUTRICAO, HealthType.FISIOTERAPIA, HealthType.TERAPIA, HealthType.TRATAMENTO ->
            listOf(DATE, TITLE, PROFESSIONAL, PLACE, "frequencia")
        HealthType.BIOIMPEDANCIA -> listOf(DATE, PLACE)
        HealthType.OFTALMOLOGIA -> listOf(DATE, PROFESSIONAL, PLACE, "od_esferico", "oe_esferico")
        HealthType.SUPLEMENTACAO -> listOf(TITLE, "marca", "dose", "horario")
        HealthType.ANAMNESE -> emptyList()
    }

    /** Conselhos profissionais por área (para achar o nome do profissional). */
    private fun councils(type: HealthType): String = when (type) {
        HealthType.ODONTOLOGIA -> "cro"
        HealthType.NUTRICAO -> "crn"
        HealthType.FISIOTERAPIA -> "crefito"
        HealthType.TERAPIA -> "crp|crm|crfa|crefito"
        HealthType.SUPLEMENTACAO -> "crn|crm"
        else -> "crm|cremesp"
    }

    private val placeWords = Regex(
        "(?<![a-z])(clinica|hospital|consultorio|instituto|centro medico|centro de saude|policlinica|odonto|ubs|upa|" +
            "posto de saude|laboratorio|espaco|studio|estudio|fisio|nutri|academia)(?![a-z])",
    )

    fun read(type: HealthType, text: String, usedPlaces: List<String> = emptyList(), today: LocalDate = LocalDate.now()): AutoFill {
        val raw = TextScan.dashes(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val folded = raw.map { TextScan.fold(it) }
        val rx = PrescriptionParser.parse(text, today)
        val professional = professional(raw, folded, type) ?: rx.doctor?.takeIf { type != HealthType.ODONTOLOGIA }
        val base = AutoFill(
            date = rx.date,
            professional = professional,
            place = place(raw, folded, usedPlaces),
        )
        val specific = when (type) {
            HealthType.CONSULTA -> consultation(raw, folded, rx, professional, today)
            HealthType.MEDICACAO -> {
                val m = rx.medicines.firstOrNull()
                AutoFill(
                    title = m?.name,
                    professional = rx.doctorWithCrm,
                    extras = m?.let { medicineExtras(it, rx.date) }.orEmpty(),
                    prescription = rx.takeIf { it.medicines.isNotEmpty() && it.looksLikePrescription },
                )
            }
            HealthType.SUPLEMENTACAO -> supplement(raw, folded, rx)
            HealthType.ODONTOLOGIA -> {
                val proc = dentalProcedure(folded)
                AutoFill(
                    title = proc,
                    extras = buildMap {
                        proc?.let { put("procedimento", it) }
                        teeth(folded)?.let { put("dentes", it) }
                    },
                )
            }
            HealthType.NUTRICAO, HealthType.FISIOTERAPIA, HealthType.TERAPIA, HealthType.TRATAMENTO -> AutoFill(
                extras = buildMap {
                    weeklyFrequency(folded)?.let { put("frequencia", it) }
                    sessions(folded)?.let { put("sessoes", it) }
                    afterLabel(raw, folded, "objetivo|objetivos|meta")?.let { put("objetivo", it) }
                },
            )
            HealthType.BIOIMPEDANCIA -> AutoFill(place = bioDevice(folded))
            HealthType.EXAME, HealthType.OFTALMOLOGIA, HealthType.ANAMNESE -> AutoFill()
        }
        return specific.orElse(base)
    }

    // ---------- Consulta ----------

    private const val DIAGNOSIS_LABELS = "hipotese diagnostica|hipoteses diagnosticas|impressao diagnostica|diagnosticos?|hd|cid(?:-?10)?|" +
        "parecer|avaliacao|conclusao"
    private const val GUIDANCE_LABELS = "conduta|orientacoes|orientacao|recomendacoes|plano terapeutico|plano de tratamento"
    private const val REASON_LABELS = "motivo da consulta|motivo do atendimento|motivo|queixa principal|queixas?|qp|" +
        "historia da doenca atual|hda|anamnese|sintomas"
    private const val PROCEDURE_LABELS = "procedimentos? realizados?|procedimentos?|exames? solicitados?|solicitacao de exames?|" +
        "pedido de exames?|exames? pedidos?|exames? complementares|solicito|solicita-se"
    private const val REFERRAL_LABELS = "encaminhamento|encaminho(?: (?:a|ao|a o|para|o paciente|a paciente)(?: (?:ao|a|para))?)?|encaminhada?|encaminhar"

    /** Qualquer rótulo conhecido (para saber onde termina o texto de outro rótulo). */
    private val anyLabel = Regex(
        "^\\s*(?:\\d+[.)]\\s*)?(?:(?:$DIAGNOSIS_LABELS|$GUIDANCE_LABELS|$REASON_LABELS|$PROCEDURE_LABELS|encaminhamento|exame fisico|prescricao|receita|" +
            "uso oral|uso interno|uso continuo|paciente|data|medicacoes?|medicamentos?|observacoes?|obs|evolucao|exame fisico)(?:\\s*[:\\-]|\\s*$)|retorno(?![a-z]))",
    )

    private fun consultation(raw: List<String>, folded: List<String>, rx: Prescription, professional: String?, today: LocalDate): AutoFill {
        val referral = referral(raw, folded)
        // A especialidade da consulta não é a do encaminhamento ("encaminho ao cardiologista").
        val specialty = rx.specialty
        // Seções do prontuário ("Hipótese diagnóstica:") não são remédios.
        val medicines = rx.medicines.filter { m -> !anyLabel.containsMatchIn(TextScan.fold(m.line.ifBlank { m.name })) }
            .takeIf { rx.looksLikePrescription }.orEmpty()
        val extras = buildMap {
            specialty?.let { put("especialidade", it) }
            returnDate(folded, rx.date ?: today)?.let { put("retorno", it.toString()) }
            listOfNotNull(labeled(raw, folded, DIAGNOSIS_LABELS), labeled(raw, folded, GUIDANCE_LABELS)).distinct()
                .joinToString("\n").takeIf { it.isNotEmpty() }?.let { put("diagnostico", it) }
            labeled(raw, folded, REASON_LABELS)?.let { put("motivo", it) }
            (labeled(raw, folded, PROCEDURE_LABELS) ?: procedureLines(raw, folded))?.let { put("procedimento", it) }
            referral?.let { put("encaminhamento", it) }
            if (medicines.isNotEmpty()) put("receita", medicines.joinToString("\n") { it.line.ifBlank { it.name } })
        }
        val title = when {
            specialty != null -> "Consulta de ${specialty.lowercase()}"
            professional != null -> "Consulta com $professional"
            rx.date != null || extras.isNotEmpty() -> "Consulta médica"
            else -> null
        }
        return AutoFill(
            title = title,
            extras = extras,
            prescription = rx.copy(medicines = medicines).takeIf { medicines.isNotEmpty() },
        )
    }

    /**
     * Texto depois de "Rótulo:" (na mesma linha e nas seguintes, até o próximo rótulo).
     * Junta até 6 linhas e corta em 500 letras.
     */
    private fun labeled(raw: List<String>, folded: List<String>, labels: String): String? {
        val re = Regex("^\\s*(?:\\d+[.)]\\s*)?(?:$labels)(?![a-z])\\s*[:\\-]?\\s*")
        for (i in folded.indices) {
            val m = re.find(folded[i]) ?: continue
            val same = raw[i].substring(m.range.last + 1).trim()
            // Sem ":" e sem nada depois, só vale se a linha for só o rótulo (título de seção).
            val head = folded[i].substring(m.range.first, m.range.last + 1)
            val verb = Regex("(?<![a-z])(solicito|solicita-se|encaminho|encaminhar|encaminhada?)(?![a-z])").containsMatchIn(head)
            if (same.isNotEmpty() && !verb && !head.contains(':') && !head.contains('-')) continue
            val parts = mutableListOf<String>()
            if (same.isNotEmpty()) parts += same
            var j = i + 1
            while (j < raw.size && parts.size < 6) {
                val f = folded[j]
                if (anyLabel.containsMatchIn(f) || isFooter(f)) break
                parts += raw[j]
                j++
                if (same.isNotEmpty() && parts.size >= 3) break
            }
            val text = parts.joinToString("\n").trim().take(500)
            if (text.count { it.isLetterOrDigit() } >= 3) return text
        }
        return null
    }

    /** Linhas de assinatura, data e local que encerram o documento. */
    private fun isFooter(f: String): Boolean =
        Regex("(?<![a-z])(crm|cremesp|rqe|assinatura|carimbo)(?![a-z])").containsMatchIn(f) ||
            Regex("^[a-z ]{3,30},\\s*\\d{1,2}(?:/|\\s+de\\s+)").containsMatchIn(f) ||
            Regex("^(?:dra?\\.?|doutora?)\\s").containsMatchIn(f)

    /** Sem rótulo: frases como "Solicito hemograma e TSH" ou "Realizado curativo". */
    private fun procedureLines(raw: List<String>, folded: List<String>): String? {
        val re = Regex("(?<![a-z])(solicito|solicitad[oa]s?|realizad[oa]s? (?:o |a )?(?:procedimento|curativo|sutura|drenagem|infiltracao|biopsia|cauterizacao|retirada)|" +
            "pedido de|fazer exames?|realizar exames?)(?![a-z])")
        val found = folded.indices.filter { re.containsMatchIn(folded[it]) && !folded[it].contains("encaminh") }.map { raw[it] }
        return found.takeIf { it.isNotEmpty() }?.joinToString("\n")?.take(500)
    }

    /** "Encaminho ao cardiologista", "Encaminhamento: Ortopedia" viram a especialidade (e o motivo, se houver). */
    private fun referral(raw: List<String>, folded: List<String>): String? {
        for (i in folded.indices) {
            val f = folded[i]
            val k = f.indexOf("encaminh")
            if (k < 0) continue
            val window = (f.substring(k) + " " + folded.getOrNull(i + 1).orEmpty())
            val spec = PrescriptionParser.specialties.firstOrNull { it.first.containsMatchIn(window) }?.second
            val text = labeled(raw, folded, REFERRAL_LABELS)
            return when {
                spec != null && text != null && TextScan.fold(text).length > 25 -> "$spec. $text"
                spec != null -> spec
                text != null -> text
                else -> raw[i].substring(k).trim().take(200).takeIf { it.count { c -> c.isLetter() } >= 12 }
            }
        }
        return null
    }

    /** Campos da Medicação a partir de um remédio da receita. */
    fun medicineExtras(m: PrescribedMedicine, prescriptionDate: LocalDate?): Map<String, String> = buildMap {
        m.dose?.let { put("dose", it) }
        val freq = listOfNotNull(m.posology, m.duration?.takeIf { m.continuous }?.lowercase()).joinToString(", ")
        if (freq.isNotBlank()) put("frequencia", freq)
        prescriptionDate?.let { d ->
            put("inicio", d.toString())
            m.durationDays?.let { days -> put("fim", d.plusDays(days.toLong() - 1).toString()) }
        }
    }

    private fun professional(raw: List<String>, folded: List<String>, type: HealthType): String? {
        val council = Regex("(?<![a-z])(?:${councils(type)})(?![a-z])")
        val title = Regex("(?<![a-z])(?:dra?\\.?|doutora?|nutricionista|fisioterapeuta|psicologa?|terapeuta|dentista)\\s+(?=[a-z])")
        val idx = folded.indices.firstOrNull { council.containsMatchIn(folded[it]) }
        val candidates = folded.indices.filter { i -> title.containsMatchIn(folded[i]) && !folded[i].contains("paciente") }
        val pick = if (idx != null) candidates.minByOrNull { kotlin.math.abs(it - idx) } else candidates.firstOrNull()
        if (pick != null) {
            val m = title.find(folded[pick])!!
            val keepTitle = folded[pick].substring(m.range.first).startsWith("dr")
            val start = if (keepTitle) m.range.first else m.range.last + 1
            name(raw[pick].substring(start))?.let { return it }
        }
        if (idx != null) {
            val f = folded[idx]
            val c = council.find(f)!!
            name(raw[idx].substring(0, c.range.first))?.let { return it }
            if (idx > 0) name(raw[idx - 1])?.takeIf { n -> n.none { it.isDigit() } && !placeWords.containsMatchIn(TextScan.fold(n)) }?.let { return it }
        }
        return null
    }

    private fun name(s: String): String? {
        var t = s
        val f = TextScan.fold(t)
        val stops = listOf(" - ", " – ", "|", ",", "(", " / ", "crm", "cro", "crn", "crefito", "crp", ":")
        var end = t.length
        for (st in stops) {
            val i = f.indexOf(st, startIndex = 2)
            if (i in 1 until end) end = i
        }
        val digit = t.indexOfFirst { it.isDigit() }
        if (digit in 1 until end) end = digit
        t = t.substring(0, end).trim().trim('-', ':', ',', '.', ' ').replace(Regex("\\s+"), " ")
        val words = t.split(' ').filter { w -> w.count { it.isLetter() } >= 2 }
        if (words.size !in 2..7 || t.length > 60) return null
        return PrescriptionParser.nameCase(t)
    }

    private fun place(raw: List<String>, folded: List<String>, usedPlaces: List<String>): String? {
        val head = folded.take(15)
        for (p in usedPlaces) {
            val n = TextScan.fold(p.trim())
            if (n.length >= 4 && head.any { it.contains(n) }) return p
        }
        // "Unidade: Policlínica Central   Especialidade: ..." (rótulo explícito).
        val labeledPlace = Regex("(?<![a-z])(?:unidade|local de atendimento|local|estabelecimento|instituicao|servico)\\s*:\\s*")
        for (i in head.indices) {
            val m = labeledPlace.find(head[i]) ?: continue
            val v = raw[i].substring(m.range.last + 1).split(Regex("\\s{2,}|\\||\\t")).first().trim().trim('-', ':', ' ')
            if (v.count { it.isLetter() } >= 4 && v.length <= 60) return v
        }
        for (i in head.indices) {
            val f = head[i]
            if (f.contains("paciente") || f.contains("convenio") || f.startsWith("rua") || f.startsWith("av")) continue
            // "Clínica Médica" sozinha é especialidade, não lugar.
            val onlySpecialty = Regex("^(?:clinica medica|clinica geral)(?:\\s*[-–].*)?$").containsMatchIn(f)
            if (placeWords.containsMatchIn(f) && !onlySpecialty && raw[i].length in 5..60 && raw[i].count { it.isDigit() } <= 2 &&
                !f.contains("crm") && !Regex("(?<![a-z])dra?\\.").containsMatchIn(f)
            ) {
                return raw[i].trim().trim('-', ':', ' ')
            }
        }
        return null
    }

    /** "Retorno em 30 dias" ou "Retorno: 12/11/2026". */
    private fun returnDate(folded: List<String>, from: LocalDate): LocalDate? {
        for (f in folded) {
            val i = f.indexOf("retorno")
            if (i < 0) continue
            val rest = f.substring(i)
            TextScan.dates(rest, LocalDate.MAX).firstOrNull()?.let { if (it.isAfter(from)) return it }
            val m = Regex("retorno\\s*(?:em|apos|daqui a|:)?\\s*(\\d{1,3})\\s*(dias?|semanas?|meses|mes)").find(rest) ?: continue
            val n = m.groupValues[1].toLong()
            return when {
                m.groupValues[2].startsWith("dia") -> from.plusDays(n)
                m.groupValues[2].startsWith("semana") -> from.plusWeeks(n)
                else -> from.plusMonths(n)
            }
        }
        return null
    }

    /** Texto depois de "Rótulo:" (na mesma linha ou na seguinte). */
    private fun afterLabel(raw: List<String>, folded: List<String>, labels: String): String? {
        val re = Regex("^\\s*(?:$labels)\\s*[:\\-]\\s*")
        for (i in folded.indices) {
            val m = re.find(folded[i]) ?: continue
            val same = raw[i].substring(m.range.last + 1).trim()
            val text = same.ifBlank { raw.getOrNull(i + 1)?.trim().orEmpty() }
            if (text.count { it.isLetterOrDigit() } >= 3) return text.take(300)
        }
        return null
    }

    private val procedures: List<Pair<Regex, String>> = listOf(
        "tratamento de canal|tratamento endodontico|endodontia|canal" to "Tratamento de canal",
        "limpeza|profilaxia|tartarectomia" to "Limpeza",
        "raspagem|periodont" to "Raspagem (periodontia)",
        "restauracao|restauracoes|resina" to "Restauração",
        "extracao|exodontia" to "Extração",
        "implante" to "Implante",
        "clareamento" to "Clareamento",
        "aparelho|ortodont|manutencao do aparelho" to "Aparelho (ortodontia)",
        "protese|coroa|ponte movel|dentadura" to "Prótese",
        "faceta|lente de contato dental" to "Facetas",
        "aplicacao de fluor|fluor" to "Aplicação de flúor",
        "avaliacao|consulta inicial|orcamento" to "Avaliação",
    ).map { (k, v) -> Regex("(?<![a-z])(?:$k)(?![a-z])") to v }

    private fun dentalProcedure(folded: List<String>): String? {
        val all = folded.joinToString("\n")
        return procedures.firstOrNull { it.first.containsMatchIn(all) }?.second
    }

    private val tooth = "(?:[1-4][1-8]|[5-8][1-5])"

    /** "Dente 16", "elementos 26 e 27", "d. 36" viram "16" / "26, 27". */
    private fun teeth(folded: List<String>): String? {
        val re = Regex("(?<![a-z])(?:dentes?|elementos?|d\\.)\\s*(?:n[º°o.]*\\s*)?:?\\s*($tooth(?:\\s*(?:,|e|/|-)\\s*$tooth)*)(?!\\d)")
        val found = folded.flatMap { f ->
            re.findAll(f).flatMap { m -> Regex(tooth).findAll(m.groupValues[1]).map { it.value } }.toList()
        }.distinct()
        return if (found.isEmpty()) null else found.joinToString(", ")
    }

    private val weekWords = mapOf("uma" to "1", "um" to "1", "duas" to "2", "dois" to "2", "tres" to "3", "quatro" to "4", "cinco" to "5")

    /** "2x por semana", "duas vezes na semana" viram "2 vezes por semana". */
    private fun weeklyFrequency(folded: List<String>): String? {
        val re = Regex("(?<![a-z0-9])(\\d|uma|um|duas|dois|tres|quatro|cinco)\\s*(?:x|vez|vezes)\\s*(?:por|na|a|ao|/)?\\s*semana")
        for (f in folded) {
            val m = re.find(f) ?: continue
            val n = m.groupValues[1].toIntOrNull()?.toString() ?: weekWords[m.groupValues[1]] ?: continue
            return if (n == "1") "1 vez por semana" else "$n vezes por semana"
        }
        if (folded.any { Regex("(?<![a-z])semanal(?:mente)?(?![a-z])").containsMatchIn(it) }) return "1 vez por semana"
        if (folded.any { Regex("(?<![a-z])quinzenal(?:mente)?(?![a-z])").containsMatchIn(it) }) return "A cada 15 dias"
        return null
    }

    private fun sessions(folded: List<String>): String? {
        val re = Regex("(?<![0-9])(\\d{1,3})\\s*(?:\\(\\s*[a-z]+\\s*\\)\\s*)?sess(?:oes|ao)(?![a-z])")
        return folded.firstNotNullOfOrNull { re.find(it)?.groupValues?.get(1) }?.takeIf { (it.toIntOrNull() ?: 0) in 1..200 }
    }

    private fun bioDevice(folded: List<String>): String? {
        val brands = listOf("inbody" to "InBody", "tanita" to "Tanita", "omron" to "Omron", "biodynamics" to "Biodynamics", "sanny" to "Sanny", "seca" to "Seca", "relaxmedic" to "Relaxmedic")
        val all = folded.joinToString(" ")
        return brands.firstOrNull { Regex("(?<![a-z])${it.first}(?![a-z])").containsMatchIn(all) }?.let { "Balança ${it.second}" }
    }

    // ---------- Suplementos ----------

    /** Suplementos comuns (texto sem acento) e o nome para mostrar. */
    val supplementNames: List<Pair<Regex, String>> = listOf(
        "whey protein|whey" to "Whey protein",
        "creatina|creatine" to "Creatina",
        "omega ?3|omega-3|oleo de peixe|fish oil" to "Ômega 3",
        "vitamina d3?|colecalciferol" to "Vitamina D",
        "vitamina c|acido ascorbico" to "Vitamina C",
        "vitamina b12|b12|cianocobalamina|metilcobalamina" to "Vitamina B12",
        "complexo b" to "Complexo B",
        "multivitaminico|polivitaminico|multivitamin" to "Multivitamínico",
        "colageno|collagen" to "Colágeno",
        "magnesio|magnesium" to "Magnésio",
        "zinco|zinc" to "Zinco",
        "ferro|ferroso|ferrico" to "Ferro",
        "calcio|calcium" to "Cálcio",
        "acido folico|folato" to "Ácido fólico",
        "cafeina|caffeine" to "Cafeína",
        "beta-alanina|beta alanina" to "Beta-alanina",
        "glutamina|glutamine" to "Glutamina",
        "bcaa" to "BCAA",
        "pre-treino|pre treino|pre-workout" to "Pré-treino",
        "hipercalorico|mass gainer" to "Hipercalórico",
        "albumina" to "Albumina",
        "probiotico|lactobacillus|bifidobacterium" to "Probiótico",
        "melatonina" to "Melatonina",
        "coenzima q10|coq10" to "Coenzima Q10",
        "biotina" to "Biotina",
        "termogenico" to "Termogênico",
    ).map { (k, v) -> Regex("(?<![a-z])(?:$k)(?![a-z])") to v }

    private val brands = listOf(
        "Growth Supplements", "Growth", "Max Titanium", "Integralmedica", "Integral Médica", "Probiótica", "Optimum Nutrition",
        "Dux Nutrition", "Dux", "Essential Nutrition", "Black Skull", "Atlhetica", "Atlhetica Nutrition", "Vitafor", "Sanavita",
        "Puravida", "Nature's Bounty", "Centrum", "Now Foods", "Nutrify", "New Millen", "Darkness", "Cellucor", "Universal",
        "Muscletech", "Body Action", "Equaliv", "Lavitan", "Ocean Drop", "Nutrata", "Soldiers Nutrition", "Bold Snacks",
    )

    private fun supplement(raw: List<String>, folded: List<String>, rx: Prescription): AutoFill {
        val all = folded.joinToString("\n")
        // Só usa a lista de remédios quando é mesmo uma receita (com médico ou nutricionista).
        val first = rx.medicines.firstOrNull()?.takeIf { rx.doctor != null || rx.crm != null }
        val name = first?.name ?: supplementNames.firstOrNull { it.first.containsMatchIn(all) }?.second
        val brand = brands.sortedByDescending { it.length }.firstOrNull { b ->
            Regex("(?<![a-z])" + Regex.escape(TextScan.fold(b)) + "(?![a-z])").containsMatchIn(all)
        }
        val dose = first?.dose ?: Regex("(?<![a-z])(?:porcao|porcao de|dose|serving|tomar|ingerir|consumir)\\s*(?:de|:)?\\s*(\\d+(?:[.,]\\d+)?\\s*(?:g|mg|mcg|ml|ui|capsulas?|comprimidos?|scoops?|dosadores?|medidas?|gotas?)(?:\\s*\\([^)]{1,30}\\))?)")
            .find(all)?.let { m -> rawAt(raw, folded, m.groups[1]!!.range) }
        val schedule = first?.posology ?: Regex("(?<![a-z])(?:\\d\\s*(?:x|vez|vezes) ao dia|ao dia|antes do treino|apos o treino|pela manha|a noite|ao deitar|em jejum|junto (?:com|as) refeic(?:ao|oes))(?![a-z])")
            .find(all)?.let { m -> rawAt(raw, folded, m.range) }
        return AutoFill(
            title = name,
            professional = rx.doctorWithCrm,
            extras = buildMap {
                brand?.let { put("marca", it) }
                dose?.let { put("dose", it) }
                schedule?.let { put("horario", it) }
                rx.date?.let { d -> if (first != null) put("inicio", d.toString()) }
            },
        )
    }

    /** Recorta do texto original o trecho achado no texto juntado (sem acento) por "\n". */
    private fun rawAt(raw: List<String>, folded: List<String>, range: IntRange): String {
        val joined = raw.joinToString("\n")
        if (folded.isEmpty() || range.first >= joined.length) return ""
        return joined.substring(range.first, (range.last + 1).coerceAtMost(joined.length)).trim()
    }
}
