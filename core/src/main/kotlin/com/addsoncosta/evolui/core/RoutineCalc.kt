package com.addsoncosta.evolui.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Rotina do dia a dia (lavar louça, levar as crianças, trabalho em pé...): atividades que se repetem
 * em certos dias da semana e entram sozinhas no gasto do dia.
 *
 * Para não contar duas vezes o que o repouso e o nível de atividade já contam, só entra o gasto
 * acima do repouso (MET líquido = MET − 1).
 * Dias da semana guardados num número: bit 0 = segunda ... bit 6 = domingo.
 */
object RoutineCalc {
    const val ALL_DAYS = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun has(daysMask: Int, day: DayOfWeek): Boolean = daysMask and bit(day) != 0

    fun toggle(daysMask: Int, day: DayOfWeek): Int = daysMask xor bit(day)

    fun appliesOn(daysMask: Int, date: LocalDate): Boolean = has(daysMask, date.dayOfWeek)

    /** Só o que passa do repouso. */
    fun netMet(met: Double): Double = (met - 1.0).coerceAtLeast(0.0)

    /** kcal acima do repouso, com o gasto em repouso da pessoa (mesma fórmula dos treinos). */
    fun netKcal(met: Double, minutes: Double, restingKcalPerDay: Double): Double =
        EnergyCalculator.personalExerciseKcal(netMet(met), minutes, restingKcalPerDay)

    /** O mesmo, pela fórmula clássica do peso, quando ainda não há perfil. */
    fun netKcalByWeight(met: Double, weightKg: Double, minutes: Double): Double {
        val net = netMet(met)
        if (net <= 0 || minutes <= 0 || weightKg <= 0) return 0.0
        return EnergyCalculator.exerciseKcal(net, weightKg, minutes)
    }

    private val SHORT = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

    /** Abreviação de cada dia, de segunda a domingo. */
    fun shortName(day: DayOfWeek): String = SHORT[day.value - 1]

    /** "Todos os dias", "Seg a sex", "Fim de semana" ou "Seg, Qua, Sex". */
    fun daysLabel(daysMask: Int): String = when (daysMask and ALL_DAYS) {
        0 -> "Nenhum dia"
        ALL_DAYS -> "Todos os dias"
        WEEKDAYS -> "Seg a sex"
        WEEKEND -> "Fim de semana"
        else -> DayOfWeek.entries.filter { has(daysMask, it) }.joinToString(", ") { shortName(it) }
    }
}
