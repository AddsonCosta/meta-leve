package com.addsoncosta.evolui.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.core.Sex
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val sex: String,
    val birthEpochDay: Long,
    val heightCm: Double,
    val startWeightKg: Double,
    val startEpochDay: Long,
    val goalType: String,
    val targetWeightKg: Double,
    val weeklyChangeKg: Double,
    val defaultActivity: String,
    val carbPercent: Int,
    val proteinPercent: Int,
    val fatPercent: Int,
) {
    fun toProfile() = Profile(
        name = name,
        sex = Sex.valueOf(sex),
        birthDate = LocalDate.ofEpochDay(birthEpochDay),
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        startDate = LocalDate.ofEpochDay(startEpochDay),
        goalType = GoalType.valueOf(goalType),
        targetWeightKg = targetWeightKg,
        weeklyChangeKg = weeklyChangeKg,
        defaultActivity = ActivityLevel.valueOf(defaultActivity),
        carbPercent = carbPercent,
        proteinPercent = proteinPercent,
        fatPercent = fatPercent,
    )

    companion object {
        fun from(p: Profile) = ProfileEntity(
            name = p.name,
            sex = p.sex.name,
            birthEpochDay = p.birthDate.toEpochDay(),
            heightCm = p.heightCm,
            startWeightKg = p.startWeightKg,
            startEpochDay = p.startDate.toEpochDay(),
            goalType = p.goalType.name,
            targetWeightKg = p.targetWeightKg,
            weeklyChangeKg = p.weeklyChangeKg,
            defaultActivity = p.defaultActivity.name,
            carbPercent = p.carbPercent,
            proteinPercent = p.proteinPercent,
            fatPercent = p.fatPercent,
        )
    }
}

/** Dados de um dia: peso medido, nível de atividade e anotação. */
@Serializable
@Entity(tableName = "day_log")
data class DayLogEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double? = null,
    val activity: String? = null,
    val note: String? = null,
)

/** Alimento com valores por 100 g. */
@Serializable
@Entity(tableName = "food", indices = [Index("searchName")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val name: String,
    val searchName: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
    val servingName: String? = null,
    val servingGrams: Double? = null,
    val favorite: Boolean = false,
    val custom: Boolean = false,
    val tacoId: Int? = null,
)

enum class MealType(val label: String) {
    BREAKFAST("Café da manhã"),
    LUNCH("Almoço"),
    SNACK("Lanche"),
    DINNER("Jantar"),
}

/** Alimento consumido. Os valores ficam gravados, mesmo que o alimento mude depois. */
@Serializable
@Entity(tableName = "meal", indices = [Index("epochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val foodId: Long?,
    val name: String,
    val grams: Double,
    val mealType: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
)

@Serializable
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val name: String,
    val met: Double,
    val custom: Boolean = false,
    val favorite: Boolean = false,
)

@Serializable
@Entity(tableName = "workout", indices = [Index("epochDay")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val exerciseId: Long?,
    val name: String,
    val minutes: Double,
    val kcal: Double,
    /** Id do registro no Health Connect, para não importar o mesmo treino duas vezes. */
    val externalId: String? = null,
)

/** Um jejum. Sem [endMillis] significa que está em andamento. */
@Serializable
@Entity(tableName = "fast")
data class FastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long? = null,
    val targetHours: Int,
)

/** Foto de progresso guardada na pasta interna do app. */
@Serializable
@Entity(tableName = "photo", indices = [Index("epochDay")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val fileName: String,
    val weightKg: Double? = null,
)

data class DayTotal(val epochDay: Long, val total: Double)
