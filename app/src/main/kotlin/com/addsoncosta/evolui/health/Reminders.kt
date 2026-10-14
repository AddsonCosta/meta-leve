package com.addsoncosta.evolui.health

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
import com.addsoncosta.evolui.data.HealthEventEntity
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lembretes locais do calendário de saúde. Usa alarmes inexatos (setAndAllowWhileIdle),
 * que não pedem permissão especial; o aviso pode chegar alguns minutos depois do horário.
 */
object Reminders {
    const val CHANNEL_ID = "lembretes_saude"
    const val ACTION_REMIND = "com.addsoncosta.evolui.action.LEMBRETE_SAUDE"
    const val EXTRA_EVENT_ID = "event_id"

    /** Horário usado para eventos de dia inteiro (sem hora marcada). */
    const val ALL_DAY_MINUTE = 9 * 60

    private val scheduledOnce = AtomicBoolean(false)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))

    /** Quando o aviso deve tocar, em milissegundos; nulo se o evento não tem lembrete. */
    fun triggerMillis(event: HealthEventEntity, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val before = event.remindMinutesBefore ?: return null
        val minute = event.minuteOfDay ?: ALL_DAY_MINUTE
        return LocalDate.ofEpochDay(event.epochDay).atStartOfDay(zone)
            .plusMinutes(minute.toLong())
            .minusMinutes(before.toLong())
            .toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, eventId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.parse("evolui://lembrete/$eventId"))
            .putExtra(EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(context, eventId.toInt(), intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Agenda (ou reagenda) o aviso do evento. Cancela se não houver lembrete, se já foi feito ou se já passou. */
    fun schedule(context: Context, event: HealthEventEntity) {
        if (event.id == 0L) return
        cancel(context, event.id)
        if (event.done) return
        val at = triggerMillis(event) ?: return
        if (at <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, event.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun scheduleAll(context: Context, events: List<HealthEventEntity>) {
        events.forEach { schedule(context, it) }
    }

    /** Cancela o aviso do evento (ao apagar, por exemplo). */
    fun cancel(context: Context, eventId: Long) {
        val pi = pendingIntent(context, eventId, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    /** Reagenda todos os lembretes futuros, lendo o banco. Chamar fora da thread principal. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as EvoluiApp
        val today = LocalDate.now().toEpochDay()
        // Inclui o dia anterior: um lembrete "1 dia antes" de amanhã pode ser hoje.
        val events = app.container.database.healthEventDao().pendingReminders(today - 1)
        scheduleAll(context, events)
    }

    /** Reagenda uma vez por processo (ex.: ao abrir o app depois de uma atualização). */
    suspend fun rescheduleOnce(context: Context) {
        if (scheduledOnce.compareAndSet(false, true)) rescheduleAll(context)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Lembretes de saúde", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos de consultas, exames, remédios e outros eventos do calendário de saúde."
        }
        manager.createNotificationChannel(channel)
    }

    /** O app pode mostrar notificações? (No Android 13+ depende da permissão.) */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Mostra a notificação do evento. */
    fun notify(context: Context, event: HealthEventEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, event.id.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val type = HealthType.entries.firstOrNull { it.name == event.type }
        val title = if (type != null && !event.title.contains(type.singular, ignoreCase = true)) {
            "${type.singular}: ${event.title}"
        } else event.title
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(whenText(event))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(whenText(event), event.notes?.takeIf { it.isNotBlank() }).joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(event.id.toInt(), notification)
        } catch (e: SecurityException) {
            // Permissão retirada entre a verificação e o aviso: ignora.
        }
    }

    /** "Hoje às 14:30 · Clínica Vida" */
    private fun whenText(event: HealthEventEntity): String {
        val date = LocalDate.ofEpochDay(event.epochDay)
        val today = LocalDate.now()
        val day = when (date) {
            today -> "Hoje"
            today.plusDays(1) -> "Amanhã"
            else -> "Dia ${date.format(dateFormat)}"
        }
        val time = event.minuteOfDay?.let { " às " + LocalTime.of(it / 60, it % 60).format(timeFormat) } ?: ""
        val place = event.place?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        return day + time + place
    }

    /** Usado pelo receptor para saber se o evento ainda vale (ex.: não foi apagado). */
    internal fun isDue(event: HealthEventEntity): Boolean {
        if (event.done || event.remindMinutesBefore == null) return false
        val at = triggerMillis(event) ?: return false
        // Aceita avisos atrasados até 12 h (alarmes inexatos podem atrasar).
        return System.currentTimeMillis() - at < 12 * 3_600_000L
    }
}
