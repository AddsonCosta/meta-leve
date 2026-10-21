package com.addsoncosta.evolui.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Regras simples do backup (nomes, textos e limites), sem depender do Android. */
object BackupRules {
    /** O Gmail aceita até 25 MB por e-mail; acima de 24 MB oferecemos o backup sem fotos e anexos. */
    const val EMAIL_LIMIT_BYTES: Long = 24L * 1024 * 1024

    /** Nome sugerido para o arquivo do backup automático (sempre o mesmo, regravado a cada dia). */
    const val DRIVE_FILE_NAME = "evolui-backup.zip"

    const val MIME = "application/zip"

    private val PtBr = Locale.forLanguageTag("pt-BR")
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PtBr)

    fun tooBigForEmail(sizeBytes: Long): Boolean = sizeBytes > EMAIL_LIMIT_BYTES

    fun emailFileName(date: LocalDate, includeFiles: Boolean): String =
        if (includeFiles) "evolui-backup-$date.zip" else "evolui-backup-sem-fotos-$date.zip"

    fun emailSubject(date: LocalDate): String = "Backup do Evolui ${date.format(DATE)}"

    fun emailBody(includeFiles: Boolean): String = buildString {
        append("Backup dos meus dados do Evolui")
        append(if (includeFiles) " (com fotos e anexos)." else " (sem fotos e anexos).")
        append("\n\nPara restaurar: salve o arquivo .zip anexo no celular ou no Google Drive, abra o Evolui, ")
        append("vá em Configurações > Backup > Restaurar backup e escolha o arquivo.")
        if (!includeFiles) {
            append("\n\nAs fotos e os anexos não foram incluídos. Depois de restaurar, cada registro mostra qual arquivo faltou e um botão para anexar de novo.")
        }
    }

    /** "20/10/2026 às 14:05". */
    fun formatMoment(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(DATE_TIME)

    /** "3,4 MB". */
    fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 0.1) "menos de 0,1 MB" else String.format(PtBr, "%.1f MB", mb)
    }
}
