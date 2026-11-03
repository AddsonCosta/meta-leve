package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable

/** Unidade da meta de uma tarefa diária. */
enum class TaskUnit(val label: String, val short: String) {
    REPS("repetições", "rep."),
    SECONDS("segundos", "s"),
    MINUTES("minutos", "min"),
}

/**
 * Tarefa de treino opcional, repetida todo dia (ex.: 30 flexões, 2 min de prancha).
 * [remindersPerDay] avisos entre [startMinute] e [endMinute] (minutos desde a meia-noite); 0 = sem aviso.
 */
@Serializable
@Entity(tableName = "daily_task")
data class DailyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Nome de [TaskUnit]. */
    val unit: String,
    val target: Double,
    val remindersPerDay: Int,
    val startMinute: Int,
    val endMinute: Int,
    val active: Boolean = true,
) {
    val taskUnit: TaskUnit get() = TaskUnit.entries.firstOrNull { it.name == unit } ?: TaskUnit.REPS

    /** Quanto fazer em cada aviso (a meta dividida pelos avisos, arredondada para cima). */
    val perReminder: Double
        get() = if (remindersPerDay <= 1) target else kotlin.math.ceil(target / remindersPerDay)
}

/** Uma série feita de uma tarefa (ex.: 10 flexões às 9h). */
@Serializable
@Entity(tableName = "daily_task_log", indices = [Index("epochDay"), Index("taskId")])
data class DailyTaskLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val epochDay: Long,
    val amount: Double,
    val epochMillis: Long,
)

@Dao
interface DailyTaskDao {
    @Query("SELECT * FROM daily_task ORDER BY id")
    fun observeTasks(): Flow<List<DailyTaskEntity>>

    @Query("SELECT * FROM daily_task")
    suspend fun all(): List<DailyTaskEntity>

    @Query("SELECT * FROM daily_task WHERE id = :id")
    suspend fun get(id: Long): DailyTaskEntity?

    @Upsert
    suspend fun save(task: DailyTaskEntity): Long

    @Delete
    suspend fun delete(task: DailyTaskEntity)

    @Query("DELETE FROM daily_task_log WHERE taskId = :taskId")
    suspend fun deleteLogsOf(taskId: Long)

    @Query("SELECT * FROM daily_task_log WHERE epochDay = :day ORDER BY epochMillis")
    fun observeLogs(day: Long): Flow<List<DailyTaskLogEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM daily_task_log WHERE taskId = :taskId AND epochDay = :day")
    suspend fun doneOn(taskId: Long, day: Long): Double

    @Query("SELECT * FROM daily_task_log")
    suspend fun allLogs(): List<DailyTaskLogEntity>

    @Insert
    suspend fun insertLog(log: DailyTaskLogEntity)

    @Delete
    suspend fun deleteLog(log: DailyTaskLogEntity)

    @Insert
    suspend fun insertAll(tasks: List<DailyTaskEntity>)

    @Insert
    suspend fun insertLogs(logs: List<DailyTaskLogEntity>)

    @Query("DELETE FROM daily_task")
    suspend fun clear()

    @Query("DELETE FROM daily_task_log")
    suspend fun clearLogs()
}

/** Uma tarefa com o que já foi feito no dia. */
data class TaskProgress(val task: DailyTaskEntity, val done: Double, val logs: List<DailyTaskLogEntity>) {
    val complete: Boolean get() = done >= task.target
    val fraction: Float get() = if (task.target <= 0) 0f else (done / task.target).toFloat().coerceIn(0f, 1f)
}

class DailyTaskRepository(private val db: AppDatabase) {
    private val dao get() = db.dailyTaskDao()

    fun tasks(): Flow<List<DailyTaskEntity>> = dao.observeTasks()

    /** Tarefas ativas com o progresso do dia. */
    fun day(date: LocalDate): Flow<List<TaskProgress>> =
        combine(dao.observeTasks(), dao.observeLogs(date.toEpochDay())) { tasks, logs ->
            tasks.filter { it.active }.map { t ->
                val mine = logs.filter { it.taskId == t.id }
                TaskProgress(t, mine.sumOf { it.amount }, mine)
            }
        }

    suspend fun save(task: DailyTaskEntity): Long {
        val id = dao.save(task)
        // Upsert devolve -1 quando atualiza uma linha existente.
        return if (id > 0) id else task.id
    }

    suspend fun delete(task: DailyTaskEntity) {
        dao.deleteLogsOf(task.id)
        dao.delete(task)
    }

    suspend fun log(task: DailyTaskEntity, date: LocalDate, amount: Double) {
        if (amount <= 0) return
        dao.insertLog(DailyTaskLogEntity(taskId = task.id, epochDay = date.toEpochDay(), amount = amount, epochMillis = System.currentTimeMillis()))
    }

    suspend fun undo(log: DailyTaskLogEntity) = dao.deleteLog(log)
}
