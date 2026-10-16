package com.addsoncosta.evolui.health

import java.text.Normalizer
import java.time.LocalDate

/**
 * Ajudas de texto puras (sem Android), usadas pelos leitores de receita, bioimpedância e laudos.
 * Ficam aqui para poderem ser testadas na JVM.
 */
internal object TextScan {
    /**
     * Minúsculas e sem acentos, letra por letra: o texto tem o MESMO tamanho do original,
     * então uma posição achada no texto normalizado serve para recortar o original.
     */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            sb.append((base.firstOrNull() ?: c).lowercaseChar())
        }
        return sb.toString()
    }

    /** Troca os vários tipos de traço que a leitura devolve pelo sinal de menos comum. */
    fun dashes(text: String): String =
        text.replace('−', '-').replace('–', '-').replace('—', '-').replace('‐', '-').replace('‑', '-')

    private val dateRegex = Regex("""(?<!\d)(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})(?!\d)""")

    /** Datas válidas de uma linha, na ordem em que aparecem. */
    fun dates(line: String, today: LocalDate = LocalDate.now()): List<LocalDate> = dateRegex.findAll(line).mapNotNull { m ->
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        var y = m.groupValues[3].toInt()
        if (y < 100) y += 2000
        runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }.filter { it.year >= 1990 && !it.isAfter(today) }.toList()

    /**
     * Data do documento: a da linha com uma palavra-chave (realizado, data do exame...);
     * sem palavra-chave, a mais recente (a de nascimento costuma ser antiga).
     */
    fun findDate(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val lines = text.lines().map { fold(it) }
        val keywords = listOf("data do exame", "realizado", "realizacao", "data da consulta", "atendimento", "colet", "data:", "emissao", "data ")
        for (line in lines) {
            if (line.contains("nasc")) continue
            if (keywords.any { line.contains(it) }) dates(line, today).firstOrNull()?.let { return it }
        }
        return lines.filter { !it.contains("nasc") }.flatMap { dates(it, today) }.maxOrNull()
    }

    /** "ULTRASSONOGRAFIA DE ABDOME" vira "Ultrassonografia de abdome"; texto misto fica como está. */
    fun sentenceCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
        val small = setOf("de", "da", "do", "das", "dos", "e", "com", "sem", "a", "o", "em", "na", "no", "para")
        return s.lowercase().split(' ').mapIndexed { i, w ->
            when {
                i == 0 -> w.replaceFirstChar { it.uppercase() }
                w in small -> w
                w.length <= 3 && w.none { it.isDigit() } && w.all { it.isLetter() } && w !in small -> w.uppercase() // siglas: USG, RM, TC
                else -> w
            }
        }.joinToString(" ")
    }
}

/** O essencial de um laudo de imagem ou procedimento (ultrassom, ressonância, biópsia...). */
data class ReportSummary(
    val examName: String? = null,
    val date: LocalDate? = null,
    val place: String? = null,
    val requestingDoctor: String? = null,
    val reportingDoctor: String? = null,
    val conclusion: String? = null,
    /** O nome do exame foi reconhecido como exame de imagem/procedimento. */
    val looksLikeReport: Boolean = false,
) {
    val isEmpty get() = examName == null && date == null && place == null && requestingDoctor == null &&
        reportingDoctor == null && conclusion == null
}

/**
 * Lê um laudo e guarda só o essencial: nome do exame, data, clínica, médicos e a conclusão.
 * O texto completo continua só no anexo (para a busca).
 */
object ReportSummaryParser {
    const val MAX_CONCLUSION = 1200

    /** Palavras (já sem acento) que indicam exame de imagem ou procedimento, e o nome para mostrar. */
    private val examKeywords: List<Pair<Regex, String>> = listOf(
        "ultrassonografia|ultrassom|ultra-som|ultra som|ecografia|usg" to "Ultrassonografia",
        "ressonancia magnetica|ressonancia|rnm" to "Ressonância magnética",
        "tomografia computadorizada|tomografia|angiotomografia" to "Tomografia",
        "radiografia|raio-x|raio x|raios-x|rx de|rx do|rx da" to "Raio-X",
        "ecocardiograma|ecocardiografia|ecodopplercardiograma" to "Ecocardiograma",
        "endoscopia digestiva|endoscopia|esofagogastroduodenoscopia" to "Endoscopia",
        "colonoscopia|retossigmoidoscopia" to "Colonoscopia",
        "anatomopatologico|anatomo-patologico|histopatologico|biopsia|citopatologico|papanicolau" to "Biópsia / anatomopatológico",
        "mamografia" to "Mamografia",
        "densitometria" to "Densitometria óssea",
        "eletrocardiograma|ecg" to "Eletrocardiograma",
        "holter" to "Holter",
        "monitorizacao ambulatorial da pressao|m\\.a\\.p\\.a" to "MAPA",
        "teste ergometrico|ergometria|teste de esforco" to "Teste ergométrico",
        "cintilografia" to "Cintilografia",
        "doppler|duplex scan" to "Doppler",
        "elastografia" to "Elastografia",
        "pet-ct|pet ct|pet/ct" to "PET-CT",
        "eletroencefalograma|eeg" to "Eletroencefalograma",
        "eletroneuromiografia" to "Eletroneuromiografia",
        "espirometria|prova de funcao pulmonar" to "Espirometria",
        "polissonografia" to "Polissonografia",
    ).map { (k, v) -> Regex("(?<![a-z])(?:$k)(?![a-z])") to v }

