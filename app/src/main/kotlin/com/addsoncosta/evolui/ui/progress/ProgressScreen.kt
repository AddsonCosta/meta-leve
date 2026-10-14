package com.addsoncosta.evolui.ui.progress

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.data.PhotoEntity
import com.addsoncosta.evolui.data.PhotoRepository
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.DayFormat
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.Ring
import com.addsoncosta.evolui.ui.components.ShortDateFormat
import com.addsoncosta.evolui.ui.components.Stat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.history.HistoryViewModel
import com.addsoncosta.evolui.ui.share.CaptionedPhoto
import com.addsoncosta.evolui.ui.share.FrameEditorDialog
import com.addsoncosta.evolui.ui.share.FramedImage
import com.addsoncosta.evolui.ui.share.PhotoCache
import com.addsoncosta.evolui.ui.share.SharePeriod
import com.addsoncosta.evolui.ui.share.ShareSheet
import com.addsoncosta.evolui.ui.share.framing
import com.addsoncosta.evolui.ui.share.rememberPhoto
import com.addsoncosta.evolui.ui.theme.Evolui
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Proporção das janelas de antes e depois (largura / altura). */
private const val WINDOW_ASPECT = 0.75f

private enum class Slot(val title: String) { BEFORE("Antes"), AFTER("Depois") }

/** O que fazer com a foto escolhida na galeria. */
private sealed interface PickTarget {
    data object AddNew : PickTarget
    data class Fill(val slot: Slot) : PickTarget
    data class Replace(val photo: PhotoEntity) : PickTarget
}

