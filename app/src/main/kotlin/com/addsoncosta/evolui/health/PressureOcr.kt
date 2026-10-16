package com.addsoncosta.evolui.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import com.addsoncosta.evolui.ui.share.loadBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** O que a foto do aparelho (ou da anotação) rendeu. [text] é o que foi lido, para o usuário conferir. */
data class PressurePhotoResult(val text: String?, val candidates: List<PressureGuess>)

/**
 * Lê a foto do medidor de pressão no próprio celular (ML Kit) e procura os valores.
 * Fotos do celular às vezes chegam deitadas; se nada aparecer, tenta girar a imagem.
 * Visores de 7 segmentos são lidos com dificuldade: o resultado sempre vai para conferência.
 */
object PressureOcr {
    private const val MAX_SIDE = 2000

    suspend fun read(context: Context, uri: Uri): PressurePhotoResult = withContext(Dispatchers.Default) {
        val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull()
            ?: return@withContext PressurePhotoResult(null, emptyList())
        try {
            val original = withContext(Dispatchers.IO) { loadBitmap(context, uri, MAX_SIDE) }
                ?: return@withContext PressurePhotoResult(null, emptyList())
            var firstText: String? = null
            for (degrees in listOf(0, 90, 270, 180)) {
                val bmp = if (degrees == 0) original else rotate(original, degrees) ?: continue
                val text = runCatching { recognize(recognizer, bmp) }.getOrNull()
                if (bmp !== original) bmp.recycle()
                if (text.isNullOrBlank()) continue
                if (firstText == null) firstText = text
                val found = PressureParser.candidates(text)
                if (found.isNotEmpty()) return@withContext PressurePhotoResult(text, found)
            }
            PressurePhotoResult(firstText, emptyList())
        } catch (e: Exception) {
            PressurePhotoResult(null, emptyList())
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap? = runCatching {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }.getOrNull()

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return rows(result).ifBlank { result.text }.ifBlank { null }
    }

    /**
     * Linhas de cima para baixo, juntando as que estão na mesma altura (rótulo "SYS" ao lado do número):
     * no visor a sistólica vem antes da diastólica e do pulso.
     */
    private fun rows(result: Text): String {
        class L(val text: String, val left: Int, val cy: Int, val h: Int)
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                L(line.text, box.left, box.centerY(), box.height().coerceAtLeast(1))
            }
        }.sortedBy { it.cy }
        if (lines.isEmpty()) return result.text
        val groups = mutableListOf<MutableList<L>>()
        for (l in lines) {
            val g = groups.lastOrNull()
            val first = g?.first()
            if (g != null && first != null && abs(first.cy - l.cy).toDouble() < maxOf(first.h, l.h) * 0.5) g.add(l) else groups.add(mutableListOf(l))
        }
        return groups.joinToString("\n") { g -> g.sortedBy { it.left }.joinToString("   ") { it.text } }
    }
}
