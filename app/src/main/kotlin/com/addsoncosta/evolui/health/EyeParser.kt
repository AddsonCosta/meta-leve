package com.addsoncosta.evolui.health

import java.time.LocalDate
import java.util.Locale

/** Valores de um olho na receita de óculos, já no formato de mostrar (ex.: "-2,00", "180", "20/20"). */
data class EyeValues(
    val esferico: String? = null,
    val cilindrico: String? = null,
    val eixo: String? = null,
    val adicao: String? = null,
    val acuidade: String? = null,
    val pio: String? = null,
) {
    val isEmpty get() = esferico == null && cilindrico == null && eixo == null && adicao == null && acuidade == null && pio == null

    /** "-2,00 -0,75 x 180", para listas e relatórios. */
    fun refraction(): String = listOfNotNull(esferico, cilindrico, eixo?.let { "x $it°" }).joinToString(" ")
}

/** O que foi possível ler de uma receita de óculos ou relatório oftalmológico. */
data class EyeExam(
    val od: EyeValues = EyeValues(),
    val oe: EyeValues = EyeValues(),
    val dnp: String? = null,
    val diagnoses: List<String> = emptyList(),
    val date: LocalDate? = null,
) {
    val isEmpty get() = od.isEmpty && oe.isEmpty && dnp == null && diagnoses.isEmpty() && date == null

    /** Campos para o JSON `extra` (mesmas chaves do formulário de Oftalmologia). */
    fun toExtras(): Map<String, String> = buildMap {
        fun put(eye: String, v: EyeValues) {
            v.esferico?.let { put("${eye}_esferico", it) }
            v.cilindrico?.let { put("${eye}_cilindrico", it) }
            v.eixo?.let { put("${eye}_eixo", it) }
            v.adicao?.let { put("${eye}_adicao", it) }
            v.acuidade?.let { put("${eye}_av", it) }
            v.pio?.let { put("${eye}_pio", it) }
        }
        put("od", od)
        put("oe", oe)
        dnp?.let { put("dnp", it) }
        if (diagnoses.isNotEmpty()) put(EyeParser.DIAGNOSES_KEY, diagnoses.joinToString(", "))
    }
}

/**
 * Lê receitas de óculos e relatórios do oftalmologista. Entende formatos comuns como
 * "OD -2,00 -0,75 x 180", "OE: esf -1,50 cil -0,50 eixo 90", "AV 20/20", "PIO 14 mmHg", "Adição +2,00"
 * e tabelas com OD/OE nas linhas. O usuário sempre confere antes de salvar.
 */
object EyeParser {
    const val DIAGNOSES_KEY = "diagnosticos"

    /** Diagnósticos comuns (nome para mostrar e formas de escrever, sem acento). */
    val knownDiagnoses: List<Pair<String, List<String>>> = listOf(
        "Miopia" to listOf("miopia", "miope", "miopico"),
        "Hipermetropia" to listOf("hipermetropia", "hipermetrope", "hipermetropico"),
        "Astigmatismo" to listOf("astigmatismo"),
        "Presbiopia" to listOf("presbiopia", "vista cansada", "presbita"),
        "Catarata" to listOf("catarata", "facoesclerose", "opacidade do cristalino"),
        "Glaucoma" to listOf("glaucoma"),
        "Ceratocone" to listOf("ceratocone", "queratocone"),
        "Retinopatia diabética" to listOf("retinopatia diabetica"),
        "Degeneração macular" to listOf("degeneracao macular", "dmri", "maculopatia"),
        "Olho seco" to listOf("olho seco", "sindrome do olho seco", "ceratoconjuntivite seca"),
        "Conjuntivite" to listOf("conjuntivite"),
        "Estrabismo" to listOf("estrabismo"),
        "Ambliopia" to listOf("ambliopia", "olho preguicoso"),
        "Pterígio" to listOf("pterigio"),
        "Blefarite" to listOf("blefarite"),
        "Descolamento de retina" to listOf("descolamento de retina"),
        "Hipertensão ocular" to listOf("hipertensao ocular"),
    )

    private class Builder {
        var esf: String? = null
        var cil: String? = null
        var eixo: String? = null
        var add: String? = null
        var av: String? = null
        var pio: String? = null
        fun build() = EyeValues(esf, cil, eixo, add, av, pio)
    }

    private enum class Eye { OD, OE }

