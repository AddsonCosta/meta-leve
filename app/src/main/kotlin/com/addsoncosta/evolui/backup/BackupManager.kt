package com.addsoncosta.evolui.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.addsoncosta.evolui.data.Repository
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Situação do backup automático (arquivo escolhido pelo seletor do Android, normalmente no Google Drive). */
data class DriveBackupState(
    /** Arquivo escolhido; nulo = ainda não escolheu. */
    val target: Uri? = null,
    /** Nome do arquivo escolhido, para mostrar na tela. */
    val targetName: String? = null,
    val auto: Boolean = false,
    /** Último backup gravado com sucesso. */
    val lastMillis: Long? = null,
    /** Mensagem do último erro, para mostrar na tela; nulo = tudo certo. */
    val error: String? = null,
    /** O Evolui perdeu o acesso ao arquivo: é preciso escolher o local de novo. */
    val needsNewLocation: Boolean = false,
    /** Backup sendo gravado agora. */
    val running: Boolean = false,
)

enum class DriveResult { OK, NO_TARGET, NEEDS_NEW_LOCATION, ERROR }

/**
 * Backup pelo seletor de arquivos do Android (Storage Access Framework): a pessoa escolhe onde salvar
 * (o Google Drive aparece no seletor quando o app do Drive está instalado) e o Evolui regrava esse mesmo
 * arquivo todo dia, em segundo plano, com o WorkManager. Sem login, sem servidor do Evolui.
 */
class BackupManager(private val app: Context, private val repository: Repository) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<DriveBackupState> = _state.asStateFlow()

    /** Guarda o arquivo escolhido no seletor (CREATE_DOCUMENT), com permissão que vale depois de reiniciar. */
    fun chooseTarget(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val taken = runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!taken) return false
        val old = _state.value.target
        if (old != null && old != uri) {
            runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        val name = displayName(uri)
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, name)
            .remove(KEY_ERROR)
            .putBoolean(KEY_NEEDS_LOCATION, false)
            .apply()
        _state.update { it.copy(target = uri, targetName = name, error = null, needsNewLocation = false) }
        return true
    }

    /** Liga ou desliga o backup diário. */
    fun setAuto(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _state.update { it.copy(auto = enabled) }
        if (enabled) schedule(ExistingPeriodicWorkPolicy.UPDATE) else WorkManager.getInstance(app).cancelUniqueWork(WORK_DAILY)
    }

    /** Ao abrir o app: garante que o backup diário continua agendado (não reinicia a contagem). */
    fun ensureScheduled() {
        if (_state.value.auto && _state.value.target != null) schedule(ExistingPeriodicWorkPolicy.KEEP)
    }

    /** "Fazer backup agora": grava em segundo plano (continua mesmo se a pessoa sair da tela). */
    fun backupNow() {
        _state.update { it.copy(running = true) }
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedule(policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(WORK_DAILY, policy, request)
    }

    /** Grava o backup completo no arquivo escolhido. Chamado pelo [BackupWorker]. */
    suspend fun runDriveBackup(): DriveResult {
        return mutex.withLock { writeToTarget() }
    }

    private suspend fun writeToTarget(): DriveResult {
        val uri = _state.value.target
        if (uri == null) {
            _state.update { it.copy(running = false) }
            return DriveResult.NO_TARGET
        }
        if (!hasWritePermission(uri)) {
            lostAccess()
            return DriveResult.NEEDS_NEW_LOCATION
        }
        _state.update { it.copy(running = true) }
        val temp = File(dir(), "drive-temp.zip")
        return try {
            // Monta o arquivo no celular primeiro: se algo falhar no meio, o backup anterior no Drive continua inteiro.
            withContext(Dispatchers.IO) {
                temp.outputStream().use { repository.exportBackupZip(it, includeFiles = true) }
                val out = openForRewrite(uri) ?: throw FileNotFoundException(uri.toString())
                out.use { o -> temp.inputStream().use { it.copyTo(o) } }
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST, now).remove(KEY_ERROR).putBoolean(KEY_NEEDS_LOCATION, false).apply()
            _state.update { it.copy(lastMillis = now, error = null, needsNewLocation = false) }
            DriveResult.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            lostAccess()
            DriveResult.NEEDS_NEW_LOCATION
        } catch (e: FileNotFoundException) {
            lostAccess()
            DriveResult.NEEDS_NEW_LOCATION
        } catch (e: Exception) {
            val msg = "Não foi possível gravar o backup. Confira a internet e o espaço no Google Drive; o Evolui tenta de novo mais tarde."
            prefs.edit().putString(KEY_ERROR, msg).apply()
            _state.update { it.copy(error = msg) }
            DriveResult.ERROR
        } finally {
            temp.delete()
            _state.update { it.copy(running = false) }
        }
    }

    /** Monta o .zip no cache do app para enviar por e-mail. Apaga os arquivos de envios anteriores. */
    suspend fun buildEmailFile(includeFiles: Boolean, date: LocalDate = LocalDate.now()): File = withContext(Dispatchers.IO) {
        val folder = dir()
        folder.listFiles()?.filter { it.name.startsWith("evolui-backup") }?.forEach { it.delete() }
        val file = File(folder, BackupRules.emailFileName(date, includeFiles))
        try {
            file.outputStream().use { repository.exportBackupZip(it, includeFiles) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        file
    }

    /** Abre a lista de apps (Gmail, outro e-mail...) com o backup anexado. */
    fun emailIntent(file: File, includeFiles: Boolean, date: LocalDate = LocalDate.now()): Intent {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = BackupRules.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, BackupRules.emailSubject(date))
            putExtra(Intent.EXTRA_TEXT, BackupRules.emailBody(includeFiles))
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Enviar backup por e-mail").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun lostAccess() {
        val msg = "O Evolui perdeu o acesso ao arquivo de backup (ele pode ter sido apagado ou movido). " +
            "Toque em \"Escolher local de novo\" e escolha o arquivo no Google Drive."
        prefs.edit().putString(KEY_ERROR, msg).putBoolean(KEY_NEEDS_LOCATION, true).apply()
        _state.update { it.copy(error = msg, needsNewLocation = true, running = false) }
    }

    private fun hasWritePermission(uri: Uri): Boolean =
        app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    /** "wt" apaga o conteúdo antigo antes de gravar; alguns provedores só aceitam "w". */
    private fun openForRewrite(uri: Uri): OutputStream? {
        val resolver = app.contentResolver
        val truncated = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return truncated ?: resolver.openOutputStream(uri, "w")
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun dir(): File = File(app.cacheDir, "backup").apply { mkdirs() }

    private fun readState(): DriveBackupState = DriveBackupState(
        target = prefs.getString(KEY_URI, null)?.let { Uri.parse(it) },
        targetName = prefs.getString(KEY_NAME, null),
        auto = prefs.getBoolean(KEY_AUTO, false),
        lastMillis = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 },
        error = prefs.getString(KEY_ERROR, null),
        needsNewLocation = prefs.getBoolean(KEY_NEEDS_LOCATION, false),
    )

    private companion object {
        const val PREFS = "backup"
        const val KEY_URI = "uri"
        const val KEY_NAME = "nome"
        const val KEY_AUTO = "automatico"
        const val KEY_LAST = "ultimo"
        const val KEY_ERROR = "erro"
        const val KEY_NEEDS_LOCATION = "escolher_de_novo"
        const val WORK_DAILY = "backup-diario"
        const val WORK_NOW = "backup-agora"
    }
}
