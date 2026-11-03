package com.addsoncosta.evolui.habits

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Alarmes dos lembretes de água e tarefas, e os botões dos avisos ("Bebi 1 copo", "Fiz 10"). */
class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val kind = intent.getStringExtra(HabitReminders.EXTRA_KIND) ?: return
        val taskId = intent.getLongExtra(HabitReminders.EXTRA_TASK_ID, 0L)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    HabitReminders.ACTION_FIRE -> HabitReminders.onFire(context, kind, taskId)
                    HabitReminders.ACTION_LOG -> HabitReminders.onLog(context, kind, taskId, intent.getDoubleExtra(HabitReminders.EXTRA_AMOUNT, 0.0))
                }
            } catch (e: Exception) {
                // Nunca derrubar o app por causa de um lembrete.
            } finally {
                pending.finish()
            }
        }
    }
}
