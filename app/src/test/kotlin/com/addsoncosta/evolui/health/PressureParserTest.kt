package com.addsoncosta.evolui.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureParserTest {
    private fun assertReading(text: String, sys: Int, dia: Int, pulse: Int?) {
        val g = PressureParser.parse(text)
        assertTrue("nada encontrado em: $text", g != null)
        assertEquals("sistólica em: $text", sys, g!!.systolic)
        assertEquals("diastólica em: $text", dia, g.diastolic)
        assertEquals("pulso em: $text", pulse, g.pulse)
    }

    @Test
    fun labelsOnOneLine() {
        assertReading("SYS 128 DIA 82 PUL 70", 128, 82, 70)
        assertReading("sys128 dia82 pul70", 128, 82, 70)
    }

    @Test
    fun labelsOnDisplayWithUnits() {
        assertReading("SYS mmHg\n135\nDIA mmHg\n88\nPUL /min\n76", 135, 88, 76)
        assertReading("SYS\nmmHg 142   DIA\nmmHg 91\nPULSE 70/min", 142, 91, 70)
        assertReading("Sistólica 120 Diastólica 80 Pulso 65", 120, 80, 65)
    }

    @Test
    fun slashAndX() {
        assertReading("128/82", 128, 82, null)
        assertReading("128 / 82 mmHg 70 bpm", 128, 82, 70)
        assertReading("128 x 82", 128, 82, null)
        assertReading("130X85 p 72", 130, 85, 72)
        assertReading("128/82 70", 128, 82, 70)
    }

    @Test
    fun brazilianCmHg() {
        assertReading("12 por 8", 120, 80, null)
        assertReading("13,5 x 8,5", 135, 85, null)
        assertReading("12x8", 120, 80, null)
    }

    @Test
    fun stackedDisplayNumbers() {
        assertReading("128\n82\n70", 128, 82, 70)
        assertReading("M 03\n08:30\n131\n84\n69", 131, 84, 69)
        assertReading("151\n96", 151, 96, null)
    }

    @Test
    fun ocrConfusions() {
        assertReading("1O8/7O", 108, 70, null)
        assertReading("12O\n8O\n7l", 120, 80, 71)
    }

    @Test
    fun handwrittenNoteIgnoresDates() {
        val text = "16/10/2026 manhã 128/82 70\n17/10 noite 135/88 74"
        val all = PressureParser.candidates(text)
        assertEquals(128, all[0].systolic)
        assertEquals(82, all[0].diastolic)
        assertEquals(70, all[0].pulse)
        assertTrue(all.any { it.systolic == 135 && it.diastolic == 88 && it.pulse == 74 })
        assertTrue(all.none { it.systolic == 160 || it.systolic == 170 })
    }

    @Test
    fun nothingPlausible() {
        assertNull(PressureParser.parse(""))
        assertNull(PressureParser.parse("Omron HEM-7120"))
        assertNull(PressureParser.parse("16/10/2026"))
        assertNull(PressureParser.parse("82/128"))
        assertNull(PressureParser.parse("300 20"))
    }

    @Test
    fun classificationHigherCategoryWins() {
        assertEquals(BpCategory.OTIMA, BpCategory.of(115, 75))
        assertEquals(BpCategory.NORMAL, BpCategory.of(120, 79))
        assertEquals(BpCategory.NORMAL, BpCategory.of(119, 84))
        assertEquals(BpCategory.PRE_HIPERTENSAO, BpCategory.of(130, 70))
        assertEquals(BpCategory.PRE_HIPERTENSAO, BpCategory.of(125, 89))
        assertEquals(BpCategory.ESTAGIO_1, BpCategory.of(140, 80))
        assertEquals(BpCategory.ESTAGIO_1, BpCategory.of(120, 95))
        assertEquals(BpCategory.ESTAGIO_2, BpCategory.of(165, 95))
        assertEquals(BpCategory.ESTAGIO_2, BpCategory.of(130, 105))
        assertEquals(BpCategory.ESTAGIO_3, BpCategory.of(180, 100))
        assertEquals(BpCategory.ESTAGIO_3, BpCategory.of(150, 110))
    }
}
