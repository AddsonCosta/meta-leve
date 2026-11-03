package com.addsoncosta.evolui.habits

import com.addsoncosta.evolui.i18n.tr
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.addsoncosta.evolui.EvoluiApp
import com.addsoncosta.evolui.R
import com.addsoncosta.evolui.data.DailyTaskEntity
import com.addsoncosta.evolui.data.DailyTaskRepository
import com.addsoncosta.evolui.data.waterGoalMl
import com.addsoncosta.evolui.ui.MainActivity
import com.addsoncosta.evolui.ui.components.fmt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

/**
 * Lembretes de hábitos: água ao longo do dia e as tarefas diárias de treino.
 * Cada lembrete tem um alarme inexato que, ao tocar, mostra o aviso (se ainda fizer sentido) e marca o próximo.
 */
object HabitReminders {
    const val CHANNEL_ID = "lembretes_habitos"
    const val ACTION_FIRE = "com.addsoncosta.evolui.action.LEMBRETE_HABITO"
    const val ACTION_LOG = "com.addsoncosta.evolui.action.REGISTRAR_HABITO"
    const val EXTRA_KIND = "tipo"
    const val EXTRA_TASK_ID = "tarefa"
    const val EXTRA_AMOUNT = "quantidade"
    const val KIND_WATER = "agua"
    const val KIND_TASK = "tarefa"

    /** Um copo de água em cada aviso. */
    const val WATER_GLASS_ML = 250.0
    private const val WATER_CODE = 700_000
    private const val TASK_CODE = 710_000
    private const val FALLBACK_WATER_GOAL = 2000.0

    /** Horários (minutos desde a meia-noite) de [count] avisos distribuídos entre [start] e [end]. */
    fun slots(start: Int, end: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val s = start.coerceIn(0, 24 * 60 - 1)
        val e = end.coerceIn(s, 24 * 60 - 1)
        if (count == 1 || e == s) return listOf(s)
        return (0 until count).map { i -> s + (e - s) * i / (count - 1) }.distinct()
    }

    /** Quantos avisos de água: um copo de 250 ml por aviso, entre 4 e 16 por dia. */
    fun waterCount(goalMl: Double): Int = ceil(goalMl / WATER_GLASS_ML).toInt().coerceIn(4, 16)

    /** Próximo horário depois de [now], hoje ou amanhã. */
    fun nextTrigger(slots: List<Int>, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (slots.isEmpty()) return null
        val minuteNow = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        val next = slots.sorted().firstOrNull { it > minuteNow }
        return if (next != null) today.atStartOfDay().plusMinutes(next.toLong())
        else today.plusDays(1).atStartOfDay().plusMinutes(slots.min().toLong())
    }

