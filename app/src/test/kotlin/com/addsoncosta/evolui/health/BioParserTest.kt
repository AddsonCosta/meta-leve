package com.addsoncosta.evolui.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BioParserTest {
    private fun map(text: String) = BioParser.parse(text).associate { it.name to it.value }

    @Test
    fun inBodyLike() {
        val text = """
            InBody 270 - Análise da Composição Corporal
            Altura 175 cm   Idade 40   Data 12/10/2026 08:30
            Água Corporal Total   40,1 L   (36,0 ~ 44,0)
            Proteína   10,9 kg
            Minerais   3,71 kg
            Massa de Gordura Corporal   17,7 kg
            Peso   72,4 kg   (55,0 ~ 74,4)
            Massa Muscular Esquelética   31,2 kg
            IMC   23,6 kg/m²
            PGC (Percentual de Gordura Corporal)   24,4 %
            Nível de Gordura Visceral   8
            Área de Gordura Visceral 95,3 cm²
            Taxa Metabólica Basal   1.585 kcal
            Massa Livre de Gordura   54,7 kg
            Controle de Peso   -2,0 kg
        """.trimIndent()
        val m = map(text)
        assertEquals(72.4, m[BioParser.PESO]!!, 0.001)
        assertEquals(17.7, m[BioParser.GORDURA_KG]!!, 0.001)
        assertEquals(24.4, m[BioParser.GORDURA_PCT]!!, 0.001)
        assertEquals(31.2, m[BioParser.MUSCULO]!!, 0.001)
        assertEquals(54.7, m[BioParser.MAGRA]!!, 0.001)
        assertEquals(40.1, m[BioParser.AGUA_L]!!, 0.001)
        assertEquals(55.4, m[BioParser.AGUA_PCT]!!, 0.001)
        assertEquals(8.0, m[BioParser.VISCERAL]!!, 0.001)
        assertEquals(1585.0, m[BioParser.TMB]!!, 0.001)
        assertEquals(23.6, m[BioParser.IMC]!!, 0.001)
        assertNull(m["Proteína"])
        assertEquals(10, m.size)
    }

    @Test
    fun tanitaLike() {
        val text = """
            Peso 80,5 kg
            Gordura corporal 22,5 %
            Massa gorda 18,1 kg
            Massa muscular 59,4 kg
            Água corporal 55,0 %
            Gordura visceral 9
            TMB 1780 kcal
            Idade metabólica 38 anos
            Massa óssea 3,0 kg
            IMC 25,1
        """.trimIndent()
        val m = map(text)
        assertEquals(80.5, m[BioParser.PESO]!!, 0.001)
        assertEquals(22.5, m[BioParser.GORDURA_PCT]!!, 0.001)
        assertEquals(18.1, m[BioParser.GORDURA_KG]!!, 0.001)
        assertEquals(59.4, m[BioParser.MUSCULO]!!, 0.001)
        assertEquals(55.0, m[BioParser.AGUA_PCT]!!, 0.001)
        assertEquals(9.0, m[BioParser.VISCERAL]!!, 0.001)
        assertEquals(1780.0, m[BioParser.TMB]!!, 0.001)
        assertEquals(38.0, m[BioParser.IDADE]!!, 0.001)
        assertEquals(25.1, m[BioParser.IMC]!!, 0.001)
        assertFalse(m.containsKey("Massa óssea"))
    }

    @Test
    fun omronLikeMusclePercent() {
        val text = "Peso: 70.0 kg   IMC: 24.2\nGordura corporal: 28.3%\nMúsculo esquelético: 30.0%\nGordura visceral: 7\nMetabolismo basal: 1520 kcal\nIdade corporal: 45"
        val m = map(text)
        assertEquals(70.0, m[BioParser.PESO]!!, 0.001)
        assertEquals(24.2, m[BioParser.IMC]!!, 0.001)
        assertEquals(28.3, m[BioParser.GORDURA_PCT]!!, 0.001)
        assertEquals(21.0, m[BioParser.MUSCULO]!!, 0.001)
        assertEquals(7.0, m[BioParser.VISCERAL]!!, 0.001)
        assertEquals(1520.0, m[BioParser.TMB]!!, 0.001)
        assertEquals(45.0, m[BioParser.IDADE]!!, 0.001)
    }

    @Test
    fun ignoresIdealWeight() {
        val m = map("Peso ideal 68,0 kg\nPeso 75,3 kg")
        assertEquals(75.3, m[BioParser.PESO]!!, 0.001)
    }
}
