package app.metaleve.core

import java.time.LocalDate

enum class Sex { MALE, FEMALE }

enum class GoalType { LOSE, KEEP, GAIN }

/**
 * Nível de atividade do dia, sem contar exercícios registrados.
 * Os fatores são a fração da taxa metabólica de repouso somada a ela.
 */
enum class ActivityLevel(val label: String, val description: String, val maleFactor: Double, val femaleFactor: Double) {
    SEDENTARY("Sedentário", "Pouco ou nenhum movimento", 0.15, 0.15),
    LIGHT("Levemente ativo", "Trabalho de escritório, tarefas de casa", 0.40, 0.35),
    MODERATE("Moderadamente ativo", "Trabalho em pé, indústria leve, estudante ativo", 0.50, 0.45),
    VERY("Muito ativo", "Trabalho braçal, atletas, militares em treino", 0.85, 0.70),
    EXTREME("Extremamente ativo", "Trabalho físico pesado o dia inteiro", 1.10, 1.00);

    fun factor(sex: Sex): Double = if (sex == Sex.MALE) maleFactor else femaleFactor
}

data class Profile(
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val startWeightKg: Double,
    val startDate: LocalDate,
    val goalType: GoalType,
    val targetWeightKg: Double,
    val weeklyChangeKg: Double,
    val defaultActivity: ActivityLevel,
    val carbPercent: Int = 50,
    val proteinPercent: Int = 20,
    val fatPercent: Int = 30,
)

data class Macros(val carbG: Double, val proteinG: Double, val fatG: Double)
