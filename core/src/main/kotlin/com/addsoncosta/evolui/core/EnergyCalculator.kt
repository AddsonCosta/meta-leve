package com.addsoncosta.evolui.core

import java.time.LocalDate
import java.time.Period
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Cálculos de energia e metas. Todos os valores em kcal, kg, cm. */
object EnergyCalculator {
    /** kcal aproximadas em 1 kg de gordura corporal. */
    const val KCAL_PER_KG = 7700.0

    /** Ingestão mínima recomendada sem acompanhamento médico. */
    const val MIN_SAFE_INTAKE = 1200.0

    /** Perda semanal máxima considerada segura. */
    const val MAX_SAFE_WEEKLY_KG = 1.0

    fun ageOn(birthDate: LocalDate, date: LocalDate): Int = Period.between(birthDate, date).years

    /** Taxa metabólica de repouso pela equação de Mifflin-St Jeor. */
    fun restingRate(sex: Sex, weightKg: Double, heightCm: Double, age: Int): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    /** Gasto do dia sem exercícios: repouso mais o nível de atividade. */
    fun dailyBurn(sex: Sex, weightKg: Double, heightCm: Double, age: Int, activity: ActivityLevel): Double =
        restingRate(sex, weightKg, heightCm, age) * (1 + activity.factor(sex))

    /**
     * Meta diária de balanço calórico. Positiva significa déficit (perder),
     * negativa significa superávit (ganhar), zero para manter.
     */
    fun dailyTarget(goalType: GoalType, weeklyChangeKg: Double): Double = when (goalType) {
        GoalType.LOSE -> abs(weeklyChangeKg) * KCAL_PER_KG / 7
        GoalType.GAIN -> -abs(weeklyChangeKg) * KCAL_PER_KG / 7
        GoalType.KEEP -> 0.0
    }

    /** Calorias que ainda podem ser consumidas hoje para cumprir a meta. */
    fun budget(burn: Double, exercise: Double, eaten: Double, target: Double): Double =
        burn + exercise - eaten - target

    /** Quanto se pode comer no dia inteiro (sem considerar o que já foi comido). */
    fun intakeGoal(burn: Double, exercise: Double, target: Double): Double = burn + exercise - target

    /**
     * Índice de desempenho em %: 100 significa que o balanço do dia bateu a meta.
     * Para manter peso (meta zero) o índice compara o consumo com o gasto.
     */
    fun performanceIndex(burn: Double, exercise: Double, eaten: Double, target: Double): Double {
        val balance = burn + exercise - eaten
        return if (abs(target) < 1e-6) {
            val total = burn + exercise
            if (total <= 0) 0.0 else 100 * eaten / total
        } else {
            100 * balance / target
        }
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100
        return weightKg / (m * m)
    }

    fun bmiLabel(bmi: Double): String = when {
        bmi < 18.5 -> "Abaixo do peso"
        bmi < 25 -> "Peso normal"
        bmi < 30 -> "Sobrepeso"
        else -> "Obesidade"
    }

    /** Data prevista para chegar ao peso desejado. */
    fun projectedDate(from: LocalDate, currentKg: Double, targetKg: Double, weeklyChangeKg: Double): LocalDate? {
        if (weeklyChangeKg <= 0) return null
        val weeks = abs(currentKg - targetKg) / weeklyChangeKg
        return from.plusDays(ceil(weeks * 7).toLong())
    }

    /** Gramas de cada macronutriente para um total de kcal e percentuais. */
    fun macroTargets(kcal: Double, carbPercent: Int, proteinPercent: Int, fatPercent: Int): Macros = Macros(
        carbG = kcal * carbPercent / 100 / 4,
        proteinG = kcal * proteinPercent / 100 / 4,
        fatG = kcal * fatPercent / 100 / 9,
    )

    /** kcal gastas num exercício pelo valor MET. */
    fun exerciseKcal(met: Double, weightKg: Double, minutes: Double): Double = met * weightKg * minutes / 60

    /** Resumo pronto para a tela Hoje. */
    fun summary(profile: Profile, date: LocalDate, weightKg: Double, activity: ActivityLevel, eaten: Double, exercise: Double): DaySummary {
        val age = ageOn(profile.birthDate, date)
        val burn = dailyBurn(profile.sex, weightKg, profile.heightCm, age, activity)
        val target = dailyTarget(profile.goalType, profile.weeklyChangeKg)
        val goal = intakeGoal(burn, exercise, target)
        return DaySummary(
            burn = burn,
            exercise = exercise,
            eaten = eaten,
            target = target,
            budget = budget(burn, exercise, eaten, target),
            intakeGoal = goal,
            performance = performanceIndex(burn, exercise, eaten, target),
            macros = macroTargets(goal.coerceAtLeast(0.0), profile.carbPercent, profile.proteinPercent, profile.fatPercent),
            belowSafeMinimum = goal < MIN_SAFE_INTAKE,
        )
    }
}

data class DaySummary(
    val burn: Double,
    val exercise: Double,
    val eaten: Double,
    val target: Double,
    val budget: Double,
    val intakeGoal: Double,
    val performance: Double,
    val macros: Macros,
    val belowSafeMinimum: Boolean,
) {
    val performanceRounded: Int get() = performance.roundToInt()
}
