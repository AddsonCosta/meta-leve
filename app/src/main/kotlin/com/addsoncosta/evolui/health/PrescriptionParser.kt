package com.addsoncosta.evolui.health

import java.time.LocalDate

/** Um remédio lido da receita. */
data class PrescribedMedicine(
    /** Ex.: "Amoxicilina". */
    val name: String,
    /** Concentração e quanto tomar de cada vez. Ex.: "500 mg, 1 cápsula". */
    val dose: String? = null,
    /** Como tomar. Ex.: "1 cápsula de 8 em 8 horas". */
    val posology: String? = null,
    /** Ex.: "por 7 dias" ou "Uso contínuo". */
    val duration: String? = null,
    /** Dias de tratamento, quando a receita diz (ex.: 7). */
    val durationDays: Int? = null,
    val continuous: Boolean = false,
    /** Só se precisar (dor, febre...): sem lembrete por padrão. */
    val asNeeded: Boolean = false,
    /** Linha original da receita, para o usuário conferir. */
    val line: String = "",
)

/** O que foi possível ler de uma receita médica. */
data class Prescription(
    val date: LocalDate? = null,
    val doctor: String? = null,
    /** Ex.: "CRM-SP 123456". */
    val crm: String? = null,
    val specialty: String? = null,
    val medicines: List<PrescribedMedicine> = emptyList(),
    /** O texto tem jeito de receita (receituário, uso oral, tomar...). */
    val looksLikePrescription: Boolean = false,
) {
    val isEmpty get() = date == null && doctor == null && crm == null && specialty == null && medicines.isEmpty()

    /** "Dr. João Pereira (CRM-SP 123456)", ou o que houver. */
    val doctorWithCrm: String?
        get() = when {
            doctor != null && crm != null -> "$doctor ($crm)"
            doctor != null -> doctor
            else -> crm
        }
}

/**
 * Lê receitas médicas brasileiras: data, médico (nome e CRM), especialidade e cada remédio com
 * a dose, como tomar ("de 8 em 8 horas", "1 comprimido ao dia") e por quanto tempo ("por 7 dias",
 * "uso contínuo"). Feito no próprio celular, sobre o texto do OCR; o usuário confere antes de salvar.
 */
object PrescriptionParser {
    private val ufs = setOf(
        "ac", "al", "ap", "am", "ba", "ce", "df", "es", "go", "ma", "mt", "ms", "mg", "pa", "pb", "pr", "pe", "pi",
        "rj", "rn", "rs", "ro", "rr", "sc", "sp", "se", "to",
    )

    /** Concentração: "500 mg", "7.000 UI", "20mg/ml", "0,5%". Procura no texto sem acento. */
    private val doseRegex = Regex(
        "(?<![a-z0-9])\\d+(?:[.,]\\d+)?\\s*(?:mg/ml|mg/g|mcg|µg|ug|mg|ml|ui|u\\.i\\.|g|%|gotas|gts)(?![a-z])",
    )

    /** "1. ", "2) ", "03 - ", "• ". */
    private val numbering = Regex("^\\s*(?:\\d{1,2}\\s*[).\\-–:º°]+|[-•*>]+)\\s*")

    /** Separador entre o remédio e a quantidade a comprar ("------ 21 cápsulas"). */
    private val separator = Regex("-{2,}|_{2,}|\\.{3,}|…+|—+|\\s-\\s")

    private val verbs = "tomar|tome|usar|use|aplicar|aplique|pingar|pingue|ingerir|administrar|mastigar|dissolver|" +
        "diluir|inalar|passar|passe|colocar|coloque|instilar|fazer|faca|chupar|tomo|utilizar|utilize"

    private val verbStart = Regex("^\\s*(?:$verbs)(?![a-z])")
    private val verbInside = Regex("(?<![a-z])(?:$verbs)(?![a-z])")

