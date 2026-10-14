package com.addsoncosta.evolui.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.addsoncosta.evolui.data.HealthAttachmentEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Arquivo já copiado para a pasta do app, ainda sem registro (formulário aberto). */
data class PendingFile(val fileName: String, val mimeType: String, val originalName: String?, val ocrText: String?)

/** Anexos da seção Saúde: ficam em files/anexos, só no celular. */
object Attachments {
    /** Tipos aceitos no seletor de arquivos. */
    val pickerTypes = arrayOf("application/pdf", "image/*")

    fun dir(context: Context): File = File(context.filesDir, "anexos").apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    fun isPdf(mimeType: String) = mimeType == "application/pdf"

    /** Copia o arquivo escolhido para a pasta do app. Devolve nulo se não conseguir ler. */
    suspend fun copyIn(context: Context, uri: Uri): PendingFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val original = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val mime = resolver.getType(uri) ?: guessMime(original)
            val ext = when {
                mime == "application/pdf" -> "pdf"
                mime == "image/png" -> "png"
                mime == "image/webp" -> "webp"
                mime == "image/heic" || mime == "image/heif" -> "heic"
                mime.startsWith("image/") -> "jpg"
                else -> original?.substringAfterLast('.', "bin")?.lowercase() ?: "bin"
            }
            val name = "anexo_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext"
            val target = file(context, name)
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            PendingFile(name, mime, original, null)
        }.getOrNull()
    }

    private fun guessMime(name: String?): String = when (name?.substringAfterLast('.', "")?.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    suspend fun deleteFile(context: Context, fileName: String) = withContext(Dispatchers.IO) {
        runCatching { file(context, fileName).delete() }
    }

    private fun uri(context: Context, fileName: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file(context, fileName))

    /** Abre o arquivo original no leitor de PDF ou na galeria. */
    fun open(context: Context, a: HealthAttachmentEntity) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(context, a.fileName), a.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app para abrir este arquivo", Toast.LENGTH_LONG).show()
        }
    }

    /** Envia o arquivo original (por WhatsApp, e-mail...). */
    fun share(context: Context, a: HealthAttachmentEntity) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = a.mimeType
            putExtra(Intent.EXTRA_STREAM, uri(context, a.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Enviar arquivo").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
