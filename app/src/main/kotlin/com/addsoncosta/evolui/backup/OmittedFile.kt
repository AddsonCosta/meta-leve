package com.addsoncosta.evolui.backup

import com.addsoncosta.evolui.data.PhotoEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * Foto ou anexo deixado de fora de um backup "sem fotos e anexos".
 * Guarda o bastante para a pessoa saber o que era e anexar de novo depois de restaurar.
 * Todos os campos têm valor padrão para o formato poder crescer sem quebrar backups antigos.
 */
@Serializable
data class OmittedFile(
    /** [KIND_PHOTO], [KIND_PDF] ou [KIND_IMAGE]. */
    val kind: String = KIND_IMAGE,
    /** Pasta interna de origem: "fotos" (progresso) ou "anexos" (Saúde). */
    val folder: String = "",
    /** Nome do arquivo na pasta interna (liga o item ao registro restaurado). */
    val fileName: String = "",
    /** Nome original do arquivo, quando conhecido. */
    val originalName: String? = null,
    /** Dia do registro (ou da foto). */
    val epochDay: Long? = null,
    /** Quando o arquivo foi anexado. */
    val addedMillis: Long? = null,
    /** Registro da Saúde dono do anexo (nulo para fotos de progresso). */
    val recordId: Long? = null,
    /** Foto de progresso dona do arquivo (nulo para anexos). */
    val photoId: Long? = null,
    /** Ex.: "Exame: Hemograma completo". */
    val recordLabel: String? = null,
    /** Páginas do PDF, quando foi possível contar. */
    val pages: Int? = null,
    /** Peso anotado na foto de progresso. */
    val weightKg: Double? = null,
    /** Quantos valores de exame já tinham sido lidos do registro. */
    val valuesRead: Int = 0,
    /** Começo do texto já lido do arquivo (o texto completo continua no registro). */
    val readText: String? = null,
    /** Tamanho do arquivo original, em bytes. */
    val sizeBytes: Long? = null,
    /** A foto de progresso inteira (data, peso, enquadramento, legenda), para voltar à lista ao adicionar a foto de novo. */
    val photo: PhotoEntity? = null,
) {
    val isPhoto: Boolean get() = kind == KIND_PHOTO

    /** Ex.: "Exame: Hemograma, 12/09/2026, laudo PDF "hemo.pdf", 2 páginas, texto lido guardado". */
    fun description(): String {
        val parts = mutableListOf<String>()
        if (isPhoto) {
            parts += "Foto de progresso"
        } else {
            recordLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
        }
        epochDay?.let { parts += LocalDate.ofEpochDay(it).format(DATE) }
        if (!isPhoto) {
            val what = if (kind == KIND_PDF) "laudo PDF" else "imagem do documento"
            parts += originalName?.takeIf { it.isNotBlank() }?.let { "$what \"$it\"" } ?: what
            pages?.takeIf { it > 0 }?.let { parts += if (it == 1) "1 página" else "$it páginas" }
        }
        weightKg?.let { parts += "${formatKg(it)} kg" }
        if (valuesRead > 0) parts += if (valuesRead == 1) "1 valor lido guardado" else "$valuesRead valores lidos guardados"
        if (!readText.isNullOrBlank()) parts += "texto lido guardado"
        return parts.joinToString(", ")
    }

    companion object {
        const val KIND_PHOTO = "foto"
        const val KIND_PDF = "pdf"
        const val KIND_IMAGE = "imagem"

        /** Limite do trecho de texto lido copiado para a descrição. */
        const val READ_TEXT_LIMIT = 500

        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

        fun kindFor(mimeType: String): String = if (mimeType == "application/pdf") KIND_PDF else KIND_IMAGE

        private fun formatKg(kg: Double): String {
            val rounded = Math.round(kg * 10) / 10.0
            return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().replace('.', ',')
        }
    }
}
