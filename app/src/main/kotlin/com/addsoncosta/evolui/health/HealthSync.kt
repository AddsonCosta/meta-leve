package com.addsoncosta.evolui.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.data.WorkoutEntity
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** O que o Health Connect (alimentado pelo Samsung Health) tem para um dia. */
data class HealthDay(
    val steps: Long,
    val activeKcal: Double,
    val workouts: List<WorkoutEntity>,
    val latestWeightKg: Double?,
)

/**
 * Leitura do Health Connect. O Samsung Health grava passos, calorias, treinos e peso lá,
 * e o Evolui só lê (nunca escreve).
 */
class HealthSync(private val context: Context) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    val available: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect não instalado, mas pode ser instalado pela Play Store. */
    val needsInstall: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        available && runCatching { client.permissionController.getGrantedPermissions().containsAll(permissions) }.getOrDefault(false)

    suspend fun readDay(date: LocalDate, bodyWeightKg: Double): HealthDay {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val day = TimeRangeFilter.between(start, end)

        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = day,
            )
        )
        val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = day)).records
        val workouts = sessions.map { session ->
            val minutes = Duration.between(session.startTime, session.endTime).toMinutes().toDouble()
            val kcal = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                )
            )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            WorkoutEntity(
                epochDay = date.toEpochDay(),
                exerciseId = null,
                name = session.title?.takeIf { it.isNotBlank() } ?: exerciseName(session.exerciseType),
                minutes = minutes,
                // Sem calorias no registro, estima por uma intensidade moderada (MET 5).
                kcal = kcal?.takeIf { it > 0 } ?: EnergyCalculator.exerciseKcal(5.0, bodyWeightKg, minutes),
                externalId = session.metadata.id,
            )
        }
        val weights = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofDays(30)), end))
        ).records
        return HealthDay(
            steps = totals[StepsRecord.COUNT_TOTAL] ?: 0L,
            activeKcal = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
            workouts = workouts,
            latestWeightKg = weights.maxByOrNull { it.time }?.takeIf { !it.time.isBefore(start) }?.weight?.inKilograms,
        )
    }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminhada"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corrida"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Corrida na esteira"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Bicicleta ergométrica"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natação"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natação"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Musculação"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculação"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trilha"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elíptico"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dança"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Futebol"
        else -> "Treino (Samsung Health)"
    }
}
