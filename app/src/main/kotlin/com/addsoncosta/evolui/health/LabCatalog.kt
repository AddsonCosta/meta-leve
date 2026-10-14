package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.data.LabResultEntity
import com.addsoncosta.evolui.data.Repository

/**
 * Um item de exame conhecido pelo app: nome padrão, sinônimos (para ler laudos),
 * unidade usual e faixa de referência típica de adulto. A faixa do laudo sempre tem prioridade.
 */
data class LabItem(
    val name: String,
    val group: String,
    val unit: String,
    val low: Double? = null,
    val high: Double? = null,
    val refText: String? = null,
    val synonyms: List<String> = emptyList(),
    /** Faixa diferente para mulheres, quando houver. */
    val femaleLow: Double? = null,
    val femaleHigh: Double? = null,
    val help: String? = null,
) {
    fun refLow(sex: Sex?): Double? = if (sex == Sex.FEMALE && (femaleLow != null || femaleHigh != null)) femaleLow else low
    fun refHigh(sex: Sex?): Double? = if (sex == Sex.FEMALE && (femaleLow != null || femaleHigh != null)) femaleHigh else high
}

object LabCatalog {
    const val ERITRO = "Eritrograma"
    const val LEUCO = "Leucograma"
    const val PLAQ = "Plaquetas"
    const val BIO = "Bioimpedância"

    /** Hemograma completo, na ordem do laudo. */
    val hemogram: List<LabItem> = listOf(
        LabItem("Hemácias", ERITRO, "milhões/mm³", 4.5, 5.9, synonyms = listOf("hemacias", "eritrocitos", "globulos vermelhos", "contagem de hemacias", "rbc"), femaleLow = 4.0, femaleHigh = 5.2),
        LabItem("Hemoglobina", ERITRO, "g/dL", 13.5, 17.5, synonyms = listOf("hemoglobina", "hgb"), femaleLow = 12.0, femaleHigh = 15.5),
        LabItem("Hematócrito", ERITRO, "%", 41.0, 53.0, synonyms = listOf("hematocrito", "hct"), femaleLow = 36.0, femaleHigh = 46.0),
        LabItem("VCM", ERITRO, "fL", 80.0, 100.0, synonyms = listOf("vcm", "volume corpuscular medio", "mcv")),
        LabItem("HCM", ERITRO, "pg", 27.0, 33.0, synonyms = listOf("hcm", "hemoglobina corpuscular media", "mch")),
        LabItem("CHCM", ERITRO, "g/dL", 32.0, 36.0, synonyms = listOf("chcm", "mchc", "concentracao de hemoglobina corpuscular media", "concentracao hemoglobina corpuscular media")),
        LabItem("RDW", ERITRO, "%", 11.5, 14.5, synonyms = listOf("rdw", "rdw-cv")),
        LabItem("Leucócitos", LEUCO, "/mm³", 4000.0, 11000.0, synonyms = listOf("leucocitos", "leucocitos totais", "globulos brancos", "wbc")),
        LabItem("Neutrófilos", LEUCO, "%", 40.0, 75.0, synonyms = listOf("neutrofilos", "neutrofilos totais")),
        LabItem("Bastões", LEUCO, "%", 0.0, 5.0, synonyms = listOf("bastoes", "bastonetes", "neutrofilos bastonetes", "neutrofilos bastoes")),
        LabItem("Segmentados", LEUCO, "%", 40.0, 70.0, synonyms = listOf("segmentados", "neutrofilos segmentados")),
        LabItem("Eosinófilos", LEUCO, "%", 1.0, 5.0, synonyms = listOf("eosinofilos")),
        LabItem("Basófilos", LEUCO, "%", 0.0, 2.0, synonyms = listOf("basofilos")),
        LabItem("Linfócitos", LEUCO, "%", 20.0, 45.0, synonyms = listOf("linfocitos", "linfocitos tipicos")),
        LabItem("Monócitos", LEUCO, "%", 2.0, 10.0, synonyms = listOf("monocitos")),
        LabItem("Plaquetas", PLAQ, "/mm³", 150000.0, 450000.0, synonyms = listOf("plaquetas", "contagem de plaquetas", "plt")),
        LabItem("VPM", PLAQ, "fL", 7.5, 11.5, synonyms = listOf("vpm", "volume plaquetario medio", "mpv")),
    )