    private val form = "comprimidos?|comp\\.?|cps?\\.?|capsulas?|caps\\.?|drageas?|gotas?|gts|ml|saches?|envelopes?|" +
        "ampolas?|jatos?|puffs?|borrifadas?|colher(?:es)?(?: de (?:sopa|cha|sobremesa))?|medidas?|scoops?|" +
        "aplicac(?:ao|oes)|unidades?|pastilhas?|adesivos?|supositorios?|doses?|flaconetes?"

    private val numberWord = "\\d+(?:[.,]\\d+)?|meio|meia|um|uma|dois|duas|tres|quatro|cinco"

    /** "1 comprimido", "meio comprimido", "20 gotas": quanto tomar de cada vez. */
    private val perTake = Regex("(?<![a-z0-9])(?:$numberWord)\\s+(?:$form)(?![a-z])")
    private val perTakeStart = Regex("^\\s*(?:$numberWord)\\s+(?:$form)(?![a-z])")

    /** Padrões de posologia, para reconhecer linhas de instrução sem verbo. */
    private val posologyHint = Regex(
        "(?<![a-z])(?:de \\d{1,2} em \\d{1,2}|\\d{1,2}\\s*/\\s*\\d{1,2}\\s*h|a cada \\d|\\d\\s*x\\s*(?:ao|por)? ?dia|vezes (?:ao|por) dia|" +
            "ao dia|por dia|ao deitar|em jejum|pela manha|a noite|por semana|apos as refeicoes|antes das refeicoes|uso continuo|se dor|se febre)",
    )

    private val continuousRegex = Regex(
        "(?<![a-z])(?:uso continuo|de uso continuo|continuamente|tempo indeterminado|sem interrupcao|ate segunda ordem|ate nova orientacao|ate novas orientacoes)(?![a-z])",
    )

    private val durationRegex = Regex(
        "(?<![a-z0-9])(?:(?:por|durante)\\s+(?:um periodo de\\s+)?|x\\s*)?(\\d{1,3}|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|quinze|trinta)\\s*" +
            "(dias?|semanas?|meses|mes)(?![a-z])",
    )

    private val asNeededRegex = Regex(
        "(?<![a-z])(?:se dor|se febre|se necessario|quando necessario|em caso de|s/n|sos|se nausea|se enjoo|se coceira|se precisar)(?![a-z])",
    )

    private val intervalSlash = Regex("(?i)(?:de\\s+)?(?<!\\d)(\\d{1,2})\\s*/\\s*\\1\\s*(?:h(?:oras?|rs?|s)?)(?![a-zà-ú])")

    /** Linhas que não são remédio nem instrução. */
    private val skipLine = Regex(
        "(?<![a-z])(paciente|nome|endereco|rua|av\\.|avenida|telefone|tel\\.|tel:|fone|cpf|rg|crm|cremesp|rqe|cro|crn|" +
            "receituario|receita|prescricao|assinatura|carimbo|identificacao|comprador|fornecedor|farmaceutic|cep|www|" +
            "nascimento|idade|convenio|cnpj|hospital|clinica|consultorio|bairro|cidade|e-mail|email|data|validade|" +
            "via do paciente|via da farmacia|controle especial|emitente|sus|cartao|porcao|valor energetico|ingredientes|informacao nutricional|lote|fabricacao)(?![a-z])",
    )

    private val usageHeader = Regex("^\\s*(?:uso\\s+(?:oral|interno|externo|topico|continuo|nasal|oftalmico|otologico|retal|vaginal|sublingual|inalatorio|injetavel|intramuscular)|via oral)\\s*[:.\\-]?\\s*$")

    private val numberWords = mapOf(
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4, "cinco" to 5, "seis" to 6,
        "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10, "quinze" to 15, "trinta" to 30,
    )

