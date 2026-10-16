package com.addsoncosta.evolui.health

import kotlin.math.roundToInt

/** Um dado essencial da bioimpedância. [name] é o nome do item em [LabCatalog.bioimpedance]. */
data class BioValue(val name: String, val value: Double, val line: String)

/**
 * Lê o relatório da bioimpedância (InBody, Tanita, Omron e parecidos) e pega só o essencial:
 * peso, % de gordura, massa gorda, massa muscular esquelética, massa magra, água corporal,
 * gordura visceral, taxa metabólica basal, idade metabólica e IMC. O resto do relatório é ignorado.
 */
object BioParser {
    const val PESO = "Peso"
    const val IMC = "IMC"
    const val GORDURA_PCT = "Gordura corporal"
    const val GORDURA_KG = "Massa de gordura"
    const val MUSCULO = "Massa muscular esquelética"
    const val MAGRA = "Massa magra"
    const val AGUA_PCT = "Água corporal"
    const val AGUA_L = "Água corporal total"
    const val VISCERAL = "Gordura visceral"
    const val TMB = "Taxa metabólica basal"
    const val IDADE = "Idade metabólica"

    /** Ordem em que os itens aparecem para o usuário. */
    val order = listOf(PESO, IMC, GORDURA_PCT, GORDURA_KG, MUSCULO, MAGRA, AGUA_PCT, AGUA_L, VISCERAL, TMB, IDADE)

    private enum class Kind { PESO, IMC, GORDURA, GORDURA_PCT, GORDURA_KG, MUSCULO, MAGRA, AGUA, VISCERAL, TMB, IDADE, IGNORAR }

    /** Rótulos (sem acento, minúsculos). Os mais longos vêm antes para vencer os curtos. */
    private val labels: List<Pair<String, Kind>> = listOf(
        // O que não interessa (consome o rótulo para o valor não ir para outro item).
        "area de gordura visceral" to Kind.IGNORAR,
        "controle de peso" to Kind.IGNORAR,
        "controle de gordura" to Kind.IGNORAR,
        "controle muscular" to Kind.IGNORAR,
        "peso ideal" to Kind.IGNORAR,
        "peso alvo" to Kind.IGNORAR,
        "peso meta" to Kind.IGNORAR,
        "peso padrao" to Kind.IGNORAR,
        "peso objetivo" to Kind.IGNORAR,
        "massa ossea" to Kind.IGNORAR,
        "massa mineral" to Kind.IGNORAR,
        "minerais" to Kind.IGNORAR,
        "proteina" to Kind.IGNORAR,
        "proteinas" to Kind.IGNORAR,
        "agua intracelular" to Kind.IGNORAR,
        "agua extracelular" to Kind.IGNORAR,
        "intracelular" to Kind.IGNORAR,
        "extracelular" to Kind.IGNORAR,
        "relacao cintura" to Kind.IGNORAR,
        "relacao cintura-quadril" to Kind.IGNORAR,
        "angulo de fase" to Kind.IGNORAR,
        "pontuacao" to Kind.IGNORAR,
        "grau de obesidade" to Kind.IGNORAR,
        "massa muscular segmentar" to Kind.IGNORAR,
        "gordura segmentar" to Kind.IGNORAR,
        "altura" to Kind.IGNORAR,
        "idade" to Kind.IGNORAR,

        "nivel de gordura visceral" to Kind.VISCERAL,
        "indice de gordura visceral" to Kind.VISCERAL,
        "gordura visceral" to Kind.VISCERAL,
        "visceral fat" to Kind.VISCERAL,
        "visceral" to Kind.VISCERAL,

        "taxa metabolica basal" to Kind.TMB,
        "taxa de metabolismo basal" to Kind.TMB,
        "metabolismo basal" to Kind.TMB,
        "gasto energetico basal" to Kind.TMB,
        "tmb" to Kind.TMB,
        "bmr" to Kind.TMB,

        "idade metabolica" to Kind.IDADE,
        "idade corporal" to Kind.IDADE,
        "idade biologica" to Kind.IDADE,
        "metabolic age" to Kind.IDADE,

        "indice de massa corporal" to Kind.IMC,
        "imc" to Kind.IMC,
        "bmi" to Kind.IMC,

        "massa muscular esqueletica" to Kind.MUSCULO,
        "musculo esqueletico" to Kind.MUSCULO,
        "musculatura esqueletica" to Kind.MUSCULO,
        "massa muscular" to Kind.MUSCULO,
        "massa de musculo" to Kind.MUSCULO,
        "musculo" to Kind.MUSCULO,
        "mme" to Kind.MUSCULO,
        "smm" to Kind.MUSCULO,

        "massa livre de gordura" to Kind.MAGRA,
        "massa isenta de gordura" to Kind.MAGRA,
        "peso livre de gordura" to Kind.MAGRA,
        "massa corporal magra" to Kind.MAGRA,
        "massa magra" to Kind.MAGRA,
        "mlg" to Kind.MAGRA,
        "ffm" to Kind.MAGRA,

        "massa de gordura corporal" to Kind.GORDURA_KG,
        "massa de gordura" to Kind.GORDURA_KG,
        "massa gorda" to Kind.GORDURA_KG,
        "body fat mass" to Kind.GORDURA_KG,
        "percentual de gordura corporal" to Kind.GORDURA_PCT,
        "percentual de gordura" to Kind.GORDURA_PCT,
        "porcentagem de gordura corporal" to Kind.GORDURA_PCT,
        "porcentagem de gordura" to Kind.GORDURA_PCT,
        "% de gordura corporal" to Kind.GORDURA_PCT,
        "% de gordura" to Kind.GORDURA_PCT,
        "% gordura" to Kind.GORDURA_PCT,
        "pgc" to Kind.GORDURA_PCT,
        "body fat" to Kind.GORDURA,
        "gordura corporal" to Kind.GORDURA,
        "gordura" to Kind.GORDURA,

        "agua corporal total" to Kind.AGUA,
        "agua corporal" to Kind.AGUA,
        "agua total" to Kind.AGUA,
        "act" to Kind.AGUA,
        "tbw" to Kind.AGUA,
        "body water" to Kind.AGUA,
        "hidratacao" to Kind.AGUA,
        "% de agua" to Kind.AGUA,
        "agua" to Kind.AGUA,

        "peso corporal" to Kind.PESO,
        "peso atual" to Kind.PESO,
        "peso" to Kind.PESO,
        "weight" to Kind.PESO,
    ).sortedByDescending { it.first.length }

