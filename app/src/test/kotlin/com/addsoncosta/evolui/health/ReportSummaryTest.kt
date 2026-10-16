package com.addsoncosta.evolui.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSummaryTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun ultrasoundWithConclusion() {
        val text = """
            Clínica Imagem Total
            Paciente: Maria da Silva   Data de nascimento: 01/02/1970
            Médico solicitante: Dr. Carlos Lima CRM 12345
            Data do exame: 03/10/2026
            ULTRASSONOGRAFIA DE ABDOME TOTAL
            Técnica: exame realizado com transdutor convexo.
            Fígado de dimensões normais, contornos regulares e ecotextura homogênea.
            Vesícula biliar com cálculo móvel de 1,2 cm.
            CONCLUSÃO:
            Colelitíase.
            Demais estruturas sem alterações.

            Dra. Ana Souza
            CRM 54321 - Médica radiologista
        """.trimIndent()
        val s = ReportSummaryParser.parse(text, today)
        assertEquals("Ultrassonografia de abdome total", s.examName)
        assertEquals(LocalDate.of(2026, 10, 3), s.date)
        assertEquals("Clínica Imagem Total", s.place)
        assertEquals("Dr. Carlos Lima", s.requestingDoctor)
        assertEquals("Dra. Ana Souza", s.reportingDoctor)
        assertEquals("Colelitíase. Demais estruturas sem alterações.", s.conclusion)
        assertTrue(s.looksLikeReport)
    }

    @Test
    fun impressionSameLine() {
        val text = "Ressonância magnética do joelho direito\nAchados: menisco medial com sinal alterado.\nImpressão diagnóstica: Rotura do corno posterior do menisco medial.\nDr. João Pereira - CRM-SP 99999"
        val s = ReportSummaryParser.parse(text, today)
        assertEquals("Rotura do corno posterior do menisco medial.", s.conclusion)
        assertEquals("Dr. João Pereira", s.reportingDoctor)
        assertEquals("Ressonância magnética do joelho direito", s.examName)
    }

    @Test
    fun opinionHeading() {
        val text = "ECOCARDIOGRAMA TRANSTORÁCICO\nFração de ejeção 65%.\nOpinião:\nEcocardiograma dentro dos limites da normalidade.\nAssinado eletronicamente"
        val s = ReportSummaryParser.parse(text, today)
        assertEquals("Ecocardiograma dentro dos limites da normalidade.", s.conclusion)
    }

    @Test
    fun fallbackLastParagraph() {
        val text = "Raio-X de tórax PA\n\nCampos pleuropulmonares sem alterações significativas.\nSeios costofrênicos livres.\n\nwww.clinica.com.br"
        val s = ReportSummaryParser.parse(text, today)
        assertNotNull(s.conclusion)
        assertTrue(s.conclusion!!.startsWith("Campos pleuropulmonares"))
        assertFalse(s.conclusion!!.contains("www"))
    }

    @Test
    fun bloodTestIsNotComplex() {
        assertFalse(ReportSummaryParser.isComplexReport("Hemograma completo\nHemoglobina 14,2 g/dL"))
        assertTrue(ReportSummaryParser.isComplexReport("Tomografia computadorizada de crânio"))
    }
}