    private fun fireIntent(context: Context, kind: String, taskId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, HabitReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("evolui://habito/$kind/$taskId"))
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_TASK_ID, taskId)
        val code = if (kind == KIND_WATER) WATER_CODE else TASK_CODE + taskId.toInt()
        return PendingIntent.getBroadcast(context, code, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(context: Context, kind: String, taskId: Long, at: LocalDateTime) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = fireIntent(context, kind, taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun cancelAlarm(context: Context, kind: String, taskId: Long) {
        val pi = fireIntent(context, kind, taskId, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    private suspend fun waterGoal(app: EvoluiApp): Double {
        val c = app.container
        val today = LocalDate.now()
        val weight = c.repository.latestWeight(today).first() ?: c.repository.profile.first()?.startWeightKg
        return waterGoalMl(weight ?: 0.0, c.settings.waterGoalMl.value).takeIf { it > 0 } ?: FALLBACK_WATER_GOAL
    }

    /** Agenda (ou cancela) o próximo aviso de água. Chamar fora da thread principal. */
    suspend fun scheduleWater(context: Context) {
        val app = context.applicationContext as EvoluiApp
        val prefs = app.container.settings.waterReminders.value
        cancelAlarm(context, KIND_WATER, 0)
        if (!prefs.on) return
        val slots = slots(prefs.startMinute, prefs.endMinute, waterCount(waterGoal(app)))
        nextTrigger(slots)?.let { setAlarm(context, KIND_WATER, 0, it) }
    }

    /** Agenda (ou cancela) o próximo aviso de uma tarefa. */
    fun scheduleTask(context: Context, task: DailyTaskEntity) {
        cancelAlarm(context, KIND_TASK, task.id)
        if (!task.active || task.remindersPerDay <= 0 || task.id == 0L) return
        nextTrigger(slots(task.startMinute, task.endMinute, task.remindersPerDay))?.let { setAlarm(context, KIND_TASK, task.id, it) }
    }

    fun cancelTask(context: Context, taskId: Long) = cancelAlarm(context, KIND_TASK, taskId)

    /** Reagenda tudo (ao abrir o app, depois de reiniciar o celular ou de atualizar). */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as EvoluiApp
        scheduleWater(context)
        app.container.database.dailyTaskDao().all().forEach { scheduleTask(context, it) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, tr("Água e tarefas de treino"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("Lembretes de beber água e das tarefas diárias de treino.")
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean =
        !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)

    /** O alarme tocou: mostra o aviso se ainda fizer sentido e marca o próximo. */
    suspend fun onFire(context: Context, kind: String, taskId: Long) {
        val app = context.applicationContext as EvoluiApp
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        when (kind) {
            KIND_WATER -> {
                val prefs = app.container.settings.waterReminders.value
                if (!prefs.on) return
                val goal = waterGoal(app)
                val drunk = app.container.repository.dayLogOnce(today)?.waterMl ?: 0.0
                val slots = slots(prefs.startMinute, prefs.endMinute, waterCount(goal))
                val minute = now.hour * 60 + now.minute
                // Quanto já devia ter bebido até este aviso; se já bebeu isso, não incomoda.
                val reached = slots.count { it <= minute + 5 }
                val expected = goal * reached / slots.size.coerceAtLeast(1)
                if (drunk < goal && drunk < expected) {
                    notifyWater(context, drunk, goal)
                }
                scheduleWater(context)
            }
            KIND_TASK -> {
                val task = app.container.database.dailyTaskDao().get(taskId) ?: return cancelTask(context, taskId)
                val done = app.container.database.dailyTaskDao().doneOn(task.id, today.toEpochDay())
                if (task.active && done < task.target) notifyTask(context, task, done)
                scheduleTask(context, task)
            }
        }
    }

    /** Toque no botão do aviso ("Bebi 1 copo", "Fiz 10"). */
    suspend fun onLog(context: Context, kind: String, taskId: Long, amount: Double) {
        val app = context.applicationContext as EvoluiApp
        val today = LocalDate.now()
        when (kind) {
            KIND_WATER -> app.container.repository.updateDay(today) { day ->
                day.copy(waterMl = ((day.waterMl ?: 0.0) + amount).takeIf { it > 0 })
            }
            KIND_TASK -> {
                val task = app.container.database.dailyTaskDao().get(taskId) ?: return
                DailyTaskRepository(app.container.database).log(task, today, amount)
            }
        }
        NotificationManagerCompat.from(context).cancel(notificationId(kind, taskId))
    }

    private fun notificationId(kind: String, taskId: Long): Int = if (kind == KIND_WATER) WATER_CODE else TASK_CODE + taskId.toInt()

    private fun openApp(context: Context, code: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun logAction(context: Context, kind: String, taskId: Long, amount: Double): PendingIntent {
        val intent = Intent(context, HabitReceiver::class.java)
            .setAction(ACTION_LOG)
            .setData(Uri.parse("evolui://registrar/$kind/$taskId"))
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_AMOUNT, amount)
        return PendingIntent.getBroadcast(context, notificationId(kind, taskId) + 50_000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun show(context: Context, id: Int, title: String, text: String, actionLabel: String, action: PendingIntent) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr(title))
            .setContentText(tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(tr(text)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id))
            .addAction(0, tr(actionLabel), action)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permissão retirada: ignora.
        }
    }

    private fun notifyWater(context: Context, drunk: Double, goal: Double) {
        show(
            context, notificationId(KIND_WATER, 0),
            "Hora de beber água",
            "Hoje: ${drunk.fmt()} de ${goal.fmt()} ml. Um copo agora ajuda a chegar na meta.",
            "Bebi 1 copo (${WATER_GLASS_ML.fmt()} ml)",
            logAction(context, KIND_WATER, 0, WATER_GLASS_ML),
        )
    }

    private fun notifyTask(context: Context, task: DailyTaskEntity, done: Double) {
        val left = (task.target - done).coerceAtLeast(0.0)
        val now = minOf(task.perReminder, left)
        val unit = task.taskUnit.short
        show(
            context, notificationId(KIND_TASK, task.id),
            "Tarefa do dia: ${task.name}",
            "Faça ${now.fmt()} $unit agora. Hoje: ${done.fmt()} de ${task.target.fmt()} $unit.",
            "Fiz ${now.fmt()} $unit",
            logAction(context, KIND_TASK, task.id, now),
        )
    }
}