    private val labelRegexes: List<Pair<Regex, Kind>> = labels.map { (l, k) ->
        val start = if (l.first().isLetter()) "(?<![a-z])" else ""
        Regex(start + Regex.escape(l) + "(?![a-z])") to k
    }

    private val number = Regex("(?<![\\d.,])(\\d{1,2}\\.\\d{3}(?![\\d.,])|\\d+(?:[.,]\\d+)?)")
    private val unitAfter = Regex("^\\s*(kg|%|l(?![a-z])|litros?|kcal|anos|cm2|cm²|kg/m2|kg/m²|nivel)")

    private class Hit(val start: Int, val end: Int, val kind: Kind)

    fun parse(text: String): List<BioValue> {
        val found = LinkedHashMap<String, BioValue>()
        var musclePct: Pair<Double, String>? = null

        for (rawLine in text.lines()) {
            val raw = rawLine.trim()
            if (raw.isEmpty()) continue
            val line = TextScan.fold(raw)
            val hits = hits(line)
            hits.forEachIndexed { i, h ->
                if (h.kind == Kind.IGNORAR) return@forEachIndexed
                val limit = if (i + 1 < hits.size) hits[i + 1].start else line.length
                val between = line.substring(h.end, limit)
                val m = number.find(between) ?: return@forEachIndexed
                if (m.range.first > 40) return@forEachIndexed
                // Data ou hora não é valor.
                val next = between.getOrNull(m.range.last + 1)
                if (next == '/' || next == ':') return@forEachIndexed
                val labelUnit = unitIn(between.substring(0, m.range.first))
                val after = unitAfter.find(between.substring(m.range.last + 1))?.groupValues?.get(1)
                val unit = normUnit(after ?: labelUnit)
                val token = m.groupValues[1]
                val v = toNumber(token, h.kind) ?: return@forEachIndexed
                val pctInLabel = line.substring(h.start, h.end).contains('%') || line.substring(h.start, h.end).let { it.contains("percent") || it.contains("porcent") || it == "pgc" }

                fun put(name: String, value: Double, ok: Boolean) {
                    if (ok && !found.containsKey(name)) found[name] = BioValue(name, value, raw)
                }
                when (h.kind) {
                    Kind.PESO -> put(PESO, v, unit != "%" && v in 20.0..350.0)
                    Kind.IMC -> put(IMC, v, v in 10.0..80.0)
                    Kind.VISCERAL -> put(VISCERAL, v, unit != "cm2" && unit != "kg" && unit != "%" && v in 1.0..59.0)
                    Kind.TMB -> put(TMB, v, v in 500.0..5000.0)
                    Kind.IDADE -> put(IDADE, v, v in 10.0..110.0)
                    Kind.MAGRA -> put(MAGRA, v, unit != "%" && v in 10.0..200.0)
                    Kind.GORDURA_KG -> if (unit == "%") put(GORDURA_PCT, v, v in 2.0..75.0) else put(GORDURA_KG, v, v in 1.0..200.0)
                    Kind.GORDURA_PCT -> if (unit == "kg") put(GORDURA_KG, v, v in 1.0..200.0) else put(GORDURA_PCT, v, v in 2.0..75.0)
                    Kind.GORDURA -> when {
                        unit == "kg" -> put(GORDURA_KG, v, v in 1.0..200.0)
                        unit == "%" || pctInLabel -> put(GORDURA_PCT, v, v in 2.0..75.0)
                        // Sem unidade: "Gordura corporal 22,5" na Tanita/Omron é o percentual.
                        else -> put(GORDURA_PCT, v, v in 2.0..75.0)
                    }
                    Kind.MUSCULO -> if (unit == "%") {
                        if (musclePct == null && v in 10.0..70.0) musclePct = v to raw
                    } else put(MUSCULO, v, v in 5.0..150.0)
                    Kind.AGUA -> when {
                        unit == "l" -> put(AGUA_L, v, v in 10.0..90.0)
                        unit == "%" || pctInLabel -> put(AGUA_PCT, v, v in 20.0..85.0)
                        line.substring(h.start, h.end).contains("total") -> put(AGUA_L, v, v in 10.0..90.0)
                        else -> put(AGUA_PCT, v, v in 20.0..85.0)
                    }
                    Kind.IGNORAR -> {}
                }
            }
        }

        val peso = found[PESO]?.value
        // Água em litros e sem o percentual: calcula o percentual pelo peso (1 L de água ≈ 1 kg).
        val waterL = found[AGUA_L]
        if (waterL != null && !found.containsKey(AGUA_PCT) && peso != null) {
            found[AGUA_PCT] = BioValue(AGUA_PCT, round1(waterL.value / peso * 100), waterL.line)
        }
        // Músculo esquelético em % (Omron): passa para kg pelo peso.
        val mp = musclePct
        if (mp != null && !found.containsKey(MUSCULO) && peso != null) {
            found[MUSCULO] = BioValue(MUSCULO, round1(mp.first / 100 * peso), mp.second)
        }
        return order.mapNotNull { found[it] }
    }

