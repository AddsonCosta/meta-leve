package com.addsoncosta.evolui.data

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tipo de medida: gramas, mililitros, volume caseiro (copo, xícara), porção típica do alimento ou criada pelo usuário. */
enum class MeasureKind { GRAMS, ML, VOLUME, PORTION, CUSTOM }

/**
 * Medida que o usuário escolhe ao registrar um alimento. [gramsPerUnit] converte 1 unidade da medida em gramas;
 * [mlPerUnit] existe nas medidas de volume. [name] é o que vai para `MealEntity.unit`.
 */
data class Measure(
    val key: String,
    val name: String,
    val gramsPerUnit: Double,
    val kind: MeasureKind,
    val mlPerUnit: Double? = null,
    val portion: FoodPortionEntity? = null,
) {
    fun grams(amount: Double): Double = amount * gramsPerUnit

    /** Quantidade escolhida pronta para gravar (amount/unit nulos quando é em gramas). */
    fun choice(amount: Double): PortionChoice =
        if (kind == MeasureKind.GRAMS) PortionChoice(amount, null, null) else PortionChoice(grams(amount), amount, name)
}

/** Quanto foi comido: [grams] é a base do cálculo; [amount] e [unit] guardam a medida escolhida (ex.: 1,5 "xícara"). */
data class PortionChoice(val grams: Double, val amount: Double?, val unit: String?)

/** Medidas caseiras e conversões. As porções típicas de cada alimento vêm de portions.json (gravadas em food_portion). */
object Portions {
    const val GRAMS = "g"
    const val ML = "ml"

    /** Volumes padrão (ml) usados em líquidos e alimentos com densidade conhecida. */
    val volumes: List<Pair<String, Double>> = listOf(
        "copo americano" to 190.0,
        "copo comum" to 200.0,
        "copo grande" to 300.0,
        "xícara" to 240.0,
        "colher de sopa" to 15.0,
        "colher de sobremesa" to 10.0,
        "colher de chá" to 5.0,
    )

    /** Todas as medidas de um alimento: porções do alimento, as do usuário, a porção do rótulo, volumes, ml e gramas. */
    fun measures(food: FoodEntity, portions: List<FoodPortionEntity>): List<Measure> {
        val list = mutableListOf<Measure>()
        val names = HashSet<String>()
        fun add(m: Measure) {
            if (m.gramsPerUnit > 0 && names.add(m.name.lowercase())) list += m
        }
        portions.filter { it.custom }.forEach { add(Measure("p${it.id}", it.name, it.grams, MeasureKind.CUSTOM, portion = it)) }
        portions.filter { !it.custom }.forEach { add(Measure("p${it.id}", it.name, it.grams, MeasureKind.PORTION, portion = it)) }
        val servingName = food.servingName
        val servingGrams = food.servingGrams
        if (servingName != null && servingGrams != null) add(Measure("s", servingName, servingGrams, MeasureKind.PORTION))
        val density = food.densityGPerMl
        if (density != null) {
            volumes.forEach { (name, ml) -> add(Measure("v:$name", name, ml * density, MeasureKind.VOLUME, mlPerUnit = ml)) }
            add(Measure(ML, ML, density, MeasureKind.ML, mlPerUnit = 1.0))
        }
        add(Measure(GRAMS, GRAMS, 1.0, MeasureKind.GRAMS))
        return list
    }

    /** A medida de gramas sempre existe na lista. */
    fun gramsMeasure(measures: List<Measure>): Measure =
        measures.firstOrNull { it.kind == MeasureKind.GRAMS } ?: Measure(GRAMS, GRAMS, 1.0, MeasureKind.GRAMS)

    /** Medida e quantidade de uma escolha já gravada (refeição salva, último registro). */
    fun fromChoice(measures: List<Measure>, grams: Double, amount: Double?, unit: String?): Pair<Measure, Double> {
        if (unit != null && unit != GRAMS && amount != null) {
            measures.firstOrNull { it.name == unit }?.let { return it to amount }
        }
        return gramsMeasure(measures) to grams
    }

