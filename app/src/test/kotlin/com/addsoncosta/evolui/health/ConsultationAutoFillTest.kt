package com.addsoncosta.evolui.health

import com.addsoncosta.evolui.data.HealthType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leitura de PDFs de consulta de formatos diferentes (atestado, prontuário, encaminhamento, receita). */
class ConsultationAutoFillTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun medicalRecordWithSections() {
        val text = """
            HOSPITAL SANTA LUZIA
            Ambulatório de Ortopedia
            Paciente: José Ferreira    Data de nascimento: 04/05/1980
            Data do atendimento: 28/10/2026
            Queixa principal: dor no joelho direito há 2 meses,
            piora ao subir escadas.
            Exame físico: edema discreto.
            Hipótese diagnóstica: condromalácia patelar
            Conduta: fisioterapia 2x por semana e gelo 20 min.
            Exames solicitados: ressonância magnética do joelho direito
            Retorno em 30 dias com o exame.
            Dr. Ricardo Alves
            CRM-RJ 765432
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.CONSULTA, text, emptyList(), today)
        assertEquals(LocalDate.of(2026, 10, 28), a.date)
        assertEquals("Dr. Ricardo Alves", a.professional)
        assertEquals("HOSPITAL SANTA LUZIA", a.place)
        assertEquals("Ortopedia", a.extras["especialidade"])
        assertEquals("Consulta de ortopedia", a.title)
        assertTrue(a.extras["motivo"]!!.contains("piora ao subir escadas"))
        assertTrue(a.extras["diagnostico"]!!.startsWith("condromalácia patelar"))
        assertTrue(a.extras["diagnostico"]!!.contains("fisioterapia"))
        assertEquals("ressonância magnética do joelho direito", a.extras["procedimento"])
        assertEquals("2026-11-27", a.extras["retorno"])
        assertFalseMedicines(a)
    }

    @Test
    fun referralLetter() {
        val text = """
            UBS Vila Nova
            ENCAMINHAMENTO
            Encaminho a paciente Ana Lima ao cardiologista para avaliação
            de sopro cardíaco e palpitações.
            Solicito ecocardiograma e Holter 24 h.
            Belo Horizonte, 15 de outubro de 2026
            Dra. Carla Mendes - Clínica médica
            CRM-MG 112233
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.CONSULTA, text, emptyList(), today)
        assertEquals(LocalDate.of(2026, 10, 15), a.date)
        assertEquals("Dra. Carla Mendes", a.professional)
        assertEquals("UBS Vila Nova", a.place)
        assertTrue(a.extras["encaminhamento"]!!.startsWith("Cardiologia"))
        assertTrue(a.extras["procedimento"]!!.contains("ecocardiograma"))
        assertNotNull(a.title)
    }

    @Test
    fun prescriptionBecomesConsultationRecipe() {
        val text = """
            Consultório Dr. Marcos Teixeira - Dermatologia
            CRM-SP 99887
            Uso tópico
            1. Adapaleno gel 0,1% ---- 1 bisnaga
            Aplicar à noite no rosto por 60 dias.
            2. Protetor solar FPS 50
            Aplicar pela manhã.
            03/11/2026
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.CONSULTA, text, emptyList(), today)
        assertEquals(LocalDate.of(2026, 11, 3), a.date)
        assertEquals("Dermatologia", a.extras["especialidade"])
        assertTrue(a.extras["receita"]!!.contains("Adapaleno"))
        assertEquals(2, a.prescription?.medicines?.size)
    }

    @Test
    fun plainLetterWithoutSpecialty() {
        val text = """
            Atestado
            Atesto para os devidos fins que o paciente esteve em consulta médica
            nesta data, das 14h às 15h.
            Rio de Janeiro, 30/10/2026
            Dr. Paulo Henrique Souza
            CRM 54321
        """.trimIndent()
        val a = DocumentAutoFill.read(HealthType.CONSULTA, text, emptyList(), today)
        assertEquals(LocalDate.of(2026, 10, 30), a.date)
        assertEquals("Dr. Paulo Henrique Souza", a.professional)
        assertEquals("Consulta com Dr. Paulo Henrique Souza", a.title)
        assertNull(a.extras["encaminhamento"])
    }

    private fun assertFalseMedicines(a: AutoFill) {
        assertTrue(a.prescription == null || a.prescription!!.medicines.isEmpty())
    }
}
