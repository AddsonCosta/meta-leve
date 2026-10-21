package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.data.HealthType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAutoFillTest {
    private val today = LocalDate.of(2026, 10, 20)

    private val prescription = """
        Clínica Bem Estar
        Dr. João Carlos Pereira
        Cardiologia - CRM-SP 123456
        Paciente: Maria da Silva
        Uso oral
        1. Amoxicilina 500 mg ---------------- 21 cápsulas
        Tomar 1 cápsula de 8 em 8 horas por 7 dias.
        2. Losartana 50 mg
        1 comprimido ao dia, uso contínuo.
        Retorno em 30 dias.
        São Paulo, 12/10/2026
    """.trimIndent()

    @Test
    fun consultationFromPrescription() {
        val a = DocumentAutoFill.read(HealthType.CONSULTA, prescription, emptyList(), today)
        assertEquals(LocalDate.of(2026, 10, 12), a.date)
        assertEquals("Dr. João Carlos Pereira", a.professional)
        assertEquals("Clínica Bem Estar", a.place)
        assertEquals("Cardiologia", a.extras["especialidade"])
        assertEquals("Consulta de cardiologia", a.title)
        assertEquals("2026-11-11", a.extras["retorno"])
        assertEquals(2, a.prescription?.medicines?.size)
    }

    @Test
    fun medicationFromPrescription() {
        val a = DocumentAutoFill.read(HealthType.MEDICACAO, prescription, emptyList(), today)
        assertEquals("Amoxicilina", a.title)
        assertEquals("Dr. João Carlos Pereira (CRM-SP 123456)", a.professional)
        assertEquals("500 mg, 1 cápsula", a.extras["dose"])
        assertEquals("1 cápsula de 8 em 8 horas", a.extras["frequencia"])
        assertEquals("2026-10-12", a.extras["inicio"])
        assertEquals("2026-10-18", a.extras["fim"])
    }

    @Test
    fun dentalDocument() {
        val text = """
            Odonto Sorriso
            Orçamento / plano de tratamento
            Dr. Paulo Mendes - CRO-SP 45678
            Tratamento de canal no dente 36
            Restauração em resina nos elementos 14 e 15
            Data: 03/10/2026
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.ODONTOLOGIA, text, emptyList(), today)
        assertEquals("Odonto Sorriso", a.place)
        assertEquals("Dr. Paulo Mendes", a.professional)
        assertEquals("Tratamento de canal", a.extras["procedimento"])
        assertEquals("36, 14, 15", a.extras["dentes"])
        assertEquals(LocalDate.of(2026, 10, 3), a.date)
    }

    @Test
    fun physiotherapyReferral() {
        val text = """
            Clínica Movimento
            Encaminhamento para fisioterapia
            Fisioterapeuta Marcos Alves - CREFITO-3 12345-F
            Objetivo: recuperar o movimento do joelho direito
            10 sessões, 2x por semana
            15/09/2026
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.FISIOTERAPIA, text, emptyList(), today)
        assertEquals("Marcos Alves", a.professional)
        assertEquals("Clínica Movimento", a.place)
        assertEquals("2 vezes por semana", a.extras["frequencia"])
        assertEquals("10", a.extras["sessoes"])
        assertEquals("recuperar o movimento do joelho direito", a.extras["objetivo"])
    }

    @Test
    fun supplementLabel() {
        val text = "CREATINA MONOHIDRATADA\nGrowth Supplements\nPorção de 3 g (1 dosador)\nConsumir 1 vez ao dia"
        val a = DocumentAutoFill.read(HealthType.SUPLEMENTACAO, text, emptyList(), today)
        assertEquals("Creatina", a.title)
        assertEquals("Growth Supplements", a.extras["marca"])
        assertEquals("3 g (1 dosador)", a.extras["dose"])
        assertEquals("1 vez ao dia", a.extras["horario"])
    }

    @Test
    fun essentialKeysCoverSupplement() {
        assertTrue(DocumentAutoFill.essentialKeys(HealthType.SUPLEMENTACAO).containsAll(listOf(DocumentAutoFill.TITLE, "marca", "dose", "horario")))
    }

    @Test
    fun aiPromptListsFieldsAndConvertsAnswer() {
        val prompt = AiFormPrompt.instructions(HealthType.MEDICACAO)
        assertTrue(prompt.contains("\"dose\""))
        assertTrue(prompt.contains("\"medicamentos\""))
        assertTrue(prompt.contains("AAAA-MM-DD"))
        val a = AiFormPrompt.toAutoFill(
            HealthType.MEDICACAO,
            mapOf("data" to "2026-10-12", "profissional" to "Dra. Ana Souza", "inicio" to "12/10/2026", "local" to "null"),
            listOf(mapOf("nome" to "Amoxicilina", "dose" to "500 mg", "posologia" to "de 8 em 8 horas", "duracao" to "7 dias")),
        )
        assertEquals(LocalDate.of(2026, 10, 12), a.date)
        assertEquals("Amoxicilina", a.title)
        assertNull(a.place)
        assertEquals("500 mg", a.extras["dose"])
        assertEquals("2026-10-12", a.extras["inicio"])
        assertEquals("2026-10-18", a.extras["fim"])
        val med = a.prescription?.medicines?.single()
        assertNotNull(med)
        assertEquals(7, med?.durationDays)
    }
}
