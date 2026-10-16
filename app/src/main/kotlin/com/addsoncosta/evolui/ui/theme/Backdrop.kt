package com.addsoncosta.evolui.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Seções do app; cada uma tem sua ilustração no fundo. */
enum class Section { TODAY, FOOD, EXERCISE, FASTING, HEALTH, PROGRESS, BADGES, SETTINGS }

/** Desenha o fundo do tema (padrão decorativo + ilustração da seção, se houver) num Canvas Android. Usado na tela e nas imagens compartilhadas. */
fun drawThemeBackdrop(
    canvas: android.graphics.Canvas,
    width: Float,
    height: Float,
    palette: Palette,
    section: Section? = null,
    artAlpha: Float = 1f,
) {
    if (width <= 0f || height <= 0f) return
    val u = min(width, height) / 360f
    canvas.save()
    canvas.clipRect(0f, 0f, width, height)
    drawBase(canvas, width, height, palette)
    drawPattern(canvas, width, height, palette, u)
    if (section != null && artAlpha > 0f) drawSectionArt(canvas, width, height, palette, section, artAlpha)
    canvas.restore()
}

/** Fundo do tema atual ocupando o espaço todo (desenha com drawThemeBackdrop). */
@Composable
fun ThemeBackdrop(section: Section?, modifier: Modifier = Modifier) {
    val palette = Evolui.palette
    var box by remember { mutableStateOf(IntSize.Zero) }
    val image = rememberThemeBackdrop(palette, section, box.width, box.height)
    val background = palette.background
    Spacer(
        modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .drawBehind {
                drawRect(background)
                if (image != null) {
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            },
    )
}

/**
 * Fundo já desenhado numa imagem, guardado em cache por (tema, seção, tamanho). O desenho é feito fora da
 * thread principal; enquanto isso continua aparecendo a imagem anterior (ou só a cor de fundo).
 * [maxSide] limita a resolução: o fundo é suave, então pode ser ampliado sem perder nada visível.
 */
@Composable
fun rememberThemeBackdrop(palette: Palette, section: Section?, width: Int, height: Int, maxSide: Int = 1440): ImageBitmap? {
    val key = "${palette.key}|${section?.name}|${width}x$height|$maxSide"
    val state = produceState<ImageBitmap?>(BackdropCache.get(key), key) {
        if (width <= 0 || height <= 0) return@produceState
        value = BackdropCache.get(key) ?: withContext(Dispatchers.Default) {
            renderBackdrop(palette, section, width, height, maxSide)
        }.also { BackdropCache.put(key, it) }
    }
    return state.value
}

private fun renderBackdrop(palette: Palette, section: Section?, width: Int, height: Int, maxSide: Int): ImageBitmap {
    val scale = min(1f, maxSide / max(width, height).toFloat())
    val w = max(1, (width * scale).roundToInt())
    val h = max(1, (height * scale).roundToInt())
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    drawThemeBackdrop(android.graphics.Canvas(bitmap), w.toFloat(), h.toFloat(), palette, section)
    bitmap.prepareToDraw()
    return bitmap.asImageBitmap()
}

/** Poucas imagens prontas na memória (cerca de 3 telas cheias e as miniaturas da escolha de tema). */
private object BackdropCache : LruCache<String, ImageBitmap>(14 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}
