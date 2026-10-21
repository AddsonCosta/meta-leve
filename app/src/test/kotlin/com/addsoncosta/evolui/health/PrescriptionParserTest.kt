package com.addsoncosta.evolui.health

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionParserTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun typicalPrescriptionWithThreeMedicines() {
        val text = """
            Dr. João Carlos Pereira
            Clínica Médica - CRM-SP 123456
            Rua das Flores, 100 - Centro - São Paulo/SP
            RECEITUÁRIO
            Paciente: Maria da Silva
            Uso oral
            1. Amoxicilina 500 mg ---------------- 21 cápsulas
            Tomar 1 cápsula de 8 em 8 horas por 7 dias.
            2. Dipirona 500mg ------------ 1 caixa
            Tomar 1 comprimido de 6/6h se dor ou febre.
            3. Losartana potássica 50 mg
            1 comprimido ao dia, uso contínuo.
            São Paulo, 12/10/2026
        """.trimIndent()
        val p = PrescriptionParser.parse(text, today)
        assertEquals(LocalDate.of(2026, 10, 12), p.date)
        assertEquals("Dr. João Carlos Pereira", p.doctor)
        assertEquals("CRM-SP 123456", p.crm)
        assertEquals("Clínica médica", p.specialty)
        assertTrue(p.looksLikePrescription)
        assertEquals(3, p.medicines.size)

        val amox = p.medicines[0]
        assertEquals("Amoxicilina", amox.name)
        assertEquals("500 mg, 1 cápsula", amox.dose)
        assertEquals("1 cápsula de 8 em 8 horas", amox.posology)
        assertEquals("por 7 dias", amox.duration)
        assertEquals(7, amox.durationDays)
        assertFalse(amox.continuous)
        assertFalse(amox.asNeeded)

        val dip = p.medicines[1]
        assertEquals("Dipirona", dip.name)
        assertEquals("500mg, 1 comprimido", dip.dose)
        assertEquals("1 comprimido de 6 em 6 horas se dor ou febre", dip.posology)
        assertTrue(dip.asNeeded)

        val los = p.medicines[2]
        assertEquals("Losartana potássica", los.name)
        assertEquals("50 mg, 1 comprimido", los.dose)
        assertEquals("1 comprimido ao dia", los.posology)
        assertEquals("Uso contínuo", los.duration)
        assertTrue(los.continuous)
        assertNull(los.durationDays)
    }

    @Test
    fun upperCaseHeaderCrmAfterNumberAndWrittenDate() {
        val text = """
            DRA. ANA PAULA SOUZA
            CARDIOLOGISTA
            CRM 54321/RJ   RQE 1234
            Nome: José Santos
            USO CONTÍNUO
            AAS 100 mg
            Tomar 1 comprimido após o almoço.
            Atenolol 25mg
            Tomar 1 comprimido pela manhã e 1 à noite.
            Rio de Janeiro, 5 de outubro de 2026
        """.trimIndent()
        val p = PrescriptionParser.parse(text, today)
        assertEquals("Dra. Ana Paula Souza", p.doctor)
        assertEquals("CRM-RJ 54321", p.crm)
        assertEquals("Cardiologia", p.specialty)
        assertEquals(LocalDate.of(2026, 10, 5), p.date)
        assertEquals(listOf("AAS", "Atenolol"), p.medicines.map { it.name })
        assertTrue(p.medicines.all { it.continuous })
        assertEquals("1 comprimido após o almoço", p.medicines[0].posology)
        assertEquals("25mg, 1 comprimido", p.medicines[1].dose)
    }

    @Test
    fun instructionOnSameLineAndWeeks() {
        val text = """
            Prescrição
            Omeprazol 20 mg - tomar 1 cápsula em jejum por 4 semanas
            Vitamina D 7.000 UI - 1 cápsula 1x por semana durante 2 meses
            Dr. Paulo Mendes - CRM/MG 98.765
            Belo Horizonte, 01/09/2026
        """.trimIndent()
        val p = PrescriptionParser.parse(text, today)
        assertEquals("Dr. Paulo Mendes", p.doctor)
        assertEquals("CRM-MG 98765", p.crm)
        assertEquals(2, p.medicines.size)
        val ome = p.medicines[0]
        assertEquals("Omeprazol", ome.name)
        assertEquals("20 mg, 1 cápsula", ome.dose)
        assertEquals("1 cápsula em jejum", ome.posology)
        assertEquals("por 4 semanas", ome.duration)
        assertEquals(28, ome.durationDays)
        val vit = p.medicines[1]
        assertEquals("Vitamina D", vit.name)
        assertEquals("7.000 UI, 1 cápsula", vit.dose)
        assertEquals(60, vit.durationDays)
    }

    @Test
    fun drops() {
        val text = "Receita\n1) Paracetamol gotas 200 mg/ml\nTomar 40 gotas a cada 6 horas se febre, por 3 dias\nDra. Beatriz Lopes CRM-PR 11111 Pediatria"
        val p = PrescriptionParser.parse(text, today)
        assertEquals(1, p.medicines.size)
        val m = p.medicines[0]
        assertEquals("Paracetamol gotas", m.name)
        assertEquals("200 mg/ml, 40 gotas", m.dose)
        assertEquals(3, m.durationDays)
        assertTrue(m.asNeeded)
        assertEquals("Pediatria", p.specialty)
        assertEquals("Dra. Beatriz Lopes", p.doctor)
    }

    @Test
    fun controlledPrescriptionForm() {
        val text = """
            RECEITUÁRIO DE CONTROLE ESPECIAL
            IDENTIFICAÇÃO DO EMITENTE
            Nome: Dra. Carla Mendes Rocha
            CRM: 445566  UF: SP
            Psiquiatria
            Paciente: Pedro Alves
            Endereço: Rua A, 10
            Prescrição:
            Sertralina 50mg -------- 30 comprimidos
            Tomar 1 comprimido pela manhã, uso contínuo
            Clonazepam 0,5 mg ....... 1 caixa
            1 cp à noite por 30 dias
            1ª via farmácia
            Data: 02/10/2026
        """.trimIndent()
        val p = PrescriptionParser.parse(text, today)
        assertEquals("Dra. Carla Mendes Rocha", p.doctor)
        assertEquals("CRM 445566", p.crm)
        assertEquals("Psiquiatria", p.specialty)
        assertEquals(LocalDate.of(2026, 10, 2), p.date)
        assertEquals(listOf("Sertralina", "Clonazepam"), p.medicines.map { it.name })
        assertTrue(p.medicines[0].continuous)
        assertEquals("1 comprimido pela manhã", p.medicines[0].posology)
        assertEquals("0,5 mg, 1 cp", p.medicines[1].dose)
        assertEquals(30, p.medicines[1].durationDays)
        assertEquals(listOf(21 * 60), MedicationSchedule.times(p.medicines[1].posology))
    }

    @Test
    fun notAPrescription() {
        val p = PrescriptionParser.parse("Hemoglobina 14,2 g/dL\nLeucócitos 6.500 /mm3\nLaboratório Sabin", today)
        assertNull(p.doctor)
        assertNull(p.crm)
    }

    @Test
    fun scheduleTimes() {
        assertEquals(listOf(0, 8 * 60, 16 * 60), MedicationSchedule.times("1 cápsula de 8 em 8 horas"))
        assertEquals(listOf(8 * 60, 20 * 60), MedicationSchedule.times("12/12h"))
        assertEquals(listOf(8 * 60), MedicationSchedule.times("1 comprimido ao dia"))
        assertEquals(listOf(8 * 60, 14 * 60, 20 * 60), MedicationSchedule.times("3 vezes ao dia"))
        assertEquals(listOf(8 * 60, 20 * 60), MedicationSchedule.times("2x ao dia"))
        assertEquals(listOf(7 * 60 + 30, 22 * 60), MedicationSchedule.times("às 07:30 e 22h"))
        assertEquals(listOf(22 * 60), MedicationSchedule.times("1 comprimido ao deitar"))
        assertEquals(listOf(8 * 60, 21 * 60), MedicationSchedule.times("1 pela manhã e 1 à noite"))
        assertEquals(emptyList<Int>(), MedicationSchedule.times("1 vez por semana"))
        assertEquals(emptyList<Int>(), MedicationSchedule.times(""))
        assertEquals(listOf(8 * 60, 20 * 60), MedicationSchedule.parseTimes("08:00, 20:00"))
    }

    @Test
    fun scheduleSlotsSkipPastTimesToday() {
        val slots = MedicationSchedule.slots(startDay = 100, days = 2, times = listOf(8 * 60, 20 * 60), todayEpochDay = 100, nowMinute = 12 * 60)
        assertEquals(listOf(100L to 20 * 60, 101L to 8 * 60, 101L to 20 * 60), slots)
        assertEquals(7, MedicationSchedule.days(7, false))
        assertEquals(MedicationSchedule.CONTINUOUS_DAYS, MedicationSchedule.days(null, true))
    }
}
