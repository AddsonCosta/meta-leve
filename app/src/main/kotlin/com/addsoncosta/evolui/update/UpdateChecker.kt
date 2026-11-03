package com.addsoncosta.evolui.update

import com.addsoncosta.evolui.i18n.tr
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.addsoncosta.evolui.BuildConfig
import com.addsoncosta.evolui.R
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de uma busca por versão nova na Galaxy Store. */
sealed interface UpdateResult {
    data class Available(val versionName: String?, val versionCode: Long?) : UpdateResult
    data object UpToDate : UpdateResult
    /** Sem internet, loja fora do ar ou app ainda não publicado. */
    data object Unknown : UpdateResult
}

/**
 * Procura versão nova do Evolui na Galaxy Store (Samsung). Só manda o nome do app, a versão instalada
 * e o modelo do celular, que é o que a loja pede; nada dos seus dados.
 */
object UpdateChecker {
    const val STORE_APP_ID = BuildConfig.APPLICATION_ID
    private const val CHANNEL_ID = "atualizacoes"
    private const val WORK_NAME = "procurar_atualizacao"
    private const val PREFS = "atualizacao"
    private const val KEY_LAST_CHECK = "ultima_busca"
    private const val KEY_NOTIFIED = "versao_avisada"
    private const val DAY = 24 * 3_600_000L

    /** Lê a resposta da loja: a versão publicada (versionCode) ou o código de resultado (2 = há versão nova). */
    fun parse(xml: String, installedCode: Long): UpdateResult {
        fun tag(name: String) = Regex("<$name>\\s*(?:<!\\[CDATA\\[)?([^<\\]]*)").find(xml)?.groupValues?.get(1)?.trim()
        val code = tag("versionCode")?.toLongOrNull()
        val name = tag("versionName")?.takeIf { it.isNotEmpty() }
        val result = tag("resultCode")
        return when {
            code != null && code > installedCode -> UpdateResult.Available(name, code)
            code != null -> UpdateResult.UpToDate
            result == "2" -> UpdateResult.Available(name, null)
            result == "1" -> UpdateResult.UpToDate
            else -> UpdateResult.Unknown
        }
    }

    private fun mccMnc(context: Context): Pair<String, String> {
        val op = runCatching { context.getSystemService(TelephonyManager::class.java)?.networkOperator }.getOrNull().orEmpty()
        return if (op.length >= 5) op.take(3) to op.drop(3) else "724" to "00"
    }

    /** Pergunta à Galaxy Store. Chamar fora da thread principal. */
    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val (mcc, mnc) = mccMnc(context)
        val installed = BuildConfig.VERSION_CODE.toLong()
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://vas.samsungapps.com/stub/stubUpdateCheck.as?appId=${enc(STORE_APP_ID)}&versionCode=$installed" +
            "&deviceId=${enc(Build.MODEL)}&mcc=$mcc&mnc=$mnc&csc=&sdkVer=${Build.VERSION.SDK_INT}&pd=0"
        val result = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            try {
                if (c.responseCode !in 200..299) UpdateResult.Unknown
                else parse(c.inputStream.bufferedReader().use { it.readText() }, installed)
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            UpdateResult.Unknown
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        result
    }

    /** Ao abrir o app: no máximo uma busca por dia. */
    fun shouldCheckOnOpen(context: Context): Boolean =
        System.currentTimeMillis() - context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0) > DAY

    /** Abre a página do Evolui na Galaxy Store (ou no navegador, se a loja não estiver instalada). */
    fun openStore(context: Context) {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$STORE_APP_ID")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(app)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/$STORE_APP_ID")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Busca diária em segundo plano (com internet), que avisa por notificação. */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Mostra o aviso de versão nova uma vez por versão. */
    fun notifyAvailable(context: Context, result: UpdateResult.Available) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = result.versionCode?.toString() ?: result.versionName ?: "nova"
        if (prefs.getString(KEY_NOTIFIED, null) == key) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.let { m ->
                if (m.getNotificationChannel(CHANNEL_ID) == null) {
                    m.createNotificationChannel(NotificationChannel(CHANNEL_ID, tr("Atualizações do app"), NotificationManager.IMPORTANCE_DEFAULT))
                }
            }
        }
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$STORE_APP_ID"))
        val pi = PendingIntent.getActivity(context, 720_000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("Nova versão do Evolui"))
            .setContentText(tr("A versão ${result.versionName ?: "nova"} está na Galaxy Store. Toque para atualizar."))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(720_000, n)
            prefs.edit().putString(KEY_NOTIFIED, key).apply()
        } catch (e: SecurityException) {
            // Sem permissão: ignora.
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = UpdateChecker.check(applicationContext)
        if (result is UpdateResult.Available) UpdateChecker.notifyAvailable(applicationContext, result)
        return Result.success()
    }
}
