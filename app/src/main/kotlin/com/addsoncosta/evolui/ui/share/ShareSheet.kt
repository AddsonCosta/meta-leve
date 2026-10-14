package com.addsoncosta.evolui.ui.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.PeriodStats
import com.addsoncosta.evolui.data.PhotoRepository
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Painel de compartilhar: escolhe o período, imagem ou texto, e manda para as redes.
 * [presetPhotos] já vem preenchido quando o usuário compartilha um antes e depois;
 * [extraText] (opcional) entra antes do resumo no texto compartilhado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    onDismiss: () -> Unit,
    initialPeriod: SharePeriod = SharePeriod.Today,
    presetPhotos: List<CaptionedPhoto> = emptyList(),
    extraText: String? = null,
) {
    val context = LocalContext.current
    val container = appContainer()
    val repo = container.repository
    val photoRepo = remember { PhotoRepository(context, container.database) }
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(initialPeriod) }
    var months by remember { mutableStateOf((initialPeriod as? SharePeriod.Months)?.count ?: 3) }
    var asImage by remember { mutableStateOf(true) }
    var shape by remember { mutableStateOf(ImageShape.STORY) }
    var extraPhoto by remember { mutableStateOf<CaptionedPhoto?>(null) }
    var stats by remember { mutableStateOf<PeriodStats?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    // Enquadramento escolhido aqui para cada foto (pela posição na lista).
    var framings by remember { mutableStateOf<Map<Int, Framing>>(emptyMap()) }
    var editing by remember { mutableStateOf<Int?>(null) }

    val photos = remember(presetPhotos, extraPhoto, framings) {
        presetPhotos.ifEmpty { listOfNotNull(extraPhoto) }.mapIndexed { i, p -> framings[i]?.let { p.withFraming(it) } ?: p }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            if (bmp != null) {
                framings = emptyMap()
                extraPhoto = CaptionedPhoto(bmp, "Eu hoje")
                editing = 0 // já abre o ajuste de enquadramento
            } else {
                Toast.makeText(context, "Não foi possível abrir a foto", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun fullText(s: PeriodStats): String = listOfNotNull(extraText, shareText(s, period)).joinToString("\n\n")

    LaunchedEffect(period) {
        val (from, to) = period.range(LocalDate.now())
        stats = repo.periodStats(from, to)
    }
    LaunchedEffect(stats, shape, photos, asImage) {
        val s = stats ?: return@LaunchedEffect
        if (asImage) preview = withContext(Dispatchers.Default) { renderShareImage(s, period, shape, photos) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Compartilhar resultados", style = MaterialTheme.typography.titleLarge)

            Overline("Período")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SharePeriod.Today to "Hoje", SharePeriod.Week to "Semana", SharePeriod.Month to "Mês").forEach { (p, label) ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(label) })
                }
                FilterChip(selected = period is SharePeriod.Months, onClick = { period = SharePeriod.Months(months) }, label = { Text("Vários meses") })
            }
            if (period is SharePeriod.Months) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { months = clampMonths(months - 1); period = SharePeriod.Months(months) }) { Icon(Icons.Filled.Remove, "Menos") }
                    Text("$months ${if (months == 1) "mês" else "meses"}", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { months = clampMonths(months + 1); period = SharePeriod.Months(months) }) { Icon(Icons.Filled.Add, "Mais") }
                }
            }

            Overline("Formato")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = asImage, onClick = { asImage = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Imagem") }
                SegmentedButton(selected = !asImage, onClick = { asImage = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Texto") }
            }

            val s = stats
            if (asImage) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageShape.entries.forEach { sh -> FilterChip(selected = shape == sh, onClick = { shape = sh }, label = { Text(sh.label) }) }
                }
                if (presetPhotos.isEmpty()) {
                    if (extraPhoto == null) {
                        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Filled.Image, null)
                            Text("  Incluir uma foto")
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editing = 0 }) {
                                Icon(Icons.Filled.Crop, null)
                                Text("  Ajustar a foto")
                            }
                            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text("Trocar")
                            }
                            OutlinedButton(onClick = { extraPhoto = null; framings = emptyMap() }) { Text("Tirar a foto") }
                        }
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEachIndexed { i, p ->
                            OutlinedButton(onClick = { editing = i }) {
                                Icon(Icons.Filled.Crop, null)
                                Text("  Ajustar ${p.title?.lowercase() ?: "foto ${i + 1}"}")
                            }
                        }
                    }
                }
                if (photos.isNotEmpty()) {
                    Text(
                        "Toque em Ajustar para escolher o melhor enquadramento: arraste e use dois dedos para aproximar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                preview?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = "Prévia da imagem",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(20.dp)),
                    )
                }
            } else if (s != null) {
                Text(
                    fullText(s),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Button(
                enabled = s != null && (!asImage || preview != null),
                onClick = {
                    val text = fullText(s!!)
                    if (asImage) shareImage(context, preview!!, text) else shareTextOnly(context, text)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Share, null)
                Text("  Compartilhar", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                enabled = s != null,
                onClick = {
                    copyText(context, fullText(s!!))
                    Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null)
                Text("  Copiar texto")
            }
        }
    }

    editing?.let { i ->
        val p = photos.getOrNull(i)
        if (p != null) {
            FrameEditorDialog(
                bitmap = p.bitmap,
                aspect = photoSlotAspect(shape, photos.size),
                initial = p.framing,
                title = if (p.title != null) "Ajustar foto: ${p.title.lowercase()}" else "Ajustar enquadramento",
                onDismiss = { editing = null },
                onSave = { f ->
                    framings = framings + (i to f)
                    editing = null
                    // Foto de progresso: guarda o enquadramento para usar de novo.
                    p.photoId?.let { id ->
                        scope.launch { photoRepo.saveFraming(id, f.scale.toDouble(), f.offsetX.toDouble(), f.offsetY.toDouble()) }
                    }
                },
            )
        }
    }
}