    /** Outros exames de sangue e urina comuns. */
    val common: List<LabItem> = listOf(
        LabItem("Glicose", "Glicose e diabetes", "mg/dL", 70.0, 99.0, synonyms = listOf("glicose", "glicemia", "glicose em jejum", "glicemia de jejum", "glicose jejum")),
        LabItem("Hemoglobina glicada", "Glicose e diabetes", "%", 4.0, 5.6, synonyms = listOf("hemoglobina glicada", "hemoglobina glicosilada", "hba1c", "glicohemoglobina", "a1c")),
        LabItem("Insulina", "Glicose e diabetes", "µUI/mL", 2.6, 24.9, synonyms = listOf("insulina", "insulina basal", "insulina de jejum")),
        LabItem("Colesterol total", "Colesterol e gorduras", "mg/dL", null, 190.0, "menor que 190", listOf("colesterol total")),
        LabItem("HDL", "Colesterol e gorduras", "mg/dL", 40.0, null, "maior que 40", listOf("hdl", "colesterol hdl", "hdl colesterol", "hdl-colesterol", "hdl-c")),
        LabItem("LDL", "Colesterol e gorduras", "mg/dL", null, 130.0, "menor que 130 (a meta muda conforme o risco)", listOf("ldl", "colesterol ldl", "ldl colesterol", "ldl-colesterol", "ldl-c")),
        LabItem("Colesterol não-HDL", "Colesterol e gorduras", "mg/dL", null, 160.0, "menor que 160", listOf("colesterol nao-hdl", "colesterol nao hdl", "nao-hdl", "nao hdl")),
        LabItem("VLDL", "Colesterol e gorduras", "mg/dL", null, 30.0, "menor que 30", listOf("vldl", "colesterol vldl", "vldl-colesterol")),
        LabItem("Triglicerídeos", "Colesterol e gorduras", "mg/dL", null, 150.0, "menor que 150", listOf("triglicerideos", "triglicerides", "trigliceridios", "triglicerideo")),
        LabItem("TGO (AST)", "Fígado", "U/L", null, 40.0, "até 40", listOf("tgo", "ast", "ast/tgo", "tgo/ast", "aspartato aminotransferase", "transaminase oxalacetica")),
        LabItem("TGP (ALT)", "Fígado", "U/L", null, 41.0, "até 41", listOf("tgp", "alt", "alt/tgp", "tgp/alt", "alanina aminotransferase", "transaminase piruvica")),
        LabItem("Gama-GT", "Fígado", "U/L", 8.0, 61.0, synonyms = listOf("gama-gt", "gama gt", "ggt", "gama glutamil transferase", "gamaglutamiltransferase"), femaleLow = 5.0, femaleHigh = 36.0),
        LabItem("Fosfatase alcalina", "Fígado", "U/L", 40.0, 129.0, synonyms = listOf("fosfatase alcalina")),
        LabItem("Bilirrubina total", "Fígado", "mg/dL", 0.2, 1.2, synonyms = listOf("bilirrubina total")),
        LabItem("Albumina", "Fígado", "g/dL", 3.5, 5.2, synonyms = listOf("albumina")),
        LabItem("Creatinina", "Rins", "mg/dL", 0.7, 1.3, synonyms = listOf("creatinina"), femaleLow = 0.6, femaleHigh = 1.1),
        LabItem("Ureia", "Rins", "mg/dL", 15.0, 45.0, synonyms = listOf("ureia")),
        LabItem("Ácido úrico", "Rins", "mg/dL", 3.4, 7.0, synonyms = listOf("acido urico"), femaleLow = 2.4, femaleHigh = 6.0),
        LabItem("TSH", "Tireoide", "µUI/mL", 0.4, 4.5, synonyms = listOf("tsh", "hormonio tireoestimulante", "tireotrofina")),
        LabItem("T4 livre", "Tireoide", "ng/dL", 0.8, 1.8, synonyms = listOf("t4 livre", "t4l", "tiroxina livre")),
        LabItem("Vitamina D", "Vitaminas e minerais", "ng/mL", 30.0, 100.0, "30 a 100", listOf("vitamina d", "25-hidroxivitamina d", "25 hidroxivitamina d", "25-oh vitamina d", "25(oh)d", "vitamina d3")),
        LabItem("Vitamina B12", "Vitaminas e minerais", "pg/mL", 200.0, 900.0, synonyms = listOf("vitamina b12", "b12", "cobalamina")),
        LabItem("Ferritina", "Vitaminas e minerais", "ng/mL", 30.0, 400.0, synonyms = listOf("ferritina"), femaleLow = 15.0, femaleHigh = 150.0),
        LabItem("Ferro", "Vitaminas e minerais", "µg/dL", 60.0, 170.0, synonyms = listOf("ferro", "ferro serico")),
        LabItem("Sódio", "Vitaminas e minerais", "mEq/L", 135.0, 145.0, synonyms = listOf("sodio")),
        LabItem("Potássio", "Vitaminas e minerais", "mEq/L", 3.5, 5.1, synonyms = listOf("potassio")),
        LabItem("Cálcio", "Vitaminas e minerais", "mg/dL", 8.6, 10.3, synonyms = listOf("calcio", "calcio total")),
        LabItem("Magnésio", "Vitaminas e minerais", "mg/dL", 1.7, 2.4, synonyms = listOf("magnesio")),
        LabItem("PCR", "Inflamação", "mg/L", null, 5.0, "menor que 5", listOf("pcr", "proteina c reativa", "pcr ultrassensivel", "proteina c reativa ultrassensivel")),
        LabItem("PSA total", "Próstata", "ng/mL", null, 4.0, "menor que 4", listOf("psa", "psa total", "antigeno prostatico especifico")),
        LabItem("Urina (EAS) - densidade", "Urina", "", 1.005, 1.030, synonyms = listOf("densidade")),
        LabItem("Urina (EAS) - pH", "Urina", "", 5.0, 8.0, synonyms = listOf("ph")),
    )

