package com.addsoncosta.evolui.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTest {
    @Test
    fun fillsValuesInOrder() {
        val t = I18n.Template.of("Faltam {} ml (≈ {} copos de 200 ml)", "{} ml left (≈ {} glasses of 200 ml)")
        val m = t.regex.matchEntire("Faltam 750 ml (≈ 4 copos de 200 ml)")!!
        assertEquals("750 ml left (≈ 4 glasses of 200 ml)", t.fill(m.groupValues.drop(1)))
    }

    @Test
    fun numberedHolesChangeOrder() {
        val t = I18n.Template.of("{} de {}", "{1}: {0}")
        assertEquals("7: 3", t.fill(listOf("3", "7")))
    }

    @Test
    fun shortTemplatesNeedNumbers() {
        assertTrue(I18n.Template.of("{} de {}", "{} of {}").needsDigits)
        assertFalse(I18n.Template.of("Medalha de {}", "{} medal").needsDigits)
    }

    @Test
    fun doesNotMatchOtherText() {
        val t = I18n.Template.of("Dia {}", "Day {}")
        assertNull(t.regex.matchEntire("Diário"))
    }
}
