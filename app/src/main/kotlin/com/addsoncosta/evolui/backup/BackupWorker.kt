package com.addsoncosta.evolui.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.addsoncosta.evolui.EvoluiApp

/** Grava o backup no arquivo escolhido (Google Drive ou outro local), uma vez por dia ou quando a pessoa pede. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EvoluiApp).container
        return when (container.backup.runDriveBackup()) {
            DriveResult.OK -> Result.success()
            // Sem arquivo ou sem acesso: só resolve escolhendo o local de novo (a tela mostra o aviso).
            DriveResult.NO_TARGET, DriveResult.NEEDS_NEW_LOCATION -> Result.failure()
            DriveResult.ERROR -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}