    /** Medida sugerida ao abrir: a última usada com este alimento, a primeira porção típica, um copo (líquidos) ou 100 g. */
    fun defaultChoice(food: FoodEntity, measures: List<Measure>, last: MealEntity?): Pair<Measure, Double> {
        if (last != null) return fromChoice(measures, last.grams, last.amount, last.unit)
        measures.firstOrNull { it.kind == MeasureKind.PORTION || it.kind == MeasureKind.CUSTOM }?.let { return it to 1.0 }
        if (food.densityGPerMl != null) measures.firstOrNull { it.name == "copo comum" }?.let { return it to 1.0 }
        return gramsMeasure(measures) to 100.0
    }

    /** Quantidade sugerida quando o usuário troca de medida. */
    fun defaultAmount(measure: Measure): Double = when (measure.kind) {
        MeasureKind.GRAMS -> 100.0
        MeasureKind.ML -> 200.0
        else -> 1.0
    }

    /** Atalhos de quantidade (rótulo, valor). */
    fun quickAmounts(measure: Measure): List<Pair<String, Double>> = when (measure.kind) {
        MeasureKind.GRAMS -> listOf("50 g" to 50.0, "100 g" to 100.0, "150 g" to 150.0, "200 g" to 200.0)
        MeasureKind.ML -> listOf("100 ml" to 100.0, "200 ml" to 200.0, "300 ml" to 300.0, "500 ml" to 500.0)
        else -> listOf("½" to 0.5, "1" to 1.0, "1½" to 1.5, "2" to 2.0, "3" to 3.0)
    }

    /** Número para a caixa de texto, com vírgula e sem separador de milhar (ex.: 1,5 ou 1250). */
    fun inputText(value: Double): String =
        BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    fun round1(value: Double): Double = Math.round(value * 10) / 10.0
}

// Formato dos arquivos em assets (portions.json e extra_foods.json).

@Serializable
data class PortionSpec(val name: String, val g: Double? = null, val ml: Double? = null)

@Serializable
data class PortionRule(
    val match: List<String> = emptyList(),
    @SerialName("not") val exclude: List<String> = emptyList(),
    val category: String? = null,
    val portions: List<PortionSpec> = emptyList(),
) {
    fun matches(searchName: String, foodCategory: String): Boolean =
        (category == null || category == foodCategory) &&
            match.all { hasToken(searchName, it) } && exclude.none { hasToken(searchName, it) }
}

@Serializable
data class DensityRule(
    val match: List<String> = emptyList(),
    @SerialName("not") val exclude: List<String> = emptyList(),
    val category: String? = null,
    val density: Double,
) {
    fun matches(searchName: String, foodCategory: String): Boolean =
        (category == null || category == foodCategory) &&
            match.all { hasToken(searchName, it) } && exclude.none { hasToken(searchName, it) }
}

@Serializable
data class PortionTable(val densities: List<DensityRule> = emptyList(), val rules: List<PortionRule> = emptyList()) {
    /** Densidade (g/ml) para líquidos; nulo para sólidos. */
    fun density(searchName: String, category: String): Double? =
        densities.firstOrNull { it.matches(searchName, category) }?.density

    /** Porções da primeira regra que combina com o alimento. */
    fun portionsFor(searchName: String, category: String): List<PortionSpec> =
        rules.firstOrNull { it.matches(searchName, category) }?.portions.orEmpty()
}

@Serializable
data class ExtraFood(
    val name: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
    val density: Double? = null,
    val portions: List<PortionSpec>? = null,
    val source: String? = null,
)

@Serializable
data class ExtraFoodFile(val foods: List<ExtraFood> = emptyList())

/** Trecho com "^" combina só com o começo do nome; sem ele, em qualquer lugar. */
private fun hasToken(searchName: String, token: String): Boolean =
    if (token.startsWith("^")) searchName.startsWith(token.substring(1)) else searchName.contains(token)
