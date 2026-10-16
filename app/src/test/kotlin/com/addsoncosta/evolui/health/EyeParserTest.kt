package com.addsoncosta.evolui.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeParserTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun simpleLines() {
        val e = EyeParser.parse("OD -2,00 -0,75 x 180\nOE -1,50 -0,50 x 90", today)
        assertEquals("-2,00", e.od.esferico)
        assertEquals("-0,75", e.od.cilindrico)
        assertEquals("180", e.od.eixo)
        assertEquals("-1,50", e.oe.esferico)
        assertEquals("-0,50", e.oe.cilindrico)
        assertEquals("90", e.oe.eixo)
    }

    @Test
    fun labeledValues() {
        val e = EyeParser.parse("O.D.: esf -1,25 cil -0,25 eixo 175\nOE: esf -1,50 cil -0,50 eixo 90", today)
        assertEquals("-1,25", e.od.esferico)
        assertEquals("-0,25", e.od.cilindrico)
        assertEquals("175", e.od.eixo)
        assertEquals("-1,50", e.oe.esferico)
        assertEquals("90", e.oe.eixo)
    }

    @Test
    fun dotsDegreesAndUnicodeMinus() {
        val e = EyeParser.parse("OD: \u22123.00 \u22121.00 10°\nOE: +0.75 plano", today)
        assertEquals("-3,00", e.od.esferico)
        assertEquals("-1,00", e.od.cilindrico)
        assertEquals("10", e.od.eixo)
        assertEquals("+0,75", e.oe.esferico)
        assertEquals("0,00", e.oe.cilindrico)
    }

    @Test
    fun tableWithAcuity() {
        val text = """
            Esférico   Cilíndrico   Eixo   AV
            OD   -2,00   -0,75   180   20/20
            OE   -1,75   -0,50   5   20/25
        """.trimIndent()
        val e = EyeParser.parse(text, today)
        assertEquals("-2,00", e.od.esferico)
        assertEquals("180", e.od.eixo)
        assertEquals("20/20", e.od.acuidade)
        assertEquals("5", e.oe.eixo)
        assertEquals("20/25", e.oe.acuidade)
    }

    @Test
    fun acuityPressureAndAddition() {
        val text = "AV 20/20\nPIO 14 mmHg\nAdição +2,00\nOD -1,00\nOE -1,25"
        val e = EyeParser.parse(text, today)
        assertEquals("20/20", e.od.acuidade)
        assertEquals("20/20", e.oe.acuidade)
        assertEquals("14", e.od.pio)
        assertEquals("14", e.oe.pio)
        assertEquals("+2,00", e.od.adicao)
        assertEquals("+2,00", e.oe.adicao)
        assertEquals("-1,00", e.od.esferico)
    }

    @Test
    fun pressurePerEye() {
        val e = EyeParser.parse("Tonometria: OD 14 mmHg  OE 16 mmHg", today)
        assertEquals("14", e.od.pio)
        assertEquals("16", e.oe.pio)
        assertNull(e.od.esferico)
    }

    @Test
    fun acuityPerEye() {
        val e = EyeParser.parse("Acuidade visual: OD 20/40 OE 20/30", today)
        assertEquals("20/40", e.od.acuidade)
        assertEquals("20/30", e.oe.acuidade)
    }

    @Test
    fun additionPerEyeLine() {
        val e = EyeParser.parse("OD +1,00\nOE +1,25\nAdição OD +2,50 OE +2,50", today)
        assertEquals("+2,50", e.od.adicao)
        assertEquals("+2,50", e.oe.adicao)
        assertEquals("+1,00", e.od.esferico)
    }

    @Test
    fun nearRowGivesAddition() {
        val text = "Longe\nOD -1,00 -0,50 x 90\nOE -1,00\nPerto\nOD +1,00 -0,50 x 90\nOE +1,00"
        val e = EyeParser.parse(text, today)
        assertEquals("-1,00", e.od.esferico)
        assertEquals("+2,00", e.od.adicao)
        assertEquals("+2,00", e.oe.adicao)
    }

    @Test
    fun dnpDiagnosesAndDate() {
        val text = "Receita de óculos - 10/09/2026\nOD -2,00\nOE -2,25\nDNP 31/32 mm\nDiagnóstico: miopia e astigmatismo; suspeita de glaucoma"
        val e = EyeParser.parse(text, today)
        assertEquals("31/32", e.dnp)
        assertEquals(listOf("Miopia", "Astigmatismo", "Glaucoma"), e.diagnoses)
        assertEquals(LocalDate.of(2026, 9, 10), e.date)
        assertTrue(e.toExtras()["diagnosticos"]!!.contains("Glaucoma"))
        assertEquals("-2,00", e.toExtras()["od_esferico"])
    }

    @Test
    fun olhoDireitoWords() {
        val e = EyeParser.parse("Olho direito: -0,50\nOlho esquerdo: -0,75 -0,25 x 170", today)
        assertEquals("-0,50", e.od.esferico)
        assertEquals("-0,75", e.oe.esferico)
        assertEquals("170", e.oe.eixo)
    }

    @Test
    fun nothingFound() {
        assertTrue(EyeParser.parse("Paciente compareceu à consulta.", today).isEmpty)
    }
}
