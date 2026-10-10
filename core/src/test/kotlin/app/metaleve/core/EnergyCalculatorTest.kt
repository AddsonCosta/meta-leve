package app.metaleve.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCalculatorTest {
    private val delta = 0.01

    @Test
    fun restingRateMifflinStJeor() {
        assertEquals(1780.0, EnergyCalculator.restingRate(Sex.MALE, 80.0, 180.0, 30), delta)
        assertEquals(1370.25, EnergyCalculator.restingRate(Sex.FEMALE, 65.0, 165.0, 30), delta)
    }

    @Test
    fun dailyBurnAddsActivityFactorBySex() {
        assertEquals(1780.0 * 1.40, EnergyCalculator.dailyBurn(Sex.MALE, 80.0, 180.0, 30, ActivityLevel.LIGHT), delta)
        assertEquals(1370.25 * 1.35, EnergyCalculator.dailyBurn(Sex.FEMALE, 65.0, 165.0, 30, ActivityLevel.LIGHT), delta)
    }

    @Test
    fun dailyTargetFromWeeklyChange() {
        assertEquals(1100.0, EnergyCalculator.dailyTarget(GoalType.LOSE, 1.0), delta)
        assertEquals(-550.0, EnergyCalculator.dailyTarget(GoalType.GAIN, 0.5), delta)
        assertEquals(0.0, EnergyCalculator.dailyTarget(GoalType.KEEP, 0.5), delta)
    }

    @Test
    fun performanceIndexHitsHundredOnTarget() {
        assertEquals(100.0, EnergyCalculator.performanceIndex(2652.0, 0.0, 1552.0, 1100.0), delta)
        assertEquals(81.82, EnergyCalculator.performanceIndex(2652.0, 0.0, 1752.0, 1100.0), delta)
        assertEquals(100.0, EnergyCalculator.performanceIndex(2000.0, 0.0, 2000.0, 0.0), delta)
    }

    @Test
    fun budgetIsWhatIsLeftToEat() {
        assertEquals(0.0, EnergyCalculator.budget(2652.0, 0.0, 1552.0, 1100.0), delta)
        assertEquals(300.0, EnergyCalculator.budget(2652.0, 300.0, 1552.0, 1100.0), delta)
    }

    @Test
    fun projectedDate() {
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2026, 3, 12), EnergyCalculator.projectedDate(start, 90.0, 80.0, 1.0))
        assertNull(EnergyCalculator.projectedDate(start, 90.0, 80.0, 0.0))
    }

    @Test
    fun macroTargetsUseFourFourNine() {
        val m = EnergyCalculator.macroTargets(2000.0, 50, 20, 30)
        assertEquals(250.0, m.carbG, delta)
        assertEquals(100.0, m.proteinG, delta)
        assertEquals(66.67, m.fatG, delta)
    }

    @Test
    fun exerciseKcalFromMet() {
        assertEquals(280.0, EnergyCalculator.exerciseKcal(3.5, 80.0, 60.0), delta)
    }

    @Test
    fun summaryFlagsUnsafeIntake() {
        val p = Profile(
            name = "Teste", sex = Sex.FEMALE, birthDate = LocalDate.of(1990, 1, 1), heightCm = 160.0,
            startWeightKg = 60.0, startDate = LocalDate.of(2026, 1, 1), goalType = GoalType.LOSE,
            targetWeightKg = 55.0, weeklyChangeKg = 1.0, defaultActivity = ActivityLevel.SEDENTARY,
        )
        val s = EnergyCalculator.summary(p, LocalDate.of(2026, 1, 1), 60.0, ActivityLevel.SEDENTARY, 0.0, 0.0)
        assertTrue(s.belowSafeMinimum)
        assertEquals(s.burn - 1100.0, s.intakeGoal, delta)
    }
}