    // Marcadores de olho (no texto sem acento e minúsculo). "0" no lugar de "o" é erro comum da leitura.
    private val eyeMarker = Regex(
        "(?<![a-z0-9])(?:(olho\\s+direito|o\\.?\\s?d\\.?|0\\.?d\\.?|direito|dir\\.)|(olho\\s+esquerdo|o\\.?e\\.?|0\\.?e\\.?|esquerdo|esq\\.))(?![a-z])",
    )
    // Grau: -2,00 / +0,75 / -10.25 / 2,00. Com sinal pode ter 1 ou 2 casas; sem sinal precisa de 2.
    private val diopter = Regex("(?<![\\d/,.])(?:([+-])\\s?(\\d{1,2}(?:[.,]\\d{1,2})?)|(\\d{1,2}[.,]\\d{2}))(?![\\d/])")
    private val plano = Regex("(?<![a-z])(plano|pl|neutro)(?![a-z])")
    private val axisLabeled = Regex("(?:(?<![a-z])eixo|(?<![a-z])x|×|\\*)\\s*:?\\s*(\\d{1,3})(?!\\d)\\s*°?")
    private val axisDegree = Regex("(?<![\\d,.])(\\d{1,3})\\s*[°º]")
    private val acuity = Regex("(?<!\\d)20\\s*/\\s*(\\d{2,3})(?!\\d)")
    private val acuityDecimal = Regex("(?<![\\d,.])([01][.,]\\d{1,2})(?![\\d])")
    private val jaeger = Regex("(?<![a-z])j\\s?([1-9])(?!\\d)")
    private val pioNumber = Regex("(?<![\\d,.])(\\d{1,2}(?:[.,]\\d)?)(?![\\d,.]?\\d)")
    private val esfLabel = Regex("(?<![a-z])(esf(?:erico)?|esferico|sph|esf\\.)\\s*:?\\s*")
    private val cilLabel = Regex("(?<![a-z])(cil(?:indrico)?|cyl|cil\\.)\\s*:?\\s*")
    private val addLabel = Regex("(?<![a-z])(adicao|add|adic\\.?|ad\\.)(?![a-z])\\s*:?\\s*")
    private val avLabel = Regex("(?<![a-z])(av|a\\.v\\.|acuidade visual|acuidade)(?![a-z])")
    private val pioLabel = Regex("(?<![a-z])(pio|p\\.i\\.o\\.|pressao intraocular|pressao intra-ocular|pressao ocular|tonometria|tono)(?![a-z])")
    private val dnpRegex = Regex("(?<![a-z])(dnp|d\\.n\\.p\\.|dp|distancia pupilar|distancia interpupilar|dip)(?![a-z])\\s*:?\\s*(?:od\\s*)?(\\d{2}(?:[.,]\\d)?)(?:\\s*(?:mm)?\\s*(?:/|-|oe|e)\\s*(\\d{2}(?:[.,]\\d)?))?")
    private val nearWords = Regex("(?<![a-z])(perto|leitura|para perto|near)(?![a-z])")
    private val farWords = Regex("(?<![a-z])(longe|para longe|distancia)(?![a-z])")

    fun parse(text: String, today: LocalDate = LocalDate.now()): EyeExam {
        val od = Builder()
        val oe = Builder()
        fun b(e: Eye) = if (e == Eye.OD) od else oe
        var dnp: String? = null
        var near = false

        val lines = TextScan.dashes(text).lines().map { TextScan.fold(it).trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (nearWords.containsMatchIn(line)) near = true
            if (farWords.containsMatchIn(line) && !line.contains("pupilar")) near = false

            if (dnp == null) dnpRegex.find(line)?.let { m ->
                val a = m.groupValues[2].replace('.', ',')
                val c = m.groupValues[3].replace('.', ',')
                val first = a.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (first in 20.0..80.0) dnp = if (c.isNotEmpty()) "$a/$c" else a
            }

            val lineHasPio = pioLabel.containsMatchIn(line) || line.contains("mmhg")
            val lineHasAv = avLabel.containsMatchIn(line)
            val lineHasAdd = addLabel.containsMatchIn(line)

            val markers = eyeMarker.findAll(line).toList()
            if (markers.isEmpty()) {
                // Linha sem OD/OE: valores que valem para os dois olhos (ou OD e OE nessa ordem).
                if (lineHasAdd) {
                    val vals = addValues(line.substring(addLabel.find(line)!!.range.last + 1))
                    if (vals.isNotEmpty()) {
                        if (od.add == null) od.add = vals[0]
                        if (oe.add == null) oe.add = vals.getOrElse(1) { vals[0] }
                    }
                }
                if (lineHasPio) {
                    val start = pioLabel.find(line)?.range?.last?.plus(1) ?: 0
                    val vals = pioValues(line.substring(start))
                    if (vals.isNotEmpty()) {
                        if (od.pio == null) od.pio = vals[0]
                        if (oe.pio == null) oe.pio = vals.getOrElse(1) { vals[0] }
                    }
                }
                if (lineHasAv) {
                    val tail = line.substring(avLabel.find(line)!!.range.last + 1)
                    val vals = if (lineHasPio) acuity.findAll(tail).map { "20/" + it.groupValues[1] }.take(2).toList() else acuityValues(tail)
                    if (vals.isNotEmpty()) {
                        if (od.av == null) od.av = vals[0]
                        if (oe.av == null) oe.av = vals.getOrElse(1) { vals[0] }
                    }
                }
                continue
            }

            // Linha com OD/OE: cada pedaço vai do marcador até o próximo.
            markers.forEachIndexed { i, m ->
                val eye = if (m.groupValues[1].isNotEmpty()) Eye.OD else Eye.OE
                val end = if (i + 1 < markers.size) markers[i + 1].range.first else line.length
                val seg = line.substring(m.range.last + 1, end)
                val prefix = if (i == 0) line.substring(0, m.range.first) else ""
                readSegment(b(eye), seg, prefix, lineHasPio, lineHasAv, lineHasAdd, near)
            }
        }

        // A adição costuma ser a mesma nos dois olhos; se só um tem, vale para o outro.
        if (od.add == null && oe.add != null && od.esf != null) od.add = oe.add
        if (oe.add == null && od.add != null && oe.esf != null) oe.add = od.add
        return EyeExam(od.build(), oe.build(), dnp, diagnoses(text), TextScan.findDate(text, today))
    }

