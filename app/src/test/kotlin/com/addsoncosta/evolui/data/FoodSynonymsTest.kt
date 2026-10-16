package com.addsoncosta.evolui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSynonymsTest {
    private fun food(name: String, category: String) = FoodEntity(
        category = category,
        name = name,
        searchName = name.lowercase()
            .replace("á", "a").replace("ã", "a").replace("é", "e").replace("ç", "c").replace("ó", "o"),
        kcal = 100.0,
        protein = 0.0,
        fat = 0.0,
        carb = 0.0,
        fiber = 0.0,
    )

    private val tilapia = food("Tilápia, filé, grelhada", "Pescados e frutos do mar")
    private val camarao = food("Camarão, Rio Grande, grande, cozido", "Pescados e frutos do mar")
    private val batataDoce = food("Batata, doce, cozida", "Verduras, hortaliças e derivados")

    @Test
    fun peixeTrazPescadosSemFrutosDoMar() {
        val q = FoodSynonyms.parse("peixes")
        assertEquals(1, q.groups.size)
        assertTrue(q.rest.isEmpty())
        assertTrue(q.groups[0].matches(tilapia))
        assertFalse(q.groups[0].matches(camarao))
    }

    @Test
    fun expressaoFrutosDoMar() {
        val q = FoodSynonyms.parse("frutos do mar")
        assertEquals(1, q.groups.size)
        assertTrue(q.rest.isEmpty())
        assertTrue(q.groups[0].matches(camarao))
    }

    @Test
    fun palavraQueSobraFiltra() {
        val q = FoodSynonyms.parse("peixe assado")
        assertEquals(listOf("assado"), q.rest)
        val q2 = FoodSynonyms.parse("carne de porco")
        assertEquals(2, q2.groups.size)
        assertTrue(q2.rest.isEmpty())
    }

    @Test
    fun doceExcluiBatataDoce() {
        val g = FoodSynonyms.parse("doce").groups.single()
        assertTrue(g.excludes(batataDoce))
    }

    @Test
    fun pluralEGenero() {
        assertEquals("feijao", FoodSynonyms.singular("feijoes"))
        assertEquals("pao", FoodSynonyms.singular("paes"))
        assertEquals("pastel", FoodSynonyms.singular("pasteis"))
        assertTrue(FoodSynonyms.variants("moida").any { "carne, bovina, acem, moido, cozido".contains(it) })
        assertTrue(FoodSynonyms.variants("bananas").any { "banana, prata, crua".contains(it) })
        assertTrue(FoodSynonyms.variants("feijoes").any { "feijao, carioca, cozido".contains(it) })
    }
}
