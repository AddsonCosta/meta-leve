package com.addsoncosta.evolui.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.data.PhotoEntity
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enquadramento de uma foto dentro de uma moldura.
 * [scale] 1 = a foto preenche a moldura inteira (sem sobrar borda); [offsetX]/[offsetY] = deslocamento
 * do centro da foto em fração da largura/altura da moldura.
 */
data class Framing(val scale: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f) {
    val isCentered: Boolean get() = scale == 1f && offsetX == 0f && offsetY == 0f

    companion object {
        const val MAX_SCALE = 5f
    }
}

/** Onde a foto é desenhada, relativo ao canto da moldura (pode passar das bordas; o excesso é cortado). */
data class FrameRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Ajusta zoom e deslocamento para a foto nunca deixar buraco na moldura. */
fun Framing.clamped(bitmapW: Int, bitmapH: Int, frameW: Float, frameH: Float): Framing {
    if (bitmapW <= 0 || bitmapH <= 0 || frameW <= 0f || frameH <= 0f) return this
    val s = scale.coerceIn(1f, Framing.MAX_SCALE)
    val cover = max(frameW / bitmapW, frameH / bitmapH)
    val dw = bitmapW * cover * s
    val dh = bitmapH * cover * s
    val maxX = ((dw - frameW) / 2f / frameW).coerceAtLeast(0f)
    val maxY = ((dh - frameH) / 2f / frameH).coerceAtLeast(0f)
    return Framing(s, offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
}

/** Retângulo da foto para uma moldura de [frameW] x [frameH], já respeitando os limites. */
fun Framing.rectFor(bitmapW: Int, bitmapH: Int, frameW: Float, frameH: Float): FrameRect {
    val f = clamped(bitmapW, bitmapH, frameW, frameH)
    val cover = max(frameW / bitmapW.coerceAtLeast(1), frameH / bitmapH.coerceAtLeast(1))
    val dw = bitmapW * cover * f.scale
    val dh = bitmapH * cover * f.scale
    val cx = frameW / 2f + f.offsetX * frameW
    val cy = frameH / 2f + f.offsetY * frameH
    return FrameRect(cx - dw / 2f, cy - dh / 2f, dw, dh)
}

/** Enquadramento salvo numa foto de progresso. */
fun PhotoEntity.framing(): Framing =
    Framing(scale?.toFloat() ?: 1f, offsetX?.toFloat() ?: 0f, offsetY?.toFloat() ?: 0f)

/** Miniaturas e fotos já abertas, guardadas em memória para a tela não reabrir o arquivo a cada rolagem. */
object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun key(file: File, maxSide: Int) = "${file.absolutePath}|${file.lastModified()}|$maxSide"

    fun peek(file: File, maxSide: Int): Bitmap? = cache.get(key(file, maxSide))

    /** Abre a foto reduzida (fora da thread principal), usando a memória quando já foi aberta. */
    suspend fun load(context: Context, file: File, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val k = key(file, maxSide)
        cache.get(k) ?: loadBitmap(context, file, maxSide)?.also { cache.put(k, it) }
    }
}

/** Foto de um arquivo, carregada em segundo plano (nulo enquanto carrega). */
@Composable
fun rememberPhoto(file: File?, maxSide: Int = 600): State<Bitmap?> {
    val context = LocalContext.current
    return produceState(initialValue = file?.let { PhotoCache.peek(it, maxSide) }, file, maxSide) {
        value = if (file == null) null else PhotoCache.load(context, file, maxSide)
    }
}
