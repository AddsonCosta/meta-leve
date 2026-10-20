package com.addsoncosta.evolui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateAiParserTest {
    private val sample = """
        {
          "alimentos": [
            {"nome": "tapioca", "porcao_g": 80, "medida_caseira": "1 unidade média", "kcal": 190, "proteina_g": 0.5, "gordura_g": 0.2, "carboidrato_g": 46},
            {"nome": "ovo frito", "porcao_g": 50, "medida_caseira": "1 unidade", "kcal": 120, "proteina_g": 7, "gordura_g": 10, "carboidrato_g": 0.5},
            {"nome": "café sem açúcar", "porcao_g": 100, "medida_caseira": "1 xícara pequena", "kcal": 2, "proteina_g": 0.1, "gordura_g": 0, "carboidrato_g": 0.3}
          ],
          "total": {"kcal": 312, "proteina_g": 7.6, "gordura_g": 10.2, "carboidrato_g": 46.8},
          "confianca": "alta"
        }
    """.trimIndent()

    @Test
    fun parsesPlainJson() {
        val r = PlateAiParser.parseText(sample)
        assertNotNull(r)
        r!!
        assertEquals(3, r.items.size)
        assertEquals("Tapioca", r.items[0].name)
        assertEquals(80.0, r.items[0].grams, 0.001)
        assertEquals("1 unidade média", r.items[0].household)
        assertEquals(312.0, r.kcal, 0.001)
        assertEquals("alta", r.confidenceLabel)
    }

    @Test
    fun toleratesFencesAndExtraText() {
        val text = "Claro! Aqui está a análise:\n```json\n$sample\n```\nEspero ter ajudado."
        val r = PlateAiParser.parseText(text)
        assertEquals(3, r?.items?.size)
    }

    @Test
    fun toleratesTextAroundWithoutFences() {
        val text = "Resultado: $sample (estimativa)"
        assertEquals(3, PlateAiParser.parseText(text)?.items?.size)
    }

    @Test
    fun bracesInsideStringsDoNotBreak() {
        val text = """Veja {isto}: {"alimentos": [{"nome": "arroz {branco}", "porcao_g": 100, "kcal": 128}], "confianca": 0.7}"""
        val r = PlateAiParser.parseText(text)
        assertEquals("Arroz {branco}", r?.items?.single()?.name)
        assertEquals("média", r?.confidenceLabel)
    }

    @Test
    fun acceptsNumbersAsTextAndCommaDecimal() {
        val text = """{"alimentos": [{"nome": "feijão", "porcao_g": "86 g", "kcal": "65,5 kcal", "proteina_g": "4,1"}]}"""
        val item = PlateAiParser.parseText(text)!!.items.single()
        assertEquals(86.0, item.grams, 0.001)
        assertEquals(65.5, item.kcal, 0.001)
        assertEquals(4.1, item.protein, 0.001)
        assertEquals(0.0, item.fat, 0.001)
    }

    @Test
    fun computesKcalFromMacrosWhenMissing() {
        val text = """{"foods": [{"name": "frango", "grams": 100, "protein": 30, "fat": 3, "carb": 0}]}"""
        val item = PlateAiParser.parseText(text)!!.items.single()
        assertEquals(4 * 30.0 + 9 * 3.0, item.kcal, 0.001)
    }

    @Test
    fun dropsInvalidItems() {
        val text = """
            {"alimentos": [
              {"nome": "", "porcao_g": 100, "kcal": 100},
              {"nome": "sem peso", "kcal": 100},
              {"nome": "peso zero", "porcao_g": 0, "kcal": 10},
              {"nome": "enorme", "porcao_g": 50000, "kcal": 10},
              {"nome": "calorias impossíveis", "porcao_g": 10, "kcal": 900},
              {"nome": "sem valores", "porcao_g": 100},
              "texto solto",
              {"nome": "arroz", "porcao_g": 100, "kcal": 128}
            ]}
        """.trimIndent()
        val r = PlateAiParser.parseText(text)!!
        assertEquals(listOf("Arroz"), r.items.map { it.name })
    }

    @Test
    fun totalIsSumOfItemsNotTheAiTotal() {
        val text = """{"alimentos": [{"nome": "pão", "porcao_g": 50, "kcal": 150}], "total": {"kcal": 9999}}"""
        assertEquals(150.0, PlateAiParser.parseText(text)!!.kcal, 0.001)
    }

    @Test
    fun emptyListIsValidWithNote() {
        val r = PlateAiParser.parseText("""{"alimentos": [], "observacao": "A foto não mostra comida."}""")
        assertNotNull(r)
        assertTrue(r!!.items.isEmpty())
        assertEquals("A foto não mostra comida.", r.note)
    }

    @Test
    fun missingListIsOutOfFormat() {
        assertNull(PlateAiParser.parseText("""{"resposta": "não sei"}"""))
        assertNull(PlateAiParser.parseText("Não consegui ver o prato."))
        assertNull(PlateAiParser.parseText(null))
    }

    @Test
    fun trailingCommaTolerated() {
        val text = """{"alimentos": [{"nome": "banana", "porcao_g": 70, "kcal": 60,},],}"""
        assertEquals(1, PlateAiParser.parseText(text)?.items?.size)
    }

    @Test
    fun confidenceNumbers() {
        assertEquals("baixa", PlateAiParser.parseText("""{"alimentos": [], "confidence": 20}""")?.confidenceLabel)
        assertEquals("alta", PlateAiParser.parseText("""{"alimentos": [], "confianca": 0.9}""")?.confidenceLabel)
        assertEquals("média", PlateAiParser.parseText("""{"alimentos": [], "confianca": "média"}""")?.confidenceLabel)
        assertNull(PlateAiParser.parseText("""{"alimentos": []}""")?.confidenceLabel)
    }

    @Test
    fun splitHousehold() {
        assertEquals(2.0 to "colheres de sopa", PlateAiParser.splitHousehold("2 colheres de sopa"))
        assertEquals(1.5 to "concha", PlateAiParser.splitHousehold("1,5 concha"))
        assertEquals(0.5 to "xícara", PlateAiParser.splitHousehold("1/2 xícara"))
        assertEquals(0.5 to "xícara", PlateAiParser.splitHousehold("meia xícara"))
        assertEquals(1.0 to "porção", PlateAiParser.splitHousehold("porção"))
        assertNull(PlateAiParser.splitHousehold("  "))
    }
}
