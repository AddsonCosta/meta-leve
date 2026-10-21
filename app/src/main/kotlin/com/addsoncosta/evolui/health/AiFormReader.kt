package com.addsoncosta.evolui.health

import android.content.Context
import android.graphics.Bitmap
import com.addsoncosta.evolui.ai.AiDocumentReader
import com.addsoncosta.evolui.data.HealthType
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Um arquivo anexado, para mandar à IA: o arquivo no app, o tipo e o texto já lido no celular. */
data class AiSource(val file: File, val mimeType: String, val ocrText: String?)

/**
 * "Ler com minha IA": manda as páginas dos anexos e o texto do OCR à IA conectada pelo usuário,
 * com a lista de campos do formulário daquela área, e devolve o que ela achou.
 */
object AiFormReader {
    /** Máximo de imagens por pedido (para não ficar caro nem lento). */
    private const val MAX_IMAGES = 4

    fun isConnected(context: Context): Boolean = runCatching { AiDocumentReader(context).isConnected() }.getOrDefault(false)

    suspend fun read(context: Context, type: HealthType, reportExam: Boolean, sources: List<AiSource>): Result<AutoFill> {
        if (sources.isEmpty()) return Result.failure(IllegalStateException("Anexe um arquivo primeiro."))
        val images = mutableListOf<Bitmap>()
        for (s in sources) {
            if (images.size >= MAX_IMAGES) break
            images += Ocr.pageImages(context, s.file, s.mimeType, maxPages = MAX_IMAGES - images.size)
        }
        val ocr = sources.mapNotNull { it.ocrText?.takeIf { t -> t.isNotBlank() } }.joinToString("\n\n").ifBlank { null }
        if (images.isEmpty() && ocr == null) return Result.failure(IllegalStateException("Não consegui abrir o arquivo para enviar."))
        val result = AiDocumentReader(context).readJson(images, ocr, AiFormPrompt.instructions(type, reportExam))
        images.forEach { runCatching { it.recycle() } }
        return result.map { obj -> toAutoFill(type, reportExam, obj) }
    }

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.mapNotNull { text(it) }.joinToString(", ").ifBlank { null }
        else -> null
    }

    fun toAutoFill(type: HealthType, reportExam: Boolean, obj: JsonObject): AutoFill {
        val fields = obj.entries.mapNotNull { (k, v) -> if (k == AiFormPrompt.KEY_MEDICINES) null else text(v)?.let { k to it } }.toMap()
        val meds = (obj[AiFormPrompt.KEY_MEDICINES] as? JsonArray).orEmpty().mapNotNull { item ->
            (item as? JsonObject)?.entries?.mapNotNull { (k, v) -> text(v)?.let { k to it } }?.toMap()
        }
        return AiFormPrompt.toAutoFill(type, fields, meds, reportExam)
    }
}