    /** Rótulos achados na linha, sem sobreposição (o mais longo vence). */
    private fun hits(line: String): List<Hit> {
        val taken = BooleanArray(line.length)
        val out = mutableListOf<Hit>()
        for ((re, kind) in labelRegexes) {
            for (m in re.findAll(line)) {
                val r = m.range
                if ((r.first..r.last).any { taken[it] }) continue
                for (i in r) taken[i] = true
                out += Hit(r.first, r.last + 1, kind)
            }
        }
        return out.sortedBy { it.start }
    }

    private fun unitIn(s: String): String? {
        val m = Regex("\\(\\s*(kg|%|l|litros?|kcal|cm2|cm²)\\s*\\)").find(s) ?: Regex("(?<![a-z])(kg|%)(?![a-z])").find(s)
        return m?.groupValues?.get(1)
    }

    private fun normUnit(u: String?): String? = when (u) {
        null -> null
        "litro", "litros", "l" -> "l"
        "cm²", "cm2" -> "cm2"
        "kg/m2", "kg/m²" -> "kg/m2"
        else -> u
    }

    /** "72,4" ou "72.4" viram 72.4; "1.650" (TMB) vira 1650. */
    private fun toNumber(token: String, kind: Kind): Double? {
        if (Regex("^\\d{1,2}\\.\\d{3}$").matches(token)) {
            val asThousand = token.replace(".", "").toDoubleOrNull()
            return if (kind == Kind.TMB) asThousand else token.toDoubleOrNull()
        }
        return token.replace(',', '.').toDoubleOrNull()
    }

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}
