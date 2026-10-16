package com.addsoncosta.evolui.health

import kotlin.math.roundToInt

/**
 * Classificação da pressão arterial no consultório pela Diretriz Brasileira de Hipertensão Arterial (2020).
 * Quando a sistólica e a diastólica caem em categorias diferentes, vale a mais alta.
 * O [name] não é gravado no banco; pode mudar.
 */
enum class BpCategory(val label: String, val short: String) {
    OTIMA("Pressão ótima", "Ótima"),
    NORMAL("Pressão normal", "Normal"),
    PRE_HIPERTENSAO("Pré-hipertensão", "Pré-hipertensão"),
    ESTAGIO_1("Hipertensão estágio 1", "HA estágio 1"),
    ESTAGIO_2("Hipertensão estágio 2", "HA estágio 2"),
    ESTAGIO_3("Hipertensão estágio 3", "HA estágio 3");

    companion object {
        fun of(systolic: Int, diastolic: Int): BpCategory {
            val bySys = when {
                systolic >= 180 -> ESTAGIO_3
                systolic >= 160 -> ESTAGIO_2
                systolic >= 140 -> ESTAGIO_1
                systolic >= 130 -> PRE_HIPERTENSAO
                systolic >= 120 -> NORMAL
                else -> OTIMA
            }
            val byDia = when {
                diastolic >= 110 -> ESTAGIO_3
                diastolic >= 100 -> ESTAGIO_2
                diastolic >= 90 -> ESTAGIO_1
                diastolic >= 85 -> PRE_HIPERTENSAO
                diastolic >= 80 -> NORMAL
                else -> OTIMA
            }
            return if (bySys.ordinal >= byDia.ordinal) bySys else byDia
        }
    }
}

/** Uma leitura encontrada no texto da foto. [source] é o trecho em que ela foi achada. */
data class PressureGuess(val systolic: Int, val diastolic: Int, val pulse: Int?, val source: String)

/**
 * Procura valores de pressão no texto lido de uma foto (visor do aparelho ou anotação à mão).
 * Não depende do Android, para ser testado na JVM.
 *
 * Ordem de confiança:
 * 1. rótulos do aparelho: "SYS 128 DIA 82 PUL 70" (também SIS, MAX/MIN, PULSO, BPM...);
 * 2. escrita "128/82", "128 x 82", "128 por 82" (e "12x8" em cmHg, como se fala no Brasil);
 * 3. números empilhados no visor: o primeiro plausível como sistólica (70 a 250), depois a diastólica
 *    (40 a 150, menor que a sistólica) e o pulso (30 a 200).
 */
object PressureParser {
    val SYS_RANGE = 70..250
    val DIA_RANGE = 40..150
    val PULSE_RANGE = 30..200

    /** A melhor leitura encontrada, ou nulo. */
    fun parse(text: String): PressureGuess? = candidates(text).firstOrNull()

    /** Todas as leituras encontradas, da mais confiável para a menos (sem repetidas). */
    fun candidates(text: String): List<PressureGuess> {
        if (text.isBlank()) return emptyList()
        val clean = normalize(text)
        val out = mutableListOf<PressureGuess>()
        labeled(clean)?.let { out += it }
        val pulseLabel = labeledPulse(clean)
        val slashes = slashed(clean, pulseLabel)
        out += slashes.filter { !it.cm }.map { it.guess }
        out += stacked(clean)
        if (out.isEmpty()) out += slashes.filter { it.cm }.map { it.guess }
        return out.distinctBy { Triple(it.systolic, it.diastolic, it.pulse) }
            .let { list ->
                // A mesma pressão com e sem pulso: fica só a que tem pulso.
                list.filter { g -> g.pulse != null || list.none { it !== g && it.systolic == g.systolic && it.diastolic == g.diastolic && it.pulse != null } }
            }
    }

    fun plausible(systolic: Int, diastolic: Int): Boolean =
        systolic in SYS_RANGE && diastolic in DIA_RANGE && systolic - diastolic >= 10

    // ---- Normalização ----

    private val confusable = mapOf('O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0', 'I' to '1', 'l' to '1', '|' to '1', 'i' to '1', 'S' to '5', 's' to '5', 'B' to '8', 'Z' to '2', 'z' to '2', 'g' to '9')

    /**
     * Troca letras que a leitura confunde com dígitos ("1O8" vira "108"), só em pedaços que já
     * têm pelo menos um dígito e estão soltos, para não estragar palavras como "SYS" ou "DIA".
     */
    internal fun normalize(text: String): String {
        val fixed = Regex("(?<![A-Za-z0-9])[0-9OoDQIl|iSsBZzg]{2,3}(?![A-Za-z0-9])").replace(text) { m ->
            val s = m.value
            val digits = s.count { it.isDigit() }
            if (digits >= 1 && digits < s.length) s.map { c -> confusable[c] ?: c }.joinToString("") else s
        }
        return fixed.replace('×', 'x').replace('✕', 'x')
    }