    /** Dados principais da bioimpedância. */
    val bioimpedance: List<LabItem> = listOf(
        LabItem("Peso", BIO, "kg", synonyms = listOf("peso", "peso corporal")),
        LabItem("IMC", BIO, "kg/m²", 18.5, 24.9, synonyms = listOf("imc", "indice de massa corporal", "bmi")),
        LabItem("Gordura corporal", BIO, "%", 10.0, 20.0, synonyms = listOf("gordura corporal", "percentual de gordura", "% de gordura", "% gordura", "pgc", "gordura corporal (%)"), femaleLow = 18.0, femaleHigh = 28.0),
        LabItem("Massa de gordura", BIO, "kg", synonyms = listOf("massa de gordura", "massa gorda", "massa de gordura corporal")),
        LabItem("Massa muscular esquelética", BIO, "kg", synonyms = listOf("massa muscular esqueletica", "mme", "smm", "massa muscular")),
        LabItem("Massa magra", BIO, "kg", synonyms = listOf("massa magra", "massa livre de gordura", "mlg")),
        LabItem("Água corporal", BIO, "%", 50.0, 65.0, synonyms = listOf("agua corporal", "agua corporal total", "% de agua", "hidratacao"), femaleLow = 45.0, femaleHigh = 60.0),
        LabItem("Gordura visceral", BIO, "nível", 1.0, 9.0, "1 a 9 é o ideal", listOf("gordura visceral", "nivel de gordura visceral", "area de gordura visceral")),
        LabItem("Massa óssea", BIO, "kg", synonyms = listOf("massa ossea", "minerais", "massa mineral ossea")),
        LabItem("Taxa metabólica basal", BIO, "kcal", synonyms = listOf("taxa metabolica basal", "tmb", "metabolismo basal", "bmr")),
        LabItem("Idade metabólica", BIO, "anos", synonyms = listOf("idade metabolica", "idade corporal")),
        LabItem("Proteína", BIO, "kg", synonyms = listOf("proteina", "proteinas")),
    )

    /** Itens de exames de laboratório (sem a bioimpedância). */
    val labItems: List<LabItem> = hemogram + common

    val all: List<LabItem> = labItems + bioimpedance

    private val byName: Map<String, LabItem> = all.associateBy { Repository.normalize(it.name) }

    val hemogramNames: Set<String> = hemogram.map { it.name }.toSet()

    fun find(name: String): LabItem? = byName[Repository.normalize(name)]

    /** Posição do item no catálogo, para ordenar as tabelas igual ao laudo. */
    fun order(name: String): Int {
        val i = all.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        return if (i >= 0) i else all.size
    }

    /** Faixa de referência do valor: a do laudo, ou a típica do catálogo. */
    fun range(r: LabResultEntity, sex: Sex?): Pair<Double?, Double?> {
        if (r.refLow != null || r.refHigh != null) return r.refLow to r.refHigh
        val item = find(r.analyte) ?: return null to null
        return item.refLow(sex) to item.refHigh(sex)
    }

    /** -1 abaixo, 1 acima, 0 dentro da faixa (ou sem faixa). */
    fun status(r: LabResultEntity, sex: Sex?): Int {
        val (lo, hi) = range(r, sex)
        return when {
            lo != null && r.value < lo -> -1
            hi != null && r.value > hi -> 1
            else -> 0
        }
    }

    /** Texto da referência, ex.: "13,5 a 17,5 g/dL". */
    fun refLabel(r: LabResultEntity, sex: Sex?): String? {
        if (!r.refText.isNullOrBlank()) return r.refText
        val (lo, hi) = range(r, sex)
        val unit = r.unit ?: find(r.analyte)?.unit ?: ""
        return rangeText(lo, hi, unit)
    }

    fun rangeText(lo: Double?, hi: Double?, unit: String): String? {
        val u = if (unit.isBlank()) "" else " $unit"
        return when {
            lo != null && hi != null -> "${num(lo)} a ${num(hi)}$u"
            hi != null -> "até ${num(hi)}$u"
            lo != null -> "acima de ${num(lo)}$u"
            else -> null
        }
    }

    /** Número em formato brasileiro, sem casas desnecessárias. */
    fun num(v: Double): String {
        val abs = kotlin.math.abs(v)
        val decimals = when {
            abs >= 1000 -> 0
            abs >= 100 -> 1
            abs >= 10 -> 1
            abs >= 1 -> 2
            else -> 3
        }
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pt-BR"))
        nf.maximumFractionDigits = decimals
        nf.minimumFractionDigits = 0
        return nf.format(v)
    }
}