/** Ações do menu de cada janela. */
private enum class WindowAction { SWAP, REPLACE, FRAME, EDIT, DELETE }

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoRepo = remember { PhotoRepository(context, container.database) }
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val photos by remember { repo.photos() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val pair by photoRepo.pair.collectAsStateWithLifecycle()

    var sharing by remember { mutableStateOf(false) }
    var shareCompare by remember { mutableStateOf<List<CaptionedPhoto>?>(null) }
    var compareText by remember { mutableStateOf<String?>(null) }
    var comparePeriod by remember { mutableStateOf<SharePeriod>(SharePeriod.Month) }
    var choosing by remember { mutableStateOf<Slot?>(null) }
    var viewing by remember { mutableStateOf<PhotoEntity?>(null) }
    var deleting by remember { mutableStateOf<PhotoEntity?>(null) }
    var editing by remember { mutableStateOf<PhotoEntity?>(null) }
    var framingPhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    var pickTarget by remember { mutableStateOf<PickTarget>(PickTarget.AddNew) }

    val s = state
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = pickTarget
        scope.launch {
            runCatching {
                when (target) {
                    PickTarget.AddNew -> photoRepo.add(uri, LocalDate.now(), s?.currentWeight)
                    is PickTarget.Fill -> {
                        val id = photoRepo.add(uri, LocalDate.now(), s?.currentWeight)
                        if (target.slot == Slot.BEFORE) photoRepo.setBefore(id) else photoRepo.setAfter(id)
                    }
                    is PickTarget.Replace -> photoRepo.replace(target.photo, uri)
                }
            }.onFailure { Toast.makeText(context, "Não foi possível abrir a foto", Toast.LENGTH_SHORT).show() }
        }
    }
    fun pick(target: PickTarget) {
        pickTarget = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val before = photos.firstOrNull { it.id == pair.first } ?: photos.firstOrNull()
    val after = photos.firstOrNull { it.id == pair.second && it.id != before?.id }
        ?: photos.lastOrNull()?.takeIf { it.id != before?.id }

    fun onAction(slot: Slot, photo: PhotoEntity?, action: WindowAction) {
        when (action) {
            WindowAction.SWAP -> choosing = slot
            WindowAction.REPLACE -> pick(if (photo != null) PickTarget.Replace(photo) else PickTarget.Fill(slot))
            WindowAction.FRAME -> framingPhoto = photo
            WindowAction.EDIT -> editing = photo
            WindowAction.DELETE -> deleting = photo
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) {
                    Overline("Sua evolução")
                    Text("Progresso", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, "Compartilhar") }
            }
        }

        // Peso e meta.
        item {
            val p = s.profile
            val totalToGo = abs(p.targetWeightKg - p.startWeightKg)
            val done = if (p.goalType == GoalType.KEEP || totalToGo < 0.01) 1f
            else ((p.startWeightKg - s.currentWeight) * (if (p.goalType == GoalType.LOSE) 1 else -1) / totalToGo).toFloat().coerceIn(0f, 1f)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Ring(done, Evolui.Neon, size = 132.dp, stroke = 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(done * 100).toDouble().fmt()}%", style = MaterialTheme.typography.titleLarge)
                            Text("da meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.currentWeight.fmt(1)} kg", style = MaterialTheme.typography.headlineMedium)
                        Text("${signedKg(s.changeKg)} kg desde ${p.startDate.format(ShortDateFormat)}", color = Evolui.Neon, fontWeight = FontWeight.Bold)
                        if (p.goalType != GoalType.KEEP) {
                            Text("Meta: ${p.targetWeightKg.fmt(1)} kg · faltam ${abs(s.currentWeight - p.targetWeightKg).fmt(1)} kg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (s.weights.size >= 2) WeightChart(s.weights, p.targetWeightKg)
                else Text("Registre o peso em pelo menos dois dias (na tela Hoje) para ver o gráfico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Antes e depois: duas janelas editáveis.
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Antes e depois", style = MaterialTheme.typography.titleMedium)
                        Text("As fotos ficam só no seu celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { pick(PickTarget.AddNew) }) {
                        Icon(Icons.Filled.AddAPhoto, null)
                        Text("  Foto")
                    }
                }
                if (photos.isEmpty()) {
                    Text(
                        "Adicione uma foto hoje e outra daqui a algumas semanas. O Evolui mostra as duas lado a lado, com a data e o peso de cada uma. Você pode ajustar o enquadramento e editar o texto.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompareWindow(Slot.BEFORE, before, photoRepo, hasPhotos = photos.isNotEmpty(), Modifier.weight(1f)) { a -> onAction(Slot.BEFORE, before, a) }
                    CompareWindow(Slot.AFTER, after, photoRepo, hasPhotos = photos.isNotEmpty(), Modifier.weight(1f)) { a -> onAction(Slot.AFTER, after, a) }
                }
                Text(
                    "Toque numa janela para trocar, substituir, ajustar o enquadramento, editar o texto ou apagar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (before != null && after != null) {
                    Text(differenceText(before, after), fontWeight = FontWeight.Bold, color = Evolui.Neon)
                    Button(onClick = {
                        scope.launch {
                            val list = listOf(Slot.BEFORE to before, Slot.AFTER to after).mapNotNull { (slot, ph) ->
                                PhotoCache.load(context, photoRepo.file(ph), 1600)?.let {
                                    CaptionedPhoto(it, captionOf(ph), ph.framing(), slot.title.uppercase(), ph.id)
                                }
                            }
                            if (list.size == 2) {
                                val days = daysBetween(before, after)
                                comparePeriod = when {
                                    days <= 7 -> SharePeriod.Week
                                    days <= 30 -> SharePeriod.Month
                                    else -> SharePeriod.Months(((days + 29) / 30).toInt().coerceIn(1, 24))
                                }
                                compareText = pairText(before, after)
                                shareCompare = list
                            } else {
                                Toast.makeText(context, "Não foi possível abrir as fotos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, null)
                        Text("  Compartilhar antes e depois")
                    }
                }
                if (photos.isNotEmpty()) {
                    Text("Todas as fotos (${photos.size})", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { ph ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Thumb(photoRepo.file(ph), Modifier.size(84.dp).clickable { viewing = ph })
                                Text(LocalDate.ofEpochDay(ph.epochDay).format(ShortDateFormat), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Resumo e dias.
        item {
            Panel {
                Text("Resumo da dieta", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(s.avgEaten.fmt(), "kcal/dia")
                    Stat(s.totalExercise.fmt(), "kcal treino", color = Evolui.Orange)
                    Stat("${s.days.size}", "dias")
                }
            }
        }
        item { Overline("Dias registrados") }
        if (s.days.isEmpty()) item { Text("Ainda não há registros. Anote refeições, treinos e peso na tela Hoje.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(s.days, key = { it.date.toEpochDay() }) { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(d.date.format(DayFormat), fontWeight = FontWeight.Bold)
                    d.weightKg?.let { Text("${it.fmt(1)} kg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${d.eaten.fmt()} kcal")
                    if (d.exercise > 0) Text("−${d.exercise.fmt()} kcal treino", style = MaterialTheme.typography.bodySmall, color = Evolui.Orange)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }

    if (sharing) ShareSheet(onDismiss = { sharing = false }, initialPeriod = SharePeriod.Month)
    shareCompare?.let {
        ShareSheet(
            onDismiss = { shareCompare = null },
            initialPeriod = comparePeriod,
            presetPhotos = it,
            extraText = compareText,
        )
    }

    choosing?.let { slot ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Foto de ${slot.title.lowercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (photos.isEmpty()) Text("Nenhuma foto salva ainda.")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { ph ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Thumb(photoRepo.file(ph), Modifier.size(96.dp).clickable {
                                    if (slot == Slot.BEFORE) photoRepo.setBefore(ph.id) else photoRepo.setAfter(ph.id)
                                    choosing = null
                                })
                                Text(LocalDate.ofEpochDay(ph.epochDay).format(ShortDateFormat), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    OutlinedButton(onClick = { choosing = null; pick(PickTarget.Fill(slot)) }) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Text("  Pegar da galeria")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("Fechar") } },
        )
    }

    viewing?.let { ph ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(captionOf(ph)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bmp by rememberPhoto(photoRepo.file(ph), 900)
                    FramedImage(bmp, ph.framing(), Modifier.fillMaxWidth().aspectRatio(WINDOW_ASPECT))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { photoRepo.setBefore(ph.id); viewing = null }, modifier = Modifier.weight(1f)) { Text("Usar no Antes") }
                        OutlinedButton(onClick = { photoRepo.setAfter(ph.id); viewing = null }, modifier = Modifier.weight(1f)) { Text("Usar no Depois") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = ph; viewing = null }) {
                            Icon(Icons.Filled.Edit, null)
                            Text(" Editar")
                        }
                        TextButton(onClick = { framingPhoto = ph; viewing = null }) {
                            Icon(Icons.Filled.Crop, null)
                            Text(" Ajustar")
                        }
                        TextButton(onClick = { deleting = ph; viewing = null }) {
                            Icon(Icons.Filled.Delete, null)
                            Text(" Apagar")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Fechar") } },
        )
    }

    deleting?.let { ph ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Apagar esta foto?") },
            text = { Text("A foto de ${LocalDate.ofEpochDay(ph.epochDay).format(DateFormat)} será apagada do celular. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { photoRepo.delete(ph) }; deleting = null }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }

    editing?.let { ph ->
        EditPhotoDialog(
            photo = ph,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { photoRepo.update(updated) }
            },
        )
    }

    framingPhoto?.let { ph ->
        val big by rememberPhoto(photoRepo.file(ph), 1600)
        val bmp = big
        if (bmp != null) {
            FrameEditorDialog(
                bitmap = bmp,
                aspect = WINDOW_ASPECT,
                initial = ph.framing(),
                title = "Ajustar enquadramento",
                onDismiss = { framingPhoto = null },
                onSave = { f ->
                    framingPhoto = null
                    scope.launch { photoRepo.saveFraming(ph.id, f.scale.toDouble(), f.offsetX.toDouble(), f.offsetY.toDouble()) }
                },
            )
        }
    }
}

/** Uma janela do antes e depois: título pequeno em cima, a foto enquadrada e a legenda embaixo. */
@Composable
private fun CompareWindow(
    slot: Slot,
    photo: PhotoEntity?,
    photoRepo: PhotoRepository,
    hasPhotos: Boolean,
    modifier: Modifier,
    onAction: (WindowAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(
            slot.title,
            color = if (slot == Slot.AFTER) Evolui.Neon else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(WINDOW_ASPECT)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                val bmp by rememberPhoto(photoRepo.file(photo), 900)
                FramedImage(bmp, photo.framing(), Modifier.fillMaxSize(), shape)
                // Botão discreto no canto, para mostrar que a janela é editável.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreVert, "Opções", modifier = Modifier.size(20.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Filled.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Toque para\nescolher", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fun pick(action: WindowAction) {
                    menu = false
                    onAction(action)
                }
                if (hasPhotos) {
                    DropdownMenuItem(
                        text = { Text(if (photo != null) "Trocar por outra foto" else "Escolher das fotos salvas") },
                        leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                        onClick = { pick(WindowAction.SWAP) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (photo != null) "Substituir pela galeria" else "Pegar da galeria") },
                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                    onClick = { pick(WindowAction.REPLACE) },
                )
                if (photo != null) {
                    DropdownMenuItem(
                        text = { Text("Ajustar enquadramento") },
                        leadingIcon = { Icon(Icons.Filled.Crop, null) },
                        onClick = { pick(WindowAction.FRAME) },
                    )
                    DropdownMenuItem(
                        text = { Text("Editar texto, data e peso") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { pick(WindowAction.EDIT) },
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar foto") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { pick(WindowAction.DELETE) },
                    )
                }
            }
        }
        if (photo != null) {
            Text(
                captionOf(photo),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAction(WindowAction.EDIT) }.padding(start = 4.dp),
            )
        }
    }
}

/** Miniatura carregada fora da thread principal (com cache em memória). */
@Composable
private fun Thumb(file: File, modifier: Modifier, maxSide: Int = 300) {
    val bmp by rememberPhoto(file, maxSide)
    val image = remember(bmp) { bmp?.asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun signedKg(kg: Double): String = (if (kg > 0.04) "+" else if (kg < -0.04) "−" else "") + abs(kg).fmt(1)

private fun daysBetween(before: PhotoEntity, after: PhotoEntity): Long =
    abs(ChronoUnit.DAYS.between(LocalDate.ofEpochDay(before.epochDay), LocalDate.ofEpochDay(after.epochDay)))

private fun differenceText(before: PhotoEntity, after: PhotoEntity): String {
    val days = daysBetween(before, after)
    val diff = if (before.weightKg != null && after.weightKg != null) after.weightKg - before.weightKg else null
    return "$days ${if (days == 1L) "dia" else "dias"} de diferença" + (diff?.let { " · ${signedKg(it)} kg" } ?: "")
}

/** Texto do par para compartilhar. */
private fun pairText(before: PhotoEntity, after: PhotoEntity): String = buildString {
    appendLine("📸 Meu antes e depois no Evolui")
    appendLine("Antes: ${captionOf(before)}")
    appendLine("Depois: ${captionOf(after)}")
    append("⏳ ${differenceText(before, after)}")
}

/** Linha do peso ao longo do tempo, com a meta tracejada. */
@Composable
private fun WeightChart(points: List<Pair<LocalDate, Double>>, targetKg: Double) {
    val line = Evolui.Neon
    val goal = Evolui.Cyan
    val minDay = points.first().first.toEpochDay()
    val maxDay = points.last().first.toEpochDay().coerceAtLeast(minDay + 1)
    val minW = minOf(points.minOf { it.second }, targetKg) - 1
    val maxW = maxOf(points.maxOf { it.second }, targetKg) + 1
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp)) {
        fun x(day: Long) = (day - minDay).toFloat() / (maxDay - minDay) * size.width
        fun y(w: Double) = ((maxW - w) / (maxW - minW)).toFloat() * size.height
        drawLine(goal, Offset(0f, y(targetKg)), Offset(size.width, y(targetKg)), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
        val path = Path()
        points.forEachIndexed { i, (d, w) ->
            val p = Offset(x(d.toEpochDay()), y(w))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, line, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        points.lastOrNull()?.let { (d, w) -> drawCircle(line, 6.dp.toPx(), Offset(x(d.toEpochDay()), y(w))) }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().first.format(DateFormat), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(points.last().first.format(DateFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
