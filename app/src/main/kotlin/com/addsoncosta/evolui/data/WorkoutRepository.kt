package com.addsoncosta.evolui.data

import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.health.HealthSync
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Treinos: registrar, editar e apagar no Evolui, mantendo o Health Connect em dia
 * (grava os treinos do Evolui lá e importa os dos outros apps sem duplicar).
 */
class WorkoutRepository(
    private val db: AppDatabase,
    private val health: HealthSync,
    private val settings: AppSettings,
) {
    constructor(container: AppContainer) : this(container.database, container.health, container.settings)

    private val dao get() = db.workoutDao()

    /** Todas as atividades, das mais recentes para as mais antigas. */
    fun history(): Flow<List<WorkoutEntity>> = dao.observeHistory()

    /**
     * Registra um exercício no dia [date]. Sem [startTime], conta que terminou agora (ou neste horário, num dia passado).
     * Se a opção estiver ligada e houver permissão, também grava no Health Connect.
     */
    suspend fun log(date: LocalDate, exercise: ExerciseEntity, minutes: Double, kcal: Double, startTime: LocalTime? = null) {
        val (start, end) = interval(date, minutes, startTime)
        val workout = WorkoutEntity(
            epochDay = date.toEpochDay(),
            exerciseId = exercise.id,
            name = exercise.name,
            minutes = minutes,
            kcal = kcal,
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli(),
        )
        val id = dao.insertReturningId(workout)
        if (settings.writeWorkoutsToHealth) push(workout.copy(id = id), exercise.category)
    }

    /** Muda a duração; as calorias acompanham na mesma proporção. */
    suspend fun updateMinutes(workout: WorkoutEntity, minutes: Double) {
        if (minutes <= 0) return
        val kcal = if (workout.minutes > 0) workout.kcal * minutes / workout.minutes else workout.kcal
        val end = workout.startMillis?.let { it + (minutes * 60_000).roundToLong() } ?: workout.endMillis
        var updated = workout.copy(minutes = minutes, kcal = kcal, endMillis = end)
        val written = workout.hcWrittenId
        if (written != null) {
            // Regrava no Health Connect com a duração nova.
            health.deleteWorkout(written)
            updated = updated.copy(hcWrittenId = null)
            dao.update(updated)
            val category = workout.exerciseId?.let { db.exerciseDao().byId(it)?.category }
            push(updated, category)
        } else {
            dao.update(updated)
        }
    }

    /** Apaga do Evolui; se veio de outro app, não volta mais; se o Evolui gravou no Health Connect, apaga lá também. */
    suspend fun delete(workout: WorkoutEntity) {
        dao.delete(workout)
        workout.externalId?.let { health.ignoreWorkout(it, workout.startMillis, workout.endMillis) }
        workout.hcWrittenId?.let { health.deleteWorkout(it) }
    }

    /**
     * Grava treinos vindos do Health Connect, sem duplicar: ignora os já importados, os apagados pelo usuário
     * e os que coincidem no horário com um treino já guardado (importado ou gravado pelo Evolui). Devolve quantos entraram.
     */
    suspend fun importFromHealth(candidates: List<WorkoutEntity>): Int = lock.withLock {
        if (candidates.isEmpty()) return@withLock 0
        val known = dao.externalIds().toSet()
        val ignored = health.ignoredWorkouts()
        val ignoredIds = ignored.map { it.id }.toSet()
        val days = candidates.map { it.epochDay }
        val existing = dao.between(days.min() - 1, days.max() + 1)
            .filter { it.externalId != null || it.hcWrittenId != null }
            .mapNotNull { w -> if (w.startMillis != null && w.endMillis != null) w.startMillis to w.endMillis else null }
        val ignoredIntervals = ignored.mapNotNull { i -> if (i.startMillis != null && i.endMillis != null) i.startMillis to i.endMillis else null }
        val fresh = mutableListOf<WorkoutEntity>()
        for (c in candidates) {
            val id = c.externalId ?: continue
            if (id in known || id in ignoredIds) continue
            val s = c.startMillis
            val e = c.endMillis
            if (s != null && e != null) {
                val taken = existing + ignoredIntervals + fresh.mapNotNull { f ->
                    if (f.startMillis != null && f.endMillis != null) f.startMillis to f.endMillis else null
                }
                if (taken.any { (a, b) -> HealthSync.sameWorkout(a, b, s, e) }) continue
            }
            fresh += c
        }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        fresh.size
    }

    /** Envia ao Health Connect os treinos do Evolui deste dia que ainda não foram (ex.: permissão dada depois). */
    suspend fun pushPending(date: LocalDate) {
        if (!settings.writeWorkoutsToHealth) return
        lock.withLock {
            if (!health.hasWritePermissions()) return@withLock
            val day = date.toEpochDay()
            val pending = dao.between(day, day).filter {
                it.externalId == null && it.source == null && it.hcWrittenId == null && it.startMillis != null && it.endMillis != null
            }
            for (w in pending) {
                val category = w.exerciseId?.let { db.exerciseDao().byId(it)?.category }
                push(w, category)
            }
        }
    }

    private suspend fun push(workout: WorkoutEntity, category: String?) {
        val start = workout.startMillis ?: return
        val end = workout.endMillis ?: return
        val id = health.writeWorkout(
            name = workout.name,
            exerciseType = HealthSync.exerciseTypeFor(workout.name, category),
            start = Instant.ofEpochMilli(start),
            end = Instant.ofEpochMilli(end),
            kcal = workout.kcal,
        ) ?: return
        dao.setHcWrittenId(workout.id, id)
    }

    /** Horário do treino: começa em [startTime] ou termina agora; nunca termina no futuro. */
    private fun interval(date: LocalDate, minutes: Double, startTime: LocalTime?): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val durationMs = (minutes * 60_000).roundToLong().coerceAtLeast(60_000L)
        val now = Instant.now()
        var start = if (startTime != null) {
            date.atTime(startTime).atZone(zone).toInstant()
        } else {
            date.atTime(LocalTime.now()).atZone(zone).toInstant().minusMillis(durationMs)
        }
        if (start.plusMillis(durationMs).isAfter(now)) start = now.minusMillis(durationMs)
        return start to start.plusMillis(durationMs)
    }

    private companion object {
        /** Evita que a tela Hoje e a seção Treino importem o mesmo treino ao mesmo tempo. */
        val lock = Mutex()
    }
}