    private val monthNames = listOf(
        "janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val writtenDate = Regex("(?<!\\d)(\\d{1,2})\\s*(?:º|°|o)?\\s+de\\s+(${monthNames.joinToString("|")})\\s+de\\s+(\\d{4})")

    /** Especialidades (texto sem acento) e o nome para mostrar. */
    val specialties: List<Pair<Regex, String>> = listOf(
        "clinica medica|clinico geral|clinica geral|medicina interna|medico clinico" to "Clínica médica",
        "medicina de familia|medico de familia|medicina da familia" to "Medicina de família",
        "cardiolog" to "Cardiologia",
        "dermatolog" to "Dermatologia",
        "endocrinolog|endocrino" to "Endocrinologia",
        "ginecolog|obstetr" to "Ginecologia e obstetrícia",
        "pediatr" to "Pediatria",
        "ortoped|traumatolog" to "Ortopedia",
        "psiquiatr" to "Psiquiatria",
        "neurolog" to "Neurologia",
        "urolog" to "Urologia",
        "oftalmolog" to "Oftalmologia",
        "otorrino" to "Otorrinolaringologia",
        "gastroenterolog|gastro" to "Gastroenterologia",
        "pneumolog" to "Pneumologia",
        "reumatolog" to "Reumatologia",
        "nefrolog" to "Nefrologia",
        "geriatr" to "Geriatria",
        "infectolog" to "Infectologia",
        "angiolog|cirurgia vascular" to "Angiologia",
        "hematolog" to "Hematologia",
        "oncolog" to "Oncologia",
        "nutrolog" to "Nutrologia",
        "mastolog" to "Mastologia",
        "coloproctolog|proctolog" to "Coloproctologia",
        "alergolog|alergia e imunologia|imunolog" to "Alergia e imunologia",
        "medicina esportiva|medicina do esporte" to "Medicina esportiva",
        "cirurgia geral|cirurgiao geral" to "Cirurgia geral",
        "cirurgi[aã]o[- ]dentista|odontolog" to "Odontologia",
    ).map { (k, v) -> Regex("(?<![a-z])(?:$k)") to v }

    fun parse(text: String, today: LocalDate = LocalDate.now()): Prescription {
        val raw = TextScan.dashes(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val folded = raw.map { TextScan.fold(it) }
        val all = TextScan.fold(text)
        val crmIdx = folded.indexOfFirst { crmMatch(it) != null }
        val crm = folded.asSequence().mapNotNull { crmMatch(it) }.firstOrNull()
        val doctorIdx = doctorLine(raw, folded, crmIdx)
        val doctor = doctorIdx?.let { doctorName(raw[it], folded[it]) }
            ?: crmIdx.takeIf { it >= 0 }?.let { i -> nameBefore(raw[i], folded[i]) ?: if (i > 0) plainName(raw[i - 1], folded[i - 1]) else null }
        val medicines = medicines(raw, folded)
        val looks = Regex("(?<![a-z])(receit|prescri|uso oral|uso continuo|posologia|tomar|uso interno)").containsMatchIn(all) ||
            medicines.any { it.posology != null }
        return Prescription(
            date = findDate(text, today),
            doctor = doctor,
            crm = crm,
            specialty = specialty(folded, listOfNotNull(doctorIdx, crmIdx.takeIf { it >= 0 })),
            medicines = medicines,
            looksLikePrescription = looks,
        )
    }

    /** Data da receita: a numérica (dd/mm/aaaa) ou a escrita ("12 de outubro de 2026"). */
    fun findDate(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        TextScan.findDate(text, today)?.let { return it }
        val f = TextScan.fold(text)
        return writtenDate.findAll(f).mapNotNull { m ->
            val month = monthNames.indexOf(m.groupValues[2]) + 1
            runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
        }.filter { it.year >= 1990 && !it.isAfter(today) }.maxOrNull()
    }

    /** "CRM-SP 123456", "CRM 123456/SP", "CRM/RJ 52.12345-6" viram "CRM-SP 123456". */
    fun crmMatch(foldedLine: String): String? {
        val m = Regex("(?<![a-z])crm\\s*[-/:.]?\\s*([a-z]{2})?\\s*[-/:.]?\\s*(?:n[º°o.]*\\s*)?(\\d[\\d.\\-]{2,12}\\d)(?:\\s*[-/]\\s*([a-z]{2})(?![a-z]))?")
            .find(foldedLine) ?: return null
        val number = m.groupValues[2].filter { it.isDigit() }
        if (number.length < 4) return null
        val uf = listOf(m.groupValues[1], m.groupValues[3]).firstOrNull { it in ufs }
        return if (uf != null) "CRM-${uf.uppercase()} $number" else "CRM $number"
    }

    private val titleRegex = Regex("(?<![a-z])(?:dra?\\.?|doutora?)\\s+(?=[a-z])")

    private fun doctorLine(raw: List<String>, folded: List<String>, crmIdx: Int): Int? {
        val candidates = folded.indices.filter { i ->
            val f = folded[i]
            titleRegex.containsMatchIn(f) && !f.contains("paciente") && !f.contains("sr.") && !f.contains("sra.") &&
                doctorName(raw[i], f) != null
        }
        if (candidates.isEmpty()) return null
        // O mais perto do CRM (a assinatura); sem CRM, o primeiro (cabeçalho).
        return if (crmIdx >= 0) candidates.minByOrNull { kotlin.math.abs(it - crmIdx) } else candidates.first()
    }

    /** "Dr. João Carlos Pereira - Clínica Médica" vira "Dr. João Carlos Pereira". */
    private fun doctorName(line: String, folded: String): String? {
        val m = titleRegex.find(folded) ?: return null
        var t = line.substring(m.range.first)
        t = cutAtNoise(t, TextScan.fold(t))
        val name = tidyName(t) ?: return null
        val words = name.split(' ').filter { w -> w.any { it.isLetter() } }
        if (words.size < 2) return null
        return name
    }

    /** O que vem antes do "CRM" na mesma linha, se parece um nome. */
    private fun nameBefore(line: String, folded: String): String? {
        val i = folded.indexOf("crm")
        if (i <= 0) return null
        return plainName(line.substring(0, i), folded.substring(0, i))
    }

    /** Linha que parece só um nome de pessoa (2 a 6 palavras, sem números). */
    private fun plainName(line: String, folded: String): String? {
        val t = tidyName(cutAtNoise(line, folded)) ?: return null
        val f = TextScan.fold(t)
        if (specialties.any { it.first.containsMatchIn(f) }) return null
        if (skipLine.containsMatchIn(f) || f.contains("receit")) return null
        val words = t.split(' ').filter { it.isNotBlank() }
        if (words.size !in 2..6 || t.any { it.isDigit() }) return null
        return t
    }

    private fun cutAtNoise(t: String, f: String): String {
        val stops = listOf(" - ", " – ", "|", ",", "crm", "cremesp", "rqe", "(", " / ", "  ", "\t")
        var end = t.length
        for (s in stops) {
            val i = f.indexOf(s, startIndex = 3)
            if (i in 1 until end) end = i
        }
        val digit = t.indexOfFirst { it.isDigit() }
        if (digit in 1 until end) end = digit
        return t.substring(0, end)
    }

    private fun tidyName(s: String): String? {
        val t = s.trim().trim('-', ':', ',', '/', '|', ' ').replace(Regex("\\s+"), " ")
        if (t.count { it.isLetter() } < 4 || t.length > 60) return null
        return nameCase(t)
    }

    /** "DRA. ANA SOUZA" vira "Dra. Ana Souza"; texto misto fica como está. */
    fun nameCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
        val small = setOf("de", "da", "do", "das", "dos", "e")
        return s.lowercase().split(' ').mapIndexed { i, w ->
            if (i > 0 && w in small) w else w.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
    }

    private fun specialty(folded: List<String>, anchors: List<Int>): String? {
        val order = (anchors.flatMap { a -> (a - 2..a + 2).toList() } + folded.indices.take(15) + folded.indices.toList())
            .distinct().filter { it in folded.indices }
        for (i in order) {
            val f = folded[i]
            if (f.contains("paciente") || f.contains("encaminh")) continue
            specialties.firstOrNull { it.first.containsMatchIn(f) }?.let { return it.second }
        }
        return null
    }

    // ---------- Remédios ----------

    private fun isInstruction(f: String): Boolean {
        val body = f.replace(numbering, "")
        if (verbStart.containsMatchIn(body)) return true
        if (perTakeStart.containsMatchIn(body) && doseRegex.find(body.substringBefore(" ")) == null) return true
        return posologyHint.containsMatchIn(body) && doseRegex.find(body) == null && !numbering.containsMatchIn(f)
    }

    private fun isStop(f: String): Boolean =
        skipLine.containsMatchIn(f) || TextScan.dates(f).isNotEmpty() || writtenDate.containsMatchIn(f)

    private fun nameEnd(body: String, fb: String): Int {
        val ends = listOfNotNull(
            doseRegex.find(fb)?.range?.first,
            separator.find(fb)?.range?.first,
            verbInside.find(fb)?.range?.first,
            perTake.find(fb)?.range?.first,
            Regex("[(:;,]").find(fb)?.range?.first,
        ).filter { it > 0 }
        return ends.minOrNull() ?: body.length
    }

    private fun isMedicineCandidate(raw: String, f: String, nextFolded: String?): Boolean {
        if (isStop(f) || usageHeader.containsMatchIn(f) || isInstruction(f)) return false
        val fb = f.replace(numbering, "")
        val body = raw.substring(f.length - fb.length)
        val name = body.substring(0, nameEnd(body, fb)).trim()
        if (name.count { it.isLetter() } < 3 || name.length > 60) return false
        val hasDose = doseRegex.containsMatchIn(fb)
        val numbered = numbering.containsMatchIn(f) && f.firstOrNull()?.isDigit() == true
        val nextIsInstruction = nextFolded != null && isInstruction(nextFolded)
        return hasDose || (numbered && nextIsInstruction) || (nextIsInstruction && name.split(' ').size <= 4)
    }

    private fun medicines(raw: List<String>, folded: List<String>): List<PrescribedMedicine> {
        val out = mutableListOf<PrescribedMedicine>()
        var continuousSection = false
        var i = 0
        while (i < raw.size) {
            val f = folded[i]
            if (usageHeader.containsMatchIn(f)) {
                continuousSection = f.contains("continuo")
                i++
                continue
            }
            if (!isMedicineCandidate(raw[i], f, folded.getOrNull(i + 1))) {
                i++
                continue
            }
            val fb = f.replace(numbering, "")
            val body = raw[i].substring(f.length - fb.length)
            val end = nameEnd(body, fb)
            val rawName = body.substring(0, end).trim().trimEnd('-', ',', ':', '.').trim()
            // Siglas curtas ("AAS") ficam em maiúsculas.
            val name = if (rawName.length <= 4 && rawName.none { it.isLowerCase() }) rawName else TextScan.sentenceCase(rawName)
            // Concentração: do primeiro ao último valor antes do separador.
            val sep = separator.find(fb, end)?.range?.first ?: fb.length
            val left = fb.substring(0, sep)
            val doses = doseRegex.findAll(left).toList()
            val strength = if (doses.isEmpty()) null else body.substring(doses.first().range.first, doses.last().range.last + 1).replace(Regex("\\s+"), " ")
            val instruction = StringBuilder()
            // Resto da linha depois da concentração (ex.: "- tomar 1 cp de 6/6h").
            val afterDose = (doses.lastOrNull()?.range?.last?.plus(1) ?: end).coerceAtMost(body.length)
            val restLeft = body.substring(afterDose, sep.coerceAtLeast(afterDose).coerceAtMost(body.length))
            val restRight = if (sep < fb.length) body.substring(sep) else ""
            // À direita do separador costuma vir só a quantidade a comprar ("21 cápsulas"): ali exige verbo ou posologia.
            listOf(restLeft to true, restRight to false).forEach { (part, allowTake) ->
                val pf = TextScan.fold(part)
                if (verbInside.containsMatchIn(pf) || posologyHint.containsMatchIn(pf) || continuousRegex.containsMatchIn(pf) ||
                    (allowTake && perTake.containsMatchIn(pf))
                ) {
                    val start = listOfNotNull(verbInside.find(pf)?.range?.first, perTake.find(pf)?.range?.first, posologyHint.find(pf)?.range?.first).minOrNull() ?: 0
                    instruction.append(part.substring(start)).append(' ')
                }
            }
            var j = i + 1
            var taken = 0
            while (j < raw.size && taken < 4) {
                val nf = folded[j]
                if (isStop(nf) || usageHeader.containsMatchIn(nf)) break
                if (isMedicineCandidate(raw[j], nf, folded.getOrNull(j + 1))) break
                if (!isInstruction(nf) && taken == 0 && !continuousRegex.containsMatchIn(nf)) break
                instruction.append(raw[j].replace(numbering, "")).append(' ')
                taken++
                j++
            }
            out += medicine(name, strength, instruction.toString(), continuousSection, raw[i])
            i = j
        }
        return out.distinctBy { TextScan.fold(it.name) }
    }

    private fun medicine(name: String, strength: String?, instructionRaw: String, continuousSection: Boolean, line: String): PrescribedMedicine {
        var text = intervalSlash.replace(instructionRaw.replace(Regex("\\s+"), " ").trim()) { m -> "de ${m.groupValues[1]} em ${m.groupValues[1]} horas" }
        var f = TextScan.fold(text)
        val continuous = continuousSection || continuousRegex.containsMatchIn(f)
        var duration: String? = null
        var days: Int? = null
        if (continuous) {
            duration = "Uso contínuo"
            text = removeAll(text, f, continuousRegex)
            f = TextScan.fold(text)
        }
        val dm = durationRegex.findAll(f).firstOrNull { m ->
            val before = f.substring(0, m.range.first).trimEnd()
            !before.endsWith("cada") && !before.endsWith(" em") && !before.endsWith("de")
        }
        if (dm != null) {
            val n = dm.groupValues[1].toIntOrNull() ?: numberWords[dm.groupValues[1]] ?: 1
            val unit = dm.groupValues[2]
            val (label, mult) = when {
                unit.startsWith("dia") -> (if (n == 1) "dia" else "dias") to 1
                unit.startsWith("semana") -> (if (n == 1) "semana" else "semanas") to 7
                else -> (if (n == 1) "mês" else "meses") to 30
            }
            if (!continuous) {
                duration = "por $n $label"
                days = n * mult
            }
            text = text.removeRange(dm.range)
            f = TextScan.fold(text)
        }
        // Tira o verbo do começo ("Tomar 1 cápsula..." vira "1 cápsula...").
        val v = verbStart.find(f)
        if (v != null) {
            text = text.substring(v.range.last + 1)
            f = TextScan.fold(text)
        }
        val posology = text.replace(Regex("\\s+"), " ").replace(Regex("\\s+([,.;])"), "$1")
            .trim().trim(',', '.', ';', '-', ':', ' ').replace(Regex(",\\s*,"), ",").trim().trim(',', ' ')
            .takeIf { p -> p.count { it.isLetter() || it.isDigit() } >= 3 }
        val take = posology?.let { p -> perTakeStart.find(TextScan.fold(p))?.let { p.substring(it.range).trim() } }
        val dose = listOfNotNull(strength, take).joinToString(", ").ifBlank { null }
        return PrescribedMedicine(
            name = name,
            dose = dose,
            posology = posology,
            duration = duration,
            durationDays = days,
            continuous = continuous,
            asNeeded = asNeededRegex.containsMatchIn(TextScan.fold(instructionRaw)),
            line = line,
        )
    }

    private fun removeAll(text: String, folded: String, re: Regex): String {
        val sb = StringBuilder(text)
        re.findAll(folded).toList().asReversed().forEach { sb.delete(it.range.first, it.range.last + 1) }
        return sb.toString()
    }
}
