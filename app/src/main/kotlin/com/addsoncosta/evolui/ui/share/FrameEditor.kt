package com.addsoncosta.evolui.ui.share

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Foto desenhada com o enquadramento escolhido, cortada no formato da moldura. */
@Composable
fun FramedImage(
    bitmap: Bitmap?,
    framing: Framing,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (image != null) {
            Canvas(Modifier.fillMaxSize()) {
                val r = framing.rectFor(image.width, image.height, size.width, size.height)
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
                    dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                )
            }
        }
    }
}

/**
 * Editor de enquadramento: arrastar move, pinça aproxima. A moldura tem exatamente a proporção
 * ([aspect] = largura / altura) em que a foto vai aparecer.
 * Com [onSaveRotated], aparece também o botão de girar; ao salvar, recebe o enquadramento e o giro
 * (0, 90, 180 ou 270 graus, no sentido horário) para gravar a foto girada.
 */
@Composable
fun FrameEditorDialog(
    bitmap: Bitmap,
    aspect: Float,
    initial: Framing,
    onDismiss: () -> Unit,
    onSave: (Framing) -> Unit,
    title: String = "Ajustar enquadramento",
    onSaveRotated: ((Framing, Int) -> Unit)? = null,
) {
    var turns by remember { mutableStateOf(0) }
    val shown = remember(bitmap, turns) { rotated(bitmap, turns * 90) }
    var framing by remember { mutableStateOf(initial) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val frameMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

    fun fit(f: Framing): Framing =
        if (box.width > 0) f.clamped(shown.width, shown.height, box.width.toFloat(), box.height.toFloat()) else f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Arraste para mover a foto e use dois dedos para aproximar ou afastar. A moldura mostra exatamente o que vai aparecer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val w = minOf(maxWidth, frameMaxHeight * aspect)
                    val h = w / aspect
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(w, h)
                            .onSizeChanged { box = it }
                            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                            .pointerInput(shown) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val fw = size.width.toFloat()
                                    val fh = size.height.toFloat()
                                    framing = framing.copy(
                                        scale = framing.scale * zoom,
                                        offsetX = framing.offsetX + pan.x / fw,
                                        offsetY = framing.offsetY + pan.y / fh,
                                    ).clamped(shown.width, shown.height, fw, fh)
                                }
                            },
                    ) {
                        FramedImage(shown, framing, Modifier.fillMaxSize(), shape)
                        // Linhas de terço, para ajudar a centralizar o rosto e o corpo.
                        Canvas(Modifier.fillMaxSize()) {
                            for (i in 1..2) {
                                val x = size.width * i / 3f
                                val y = size.height * i / 3f
                                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { framing = fit(framing.copy(scale = framing.scale / 1.2f)) }) {
                        Icon(Icons.Filled.ZoomOut, "Afastar")
                    }
                    OutlinedButton(onClick = { framing = Framing() }) {
                        Icon(Icons.Filled.CenterFocusStrong, null)
                        Text("  Centralizar")
                    }
                    if (onSaveRotated != null) {
                        IconButton(onClick = {
                            turns = (turns + 1) % 4
                            framing = Framing()
                        }) {
                            Icon(Icons.Filled.Rotate90DegreesCw, "Girar")
                        }
                    }
                    IconButton(onClick = { framing = fit(framing.copy(scale = framing.scale * 1.2f)) }) {
                        Icon(Icons.Filled.ZoomIn, "Aproximar")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        val f = fit(framing)
                        if (onSaveRotated != null) onSaveRotated(f, turns * 90) else onSave(f)
                    }) { Text("Salvar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** A foto girada em [degrees] no sentido horário (a mesma, sem cópia, quando é 0). */
internal fun rotated(bitmap: Bitmap, degrees: Int): Bitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(d.toFloat()) }, true)
}