    /** Títulos da parte que interessa, do mais forte para o mais fraco. Os fracos exigem ":" ou fim da linha. */
    private val headings: List<Regex> = listOf(
        "conclusao diagnostica|conclusoes|conclusao" to false,
        "impressoes diagnosticas|impressao diagnostica|impressao" to false,
        "opiniao diagnostica|opiniao" to false,
        "parecer" to true,
        "diagnostico anatomopatologico|diagnostico histopatologico|diagnostico final|diagnostico" to true,
        "resumo" to true,
    ).map { (k, strict) ->
        val end = if (strict) "\\s*(?::|$)" else "(?![a-z])\\s*[:.\\-]?"
        Regex("^\\s*[-•*]?\\s*(?:$k)$end\\s*")
    }

    /** Linhas que encerram a conclusão: assinatura, rodapé, outra seção. */
    private val stopLine = Regex(
        "(?<![a-z])(crm|cremesp|rqe|dr\\.|dra\\.|dr |dra |medico responsavel|medica responsavel|assinado|assinatura|" +
            "laudo assinado|eletronicamente|pagina|pag\\.|www\\.|http|telefone|tel\\.|tel:|endereco|cep:|e-mail|email|" +
            "nota:|notas:|observacao:|observacoes:|obs:|obs\\.:|referencia|referencias|bibliografia|" +
            "metodo:|tecnica:|achados:|relatorio:|indicacao:|exame:|paciente:|liberado|conferido|revisado por)",
    )

    private val requestRegex = Regex("(?:medic[oa] solicitante|solicitante|requisitante|solicitado por|medic[oa] assistente|encaminhado por|requisitado por)\\s*[:\\-]?\\s*")
    private val placeWords = Regex("(?<![a-z])(clinica|hospital|instituto|centro de diagnostico|centro de imagem|diagnosticos|diagnostico por imagem|imagem|radiologia|medicina diagnostica|laboratorio|policlinica)(?![a-z])")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReportSummary {
        val raw = TextScan.dashes(text).lines().map { it.trim() }
        val nonEmpty = raw.filter { it.isNotEmpty() }
        val folded = nonEmpty.map { TextScan.fold(it) }
        val (name, isReport) = examName(nonEmpty, folded)
        return ReportSummary(
            examName = name,
            date = TextScan.findDate(text, today),
            place = place(nonEmpty, folded),
            requestingDoctor = requesting(nonEmpty, folded),
            reportingDoctor = reporting(nonEmpty, folded),
            conclusion = conclusion(raw),
            looksLikeReport = isReport,
        )
    }

    /** O texto parece um laudo de imagem/procedimento (e não um exame de sangue)? */
    fun isComplexReport(text: String): Boolean {
        val f = TextScan.fold(text)
        return examKeywords.any { it.first.containsMatchIn(f) }
    }

