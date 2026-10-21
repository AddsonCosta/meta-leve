package com.addsoncosta.evolui.core

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCalcTest {
    private val delta = 0.01

    @Test
    fun netMetCountsOnlyAboveRest() {
        assertEquals(1.0, RoutineCalc.netMet(2.0), delta)
        assertEquals(0.0, RoutineCalc.netMet(1.0), delta)
        assertEquals(0.0, RoutineCalc.netMet(0.5), delta)
    }

    @Test
    fun netKcalUsesRestingRate() {
        // Lavar louça (MET 2) por 30 min com repouso de 1440 kcal/dia: 1 × 1 kcal/min × 30.
        assertEquals(30.0, RoutineCalc.netKcal(2.0, 30.0, 1440.0), delta)
        assertEquals(
            EnergyCalculator.personalExerciseKcal(3.0, 60.0, 1780.0),
            RoutineCalc.netKcal(4.0, 60.0, 1780.0),
            delta,
        )
        assertEquals(0.0, RoutineCalc.netKcal(1.0, 60.0, 1780.0), delta)
    }

    @Test
    fun netKcalByWeightFallback() {
        assertEquals(40.0, RoutineCalc.netKcalByWeight(3.0, 80.0, 15.0), delta)
        assertEquals(0.0, RoutineCalc.netKcalByWeight(1.0, 80.0, 15.0), delta)
    }

    @Test
    fun daysOfWeekMask() {
        val monday = LocalDate.of(2026, 10, 19)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertTrue(RoutineCalc.appliesOn(RoutineCalc.WEEKDAYS, monday))
        assertFalse(RoutineCalc.appliesOn(RoutineCalc.WEEKDAYS, monday.plusDays(5)))
        assertTrue(RoutineCalc.appliesOn(RoutineCalc.WEEKEND, monday.plusDays(6)))
        val mask = RoutineCalc.toggle(RoutineCalc.toggle(0, DayOfWeek.MONDAY), DayOfWeek.FRIDAY)
        assertEquals("Seg, Sex", RoutineCalc.daysLabel(mask))
        assertEquals("Todos os dias", RoutineCalc.daysLabel(RoutineCalc.ALL_DAYS))
        assertEquals("Seg a sex", RoutineCalc.daysLabel(RoutineCalc.WEEKDAYS))
        assertEquals("Seg", RoutineCalc.daysLabel(RoutineCalc.toggle(mask, DayOfWeek.FRIDAY)))
    }
}