    private fun readSegment(
        e: Builder,
        segIn: String,
        prefix: String,
        lineHasPio: Boolean,
        lineHasAv: Boolean,
        lineHasAdd: Boolean,
        near: Boolean,
    ) {
        var seg = " $segIn "
        val prefixPio = pioLabel.containsMatchIn(prefix) || (lineHasPio && !esfLabel.containsMatchIn(seg) && diopter.findAll(seg).none { it.groupValues[1].isNotEmpty() })
        val prefixAv = avLabel.containsMatchIn(prefix) ||
            (lineHasAv && !lineHasPio && !esfLabel.containsMatchIn(seg) && diopter.findAll(seg).none { it.groupValues[1].isNotEmpty() })
        val prefixAdd = addLabel.containsMatchIn(prefix)

        // Acuidade visual: "20/20" é inconfundível; decimal ("1,0") e Jaeger ("J1") só com o rótulo AV.
        acuity.find(seg)?.let { m ->
            if (e.av == null) e.av = "20/" + m.groupValues[1]
            seg = seg.replaceRange(m.range, " ")
        }
        val avIdx = avLabel.find(seg)
        if (avIdx != null || prefixAv) {
            val tail = if (avIdx != null) seg.substring(avIdx.range.last + 1) else seg
            if (e.av == null) {
                acuityDecimal.find(tail)?.let { e.av = it.groupValues[1].replace('.', ',') }
                    ?: jaeger.find(tail)?.let { e.av = "J" + it.groupValues[1] }
            }
            if (avIdx != null) seg = seg.substring(0, avIdx.range.first) + " " + stripFirstValue(seg.substring(avIdx.range.first))
            else if (!lineHasPio && !lineHasAdd && diopter.find(seg) == null) return
        }

        // Pressão intraocular.
        val pioIdx = pioLabel.find(seg)
        if (pioIdx != null || prefixPio) {
            val tail = if (pioIdx != null) seg.substring(pioIdx.range.last + 1) else seg
            val v = pioValues(tail).firstOrNull()
            if (v != null && e.pio == null) e.pio = v
            if (pioIdx != null) seg = seg.substring(0, pioIdx.range.first)
            else return
        }

        // Adição.
        val addIdx = addLabel.find(seg)
        if (addIdx != null || prefixAdd) {
            val tail = if (addIdx != null) seg.substring(addIdx.range.last + 1) else seg
            val v = addValues(tail).firstOrNull()
            if (v != null && e.add == null) e.add = v
            if (addIdx != null) seg = seg.substring(0, addIdx.range.first) else return
        }
        if (lineHasAdd && addIdx == null && !prefixAdd && esfLabel.find(seg) == null && diopter.findAll(seg).count() == 1) {
            // "Adição   OD +2,00   OE +2,00": o rótulo está antes do primeiro marcador.
            addValues(seg).firstOrNull()?.let { if (e.add == null) e.add = it }
            return
        }

        // Refração: esférico, cilíndrico e eixo, com rótulos ou pela posição.
        var esf: String? = null
        var cil: String? = null
        var axis: String? = null
        esfLabel.find(seg)?.let { m -> valueAfter(seg, m.range.last + 1)?.let { esf = it } }
        cilLabel.find(seg)?.let { m -> valueAfter(seg, m.range.last + 1)?.let { cil = it } }
        axisLabeled.find(seg)?.let { m -> axis = axisText(m.groupValues[1]) }
            ?: axisDegree.find(seg)?.let { m -> axis = axisText(m.groupValues[1]) }

        if (esf == null && cil == null) {
            val items = mutableListOf<Pair<Int, String>>()
            plano.findAll(seg).forEach { items += it.range.first to "0,00" }
            diopter.findAll(seg).forEach { m ->
                // Não confunde o eixo ("x 180") nem a DNP com grau.
                if (m.groupValues[1].isEmpty() && m.groupValues[3].isEmpty()) return@forEach
                items += m.range.first to fmt(m)
            }
            items.sortBy { it.first }
            esf = items.getOrNull(0)?.second
            cil = items.getOrNull(1)?.second
            if (axis == null && cil != null) {
                // Eixo sem "x": o primeiro número inteiro de 0 a 180 depois do cilíndrico.
                val afterCil = items[1].first
                Regex("(?<![\\d,.+-])(\\d{1,3})(?![\\d,.])").findAll(seg).firstOrNull { it.range.first > afterCil + 2 }
                    ?.let { m -> axisText(m.groupValues[1])?.let { axis = it } }
            }
        }
        if (esf == null && cil == null && axis == null) return

        if (near) {
            // Linha "Perto": guarda a adição (diferença para o grau de longe) e não troca o grau de longe.
            val farEsf = e.esf?.let { num(it) }
            val nearEsf = esf?.let { num(it) }
            if (e.add == null && farEsf != null && nearEsf != null && nearEsf > farEsf) e.add = signed(nearEsf - farEsf)
            if (e.esf != null) return
        }
        if (e.esf == null && e.cil == null) {
            e.esf = esf
            e.cil = cil
            e.eixo = axis
        } else {
            if (e.cil == null && cil != null) e.cil = cil
            if (e.eixo == null && axis != null) e.eixo = axis
        }
    }

