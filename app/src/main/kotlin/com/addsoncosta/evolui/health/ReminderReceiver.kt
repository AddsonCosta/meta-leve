package com.addsoncosta.evolui.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.addsoncosta.evolui.EvoluiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lembretes do calendário de saúde: mostra o aviso na hora marcada e
 * reagenda todos os avisos depois que o celular reinicia.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        Reminders.ensureChannel(context)
                        Reminders.rescheduleAll(context)
                    }
                    Reminders.ACTION_REMIND -> {
                        val id = intent.getLongExtra(Reminders.EXTRA_EVENT_ID, 0L)
                        if (id != 0L) {
                            val app = context.applicationContext as EvoluiApp
                            val event = app.container.database.healthEventDao().get(id)
                            if (event != null && Reminders.isDue(event)) Reminders.notify(context, event)
                        }
                    }
                }
            } catch (_: Exception) {
                // Nunca derrubar o app por causa de um lembrete.
            } finally {
                pending.finish()
            }
        }
    }
}