    // ---- 1. Rótulos ----

    /** Número de 2 ou 3 dígitos que não é parte de data, hora ou decimal ("70/min" vale). */
    private const val NUM = "(\\d{2,3})(?!\\d|[.,:]\\d|/\\d)"
    private const val GAP = "[^\\d]{0,24}?"
    private val sysLabel = Regex("(?i)\\b(SYS|SIS|SIST[OÓ]LICA|S[IY]ST|M[AÁ]X(?:IMA)?)(?![A-Za-z])$GAP(?<![\\d/])$NUM")
    private val diaLabel = Regex("(?i)\\b(DIA|DIAS|DI[AÁ]ST[OÓ]LICA|M[IÍ]N(?:IMA)?)(?![A-Za-z])$GAP(?<![\\d/])$NUM")
    private val pulseLabel = Regex("(?i)\\b(PUL|PULSE|PULSO|PR|HR|FC|BPM|P/MIN|BATIMENTOS)(?![A-Za-z])$GAP(?<![\\d/])$NUM")
    private val pulseAfter = Regex("(?i)(?<![\\d/.,:])(\\d{2,3})\\s*(BPM|P/MIN|/MIN|PULSO|PUL)\\b")

    private fun labeled(text: String): PressureGuess? {
        val s = sysLabel.find(text) ?: return null
        val d = diaLabel.find(text, s.range.first + s.groupValues[1].length) ?: diaLabel.find(text) ?: return null
        val sys = s.groupValues[2].toInt()
        val dia = d.groupValues[2].toInt()
        if (!plausible(sys, dia)) return null
        val pulse = labeledPulse(text)
        val start = minOf(s.range.first, d.range.first)
        val end = maxOf(s.range.last, d.range.last)
        return PressureGuess(sys, dia, pulse, oneLine(text.substring(start, end + 1)))
    }

    private fun labeledPulse(text: String): Int? {
        val v = pulseLabel.find(text)?.groupValues?.get(2) ?: pulseAfter.find(text)?.groupValues?.get(1)
        return v?.toInt()?.takeIf { it in PULSE_RANGE }
    }

    // ---- 2. "128/82", "128 x 82", "12x8" ----

    private class Slash(val guess: PressureGuess, val cm: Boolean)

    private val slash = Regex(
        "(?i)(?<![\\d/.,:])(\\d{1,3}(?:[.,]\\d)?)\\s*(/|\\\\|x|por)\\s*(\\d{1,3}(?:[.,]\\d)?)(?![\\d/:]|[.,]\\d)" +
            "(?:[ \\t]*(?:mm\\s*hg|cm\\s*hg)?[ \\t,;-]*(?:(?:p|pulso|fc|bpm|pul)[ \\t:=]*)?(\\d{2,3})(?![\\d/.,:]))?"
    )

    private fun slashed(text: String, pulseLabel: Int?): List<Slash> = slash.findAll(text).mapNotNull { m ->
        val a = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val b = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        var sys = a.roundToInt()
        var dia = b.roundToInt()
        var cm = false
        val decimals = m.groupValues[1].any { it == '.' || it == ',' } || m.groupValues[3].any { it == '.' || it == ',' }
        if (a in 7.0..25.0 && b in 4.0..15.0) {
            // "12 por 8" = 120/80 (centímetros de mercúrio).
            sys = (a * 10).roundToInt()
            dia = (b * 10).roundToInt()
            cm = true
        } else if (decimals) {
            return@mapNotNull null
        }
        if (!plausible(sys, dia)) return@mapNotNull null
        val after = m.groupValues[4].toIntOrNull()?.takeIf { it in PULSE_RANGE }
        Slash(PressureGuess(sys, dia, after ?: pulseLabel, oneLine(m.value)), cm)
    }.toList()

    // ---- 3. Números empilhados (visor) ----

    /** Números soltos de 2 ou 3 dígitos, fora de datas, horas e decimais. */
    private val loose = Regex("(?<![\\d/.,:])(\\d{2,3})(?!\\d|[.,:]\\d|/\\d|\\s*[/xX\\\\]\\s*\\d)")

    private fun stacked(text: String): List<PressureGuess> {
        val nums = loose.findAll(text).map { it.groupValues[1].toInt() }.toList()
        if (nums.size < 2) return emptyList()
        for (i in nums.indices) {
            val sys = nums[i]
            if (sys !in SYS_RANGE) continue
            for (j in i + 1 until nums.size) {
                val dia = nums[j]
                if (!plausible(sys, dia)) continue
                val pulse = nums.drop(j + 1).firstOrNull { it in PULSE_RANGE }
                val shown = listOfNotNull(sys, dia, pulse).joinToString(" · ")
                return listOf(PressureGuess(sys, dia, pulse, shown))
            }
        }
        return emptyList()
    }

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()
}