    /** Tira o primeiro valor depois de um rótulo (para não ser lido como grau). */
    private fun stripFirstValue(s: String): String =
        s.replaceFirst(Regex("^[a-z. ]*:?\\s*([01][.,]\\d{1,2}|j\\s?\\d|\\d{1,2})?"), " ")

    private fun valueAfter(seg: String, from: Int): String? {
        val tail = seg.substring(from).take(14)
        if (plano.find(tail)?.range?.first?.let { it <= 2 } == true) return "0,00"
        val m = diopter.find(tail) ?: return null
        if (m.range.first > 4) return null
        return fmt(m)
    }

    private fun fmt(m: MatchResult): String {
        val sign = m.groupValues[1]
        val body = m.groupValues[2].ifEmpty { m.groupValues[3] }
        val v = body.replace(',', '.').toDoubleOrNull() ?: return body
        return signed(if (sign == "-") -v else v)
    }

    private fun signed(v: Double): String {
        if (kotlin.math.abs(v) < 0.001) return "0,00"
        return String.format(Locale.ROOT, "%+.2f", v).replace('.', ',')
    }

    private fun num(s: String): Double? = s.replace(',', '.').replace("+", "").toDoubleOrNull()

    private fun axisText(s: String): String? {
        val v = s.toIntOrNull() ?: return null
        return if (v in 0..180) v.toString() else null
    }

    private fun addValues(s: String): List<String> =
        diopter.findAll(s).map { fmt(it) }.filter { (num(it) ?: 0.0) in 0.25..4.0 }.take(2).toList()

    private fun pioValues(s: String): List<String> =
        pioNumber.findAll(s).map { it.groupValues[1].replace('.', ',') }.filter { (num(it) ?: 0.0) in 4.0..60.0 }.take(2).toList()

    private fun acuityValues(s: String): List<String> {
        val out = acuity.findAll(s).map { "20/" + it.groupValues[1] }.toList()
        if (out.isNotEmpty()) return out.take(2)
        return acuityDecimal.findAll(s).map { it.groupValues[1].replace('.', ',') }.take(2).toList()
    }

    /** Diagnósticos citados no texto (sem repetir), na ordem da lista. */
    fun diagnoses(text: String): List<String> {
        val f = TextScan.fold(text)
        return knownDiagnoses.filter { (_, forms) ->
            forms.any { Regex("(?<![a-z])" + Regex.escape(it) + "(?![a-z])").containsMatchIn(f) }
        }.map { it.first }
    }
}
