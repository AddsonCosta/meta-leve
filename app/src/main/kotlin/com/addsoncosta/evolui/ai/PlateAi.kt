package com.addsoncosta.evolui.ai

import com.addsoncosta.evolui.ai.AiJson.array
import com.addsoncosta.evolui.ai.AiJson.field
import com.addsoncosta.evolui.ai.AiJson.number
import com.addsoncosta.evolui.ai.AiJson.text
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Um alimento que a IA viu no prato. Valores para a porção inteira ([grams]). */
data class AiFoodItem(
    val name: String,
    val grams: Double,
    val household: String?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
)

/** Resposta da IA para a foto do prato, já conferida. O total é sempre a soma dos itens. */
data class PlateAnalysis(
    val items: List<AiFoodItem>,
    /** De 0 a 1, ou nulo se a IA não disse. */
    val confidence: Float?,
    val note: String?,
) {
    val kcal: Double get() = items.sumOf { it.kcal }
    val protein: Double get() = items.sumOf { it.protein }
    val fat: Double get() = items.sumOf { it.fat }
    val carb: Double get() = items.sumOf { it.carb }

    val confidenceLabel: String?
        get() = confidence?.let { if (it >= 0.75f) "alta" else if (it >= 0.45f) "média" else "baixa" }
}

/** Comando fixo enviado com a foto do prato. */
object PlatePrompt {
    val text: String = """
        Analise a foto deste prato de comida. A pessoa mora no Brasil; pratos e alimentos brasileiros são comuns
        (ex.: arroz, feijão, farofa, tapioca, cuscuz, pão de queijo, açaí). Identifique cada alimento ou bebida visível
        e estime a porção pelo tamanho no prato, talheres e recipiente.

        Responda somente com um objeto JSON neste formato:
        {
          "alimentos": [
            {
              "nome": "ovo frito",
              "porcao_g": 50,
              "medida_caseira": "1 unidade",
              "kcal": 120,
              "proteina_g": 7,
              "gordura_g": 10,
              "carboidrato_g": 0.5
            }
          ],
          "total": { "kcal": 120, "proteina_g": 7, "gordura_g": 10, "carboidrato_g": 0.5 },
          "confianca": "alta",
          "observacao": "texto curto opcional"
        }

        Regras:
        - "nome" em português do Brasil, simples como numa tabela de alimentos (ex.: "arroz branco cozido",
          "feijão carioca cozido", "tapioca", "café sem açúcar"). Um alimento por item; separe o recheio só se der para ver.
        - "porcao_g" é a quantidade em gramas da porção que aparece (bebidas: considere 1 ml = 1 g).
        - "medida_caseira" como se fala no Brasil (ex.: "2 colheres de sopa", "1 concha média", "1 xícara").
        - kcal e macros são da porção inteira, não de 100 g. Use só números, sem unidades.
        - "confianca": "alta", "media" ou "baixa".
        - Se a foto não mostrar comida, devolva "alimentos": [] e explique em "observacao".
    """.trimIndent()
}

/** Confere o JSON da IA: descarta itens sem sentido e recalcula o total. */
object PlateAiParser {
    private const val MAX_GRAMS = 3000.0

    fun parseText(text: String?): PlateAnalysis? = AiJson.extractObject(text)?.let { parse(it) }

    /** Nulo quando o objeto não tem a lista de alimentos (resposta fora do formato). */
    fun parse(obj: JsonObject): PlateAnalysis? {
        val list = obj.field("alimentos", "foods", "itens", "items")
        val array = list.array() ?: return null
        val items = array.mapNotNull { (it as? JsonObject)?.let(::item) }
        return PlateAnalysis(
            items = items,
            confidence = confidence(obj.field("confianca", "confiança", "confidence")),
            note = obj.field("observacao", "observação", "observacoes", "note", "notes").text()?.take(300),
        )
    }

    private fun item(o: JsonObject): AiFoodItem? {
        val name = o.field("nome", "name", "alimento", "food").text()?.replace(Regex("\\s+"), " ")?.take(80) ?: return null
        val grams = o.field("porcao_g", "porção_g", "gramas", "grams", "peso_g", "porcao", "porção", "portion_g", "portion").number()
            ?: return null
        if (grams <= 0 || grams > MAX_GRAMS) return null
        fun macro(vararg keys: String): Double? = o.field(*keys).number()?.takeIf { it >= 0 && it <= grams * 1.05 }
        val protein = macro("proteina_g", "proteína_g", "proteina", "proteína", "proteinas_g", "protein", "protein_g")
        val fat = macro("gordura_g", "gordura", "gorduras_g", "lipidios_g", "lipídios_g", "fat", "fat_g")
        val carb = macro("carboidrato_g", "carboidratos_g", "carboidrato", "carboidratos", "carbo_g", "carb", "carbs", "carb_g", "carbohydrate_g")
        val given = o.field("kcal", "calorias", "calories", "energia_kcal", "energia").number()?.takeIf { it >= 0 }
        val kcal = given ?: if (protein != null || fat != null || carb != null) {
            4 * (protein ?: 0.0) + 9 * (fat ?: 0.0) + 4 * (carb ?: 0.0)
        } else {
            return null
        }
        // Nada passa de ~9 kcal por grama (gordura pura).
        if (kcal > grams * 9.5) return null
        return AiFoodItem(
            name = name.replaceFirstChar { it.uppercase() },
            grams = grams,
            household = o.field("medida_caseira", "medida", "household_measure", "household").text()?.take(60),
            kcal = kcal,
            protein = protein ?: 0.0,
            fat = fat ?: 0.0,
            carb = carb ?: 0.0,
        )
    }

    private fun confidence(e: JsonElement?): Float? {
        val p = e as? JsonPrimitive ?: return null
        if (!p.isString) {
            val v = p.content.toDoubleOrNull() ?: return null
            val f = if (v > 1) v / 100 else v
            return f.coerceIn(0.0, 1.0).toFloat()
        }
        val s = p.content.trim().lowercase()
        return when {
            s.startsWith("alt") || s.startsWith("high") -> 0.85f
            s.startsWith("méd") || s.startsWith("med") || s.startsWith("mid") -> 0.6f
            s.startsWith("baix") || s.startsWith("low") -> 0.3f
            else -> e.number()?.let { v -> (if (v > 1) v / 100 else v).coerceIn(0.0, 1.0).toFloat() }
        }
    }

    /** "2 colheres de sopa" → (2, "colheres de sopa"); "meia xícara" → (0,5, "xícara"); sem número → (1, texto). */
    fun splitHousehold(text: String?): Pair<Double, String>? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        Regex("^(\\d+(?:[.,]\\d+)?)\\s+(.+)$").find(t)?.let { m ->
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            if (amount != null && amount > 0) return amount to m.groupValues[2].trim()
        }
        Regex("^(\\d+)/(\\d+)\\s+(.+)$").find(t)?.let { m ->
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[2].toDouble()
            if (a > 0 && b > 0) return a / b to m.groupValues[3].trim()
        }
        Regex("^(meia|meio)\\s+(.+)$", RegexOption.IGNORE_CASE).find(t)?.let { m -> return 0.5 to m.groupValues[2].trim() }
        return 1.0 to t
    }
}