    private fun examName(lines: List<String>, folded: List<String>): Pair<String?, Boolean> {
        val head = folded.take(30)
        for ((i, line) in head.withIndex()) {
            if (line.startsWith("indicacao") || line.contains("hipotese")) continue
            for ((re, label) in examKeywords) {
                val m = re.find(line) ?: continue
                // Usa a linha do laudo (mais específica, ex.: "Ultrassonografia de abdome total") quando é curta.
                val original = lines[i].substring(m.range.first).trim().trimEnd(':', '.', '-').trim()
                val clean = original.replace(Regex("\\s+"), " ")
                val name = if (clean.length in 4..70 && !clean.contains(Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"))) TextScan.sentenceCase(clean) else label
                return name to true
            }
        }
        return null to false
    }

    private fun place(lines: List<String>, folded: List<String>): String? {
        for (i in folded.indices.take(12)) {
            val f = folded[i]
            if (f.contains("paciente") || f.contains("solicitante") || f.contains("convenio")) continue
            if (placeWords.containsMatchIn(f) && lines[i].length <= 70 && examKeywords.none { it.first.containsMatchIn(f) }) {
                return lines[i].trim().trimEnd(':', '-').trim()
            }
        }
        return null
    }

    /** Nome depois de "Solicitante:", sem o CRM. */
    private fun requesting(lines: List<String>, folded: List<String>): String? {
        for (i in folded.indices) {
            val m = requestRegex.find(folded[i]) ?: continue
            val rest = lines[i].substring(m.range.last + 1)
            val name = cleanName(rest)
            if (name != null) return name
        }
        return null
    }

    /** Médico que assinou: a linha com CRM perto do fim do laudo (ou a linha logo antes dela). */
    private fun reporting(lines: List<String>, folded: List<String>): String? {
        val crm = Regex("(?<![a-z])(crm|cremesp)(?![a-z])")
        for (i in folded.indices.reversed()) {
            val f = folded[i]
            if (requestRegex.containsMatchIn(f)) continue
            val m = crm.find(f) ?: continue
            cleanName(lines[i].substring(0, m.range.first))?.let { return it }
            if (i > 0 && !requestRegex.containsMatchIn(folded[i - 1])) cleanName(lines[i - 1])?.let { return it }
        }
        val signed = Regex("(?:laudado por|medic[oa] responsavel|responsavel tecnico|assinado (?:eletronicamente )?por)\\s*[:\\-]?\\s*")
        for (i in folded.indices.reversed()) {
            val m = signed.find(folded[i]) ?: continue
            cleanName(lines[i].substring(m.range.last + 1))?.let { return it }
        }
        return null
    }

    private fun cleanName(s: String): String? {
        var t = s.substringBefore("CRM").substringBefore("crm").substringBefore("Crm")
        t = t.replace(Regex("[|]"), " ").trim().trim('-', ':', ',', '/', '.', ' ')
        t = t.replace(Regex("\\s+"), " ")
        val letters = t.count { it.isLetter() }
        if (letters < 4 || t.length > 60) return null
        if (t.count { it.isDigit() } > 2) return null
        return t
    }

    /** A seção "Conclusão" (ou "Impressão diagnóstica", "Opinião"...); sem ela, o último parágrafo com conteúdo. */
    fun conclusion(rawLines: List<String>): String? {
        val lines = rawLines.map { it.trim() }
        val folded = lines.map { TextScan.fold(it) }
        for (h in headings) {
            val idx = folded.indices.lastOrNull { h.containsMatchIn(folded[it]) && !folded[it].contains("hipotese") } ?: continue
            val m = h.find(folded[idx])!!
            val parts = mutableListOf<String>()
            val sameLine = lines[idx].substring(m.range.last + 1).trim()
            if (sameLine.isNotEmpty()) parts += sameLine
            var blanks = 0
            for (j in idx + 1 until lines.size) {
                val l = lines[j]
                if (l.isEmpty()) {
                    blanks++
                    if (parts.isNotEmpty() && blanks >= 2) break
                    continue
                }
                blanks = 0
                val f = folded[j]
                if (stopLine.containsMatchIn(f.take(40)) || isHeading(l)) break
                parts += l
                if (parts.size >= 15) break
            }
            val text = joinParagraph(parts)
            if (text.length >= 3) return trim(text)
        }
        return lastParagraph(lines, folded)
    }

    /** Linha que parece título de outra seção, ex.: "ACHADOS:" ou "Técnica". */
    private fun isHeading(line: String): Boolean {
        val t = line.trim()
        if (t.endsWith(":") && t.length <= 30 && t.count { it.isLetter() } >= 4) return true
        return false
    }

    private fun lastParagraph(lines: List<String>, folded: List<String>): String? {
        val paragraphs = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        for (i in lines.indices) {
            val l = lines[i]
            val meaningful = l.length >= 20 && l.count { it.isLetter() } >= 12 && !stopLine.containsMatchIn(folded[i].take(40))
            if (meaningful) cur += l else if (cur.isNotEmpty()) { paragraphs += cur; cur = mutableListOf() }
        }
        if (cur.isNotEmpty()) paragraphs += cur
        val last = paragraphs.lastOrNull { p -> p.sumOf { it.length } >= 30 } ?: return null
        return trim(joinParagraph(last.takeLast(5)))
    }

    /** Junta as linhas quebradas; itens de lista ("-", "•", "1.") ficam em linhas próprias. */
    private fun joinParagraph(parts: List<String>): String {
        val bullet = Regex("^([-•*]|\\d{1,2}[.)-])\\s+")
        val sb = StringBuilder()
        for (p in parts) {
            if (sb.isEmpty()) sb.append(p)
            else if (bullet.containsMatchIn(p)) sb.append('\n').append(p)
            else if (sb.endsWith("-") && !sb.endsWith(" -")) { sb.setLength(sb.length - 1); sb.append(p) }
            else sb.append(' ').append(p)
        }
        return sb.toString().replace(Regex("[ \\t]+"), " ").trim()
    }

    private fun trim(s: String): String {
        val t = s.trim().trim('-', ':', ' ')
        if (t.length <= MAX_CONCLUSION) return t
        val cut = t.substring(0, MAX_CONCLUSION)
        val dot = cut.lastIndexOf('.')
        return if (dot > MAX_CONCLUSION / 2) cut.substring(0, dot + 1) else "$cut…"
    }
}
