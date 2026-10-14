package com.addsoncosta.evolui.ui.progress

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.data.PhotoEntity
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
import com.addsoncosta.evolui.ui.share.SharePeriod
import com.addsoncosta.evolui.ui.share.ShareSheet
import com.addsoncosta.evolui.ui.share.loadBitmap
import com.addsoncosta.evolui.ui.theme.Evolui
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProgressScreen(onOpenProfile: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val photos by remember { repo.photos() }.collectAsState(initial = emptyList())
    val s = state ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

    var sharing by remember { mutableStateOf(false) }
    var shareCompare by remember { mutableStateOf<List<CaptionedPhoto>?>(null) }
    var beforeId by remember { mutableStateOf<Long?>(null) }
    var afterId by remember { mutableStateOf<Long?>(null) }
    var choosing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PhotoEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { repo.addPhoto(uri, LocalDate.now(), s.currentWeight) }
    }
    val before = photos.firstOrNull { it.id == beforeId } ?: photos.firstOrNull()
    val after = photos.firstOrNull { it.id == afterId } ?: photos.lastOrNull()?.takeIf { it.id != before?.id }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Sua evolução")
                    Text("Progresso", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, "Compartilhar") }
                IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Settings, "Perfil e ajustes") }
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
                        val sign = if (s.changeKg > 0) "+" else if (s.changeKg < 0) "−" else ""
                        Text("$sign${abs(s.changeKg).fmt(1)} kg desde ${p.startDate.format(ShortDateFormat)}", color = Evolui.Neon, fontWeight = FontWeight.Bold)
                        if (p.goalType != GoalType.KEEP) {
                            Text("Meta: ${p.targetWeightKg.fmt(1)} kg · faltam ${abs(s.currentWeight - p.targetWeightKg).fmt(1)} kg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (s.weights.size >= 2) WeightChart(s.weights, p.targetWeightKg)
                else Text("Registre o peso em pelo menos dois dias (na tela Hoje) para ver o gráfico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Antes e depois.
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Antes e depois", style = MaterialTheme.typography.titleMedium)
                        Text("As fotos ficam só no seu celular.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.AddAPhoto, null)
                        Text("  Foto")
                    }
                }
                if (photos.isEmpty()) {
                    Text(
                        "Adicione uma foto hoje e outra daqui a algumas semanas. O Evolui mostra as duas lado a lado, com a data e o peso de cada uma.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompareSlot("Antes", before, Modifier.weight(1f)) { choosing = "antes" }
                        CompareSlot("Depois", after, Modifier.weight(1f)) { choosing = "depois" }
                    }
                    if (before != null && after != null) {
                        val days = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(before.epochDay), LocalDate.ofEpochDay(after.epochDay))
                        val diff = if (before.weightKg != null && after.weightKg != null) after.weightKg - before.weightKg else null
                        Text(
                            "$days dias de diferença" + (diff?.let { " · ${if (it > 0) "+" else if (it < 0) "−" else ""}${abs(it).fmt(1)} kg" } ?: ""),
                            fontWeight = FontWeight.Bold,
                            color = Evolui.Neon,
                        )
                        Button(onClick = {
                            scope.launch {
                                val pair = withContext(Dispatchers.IO) {
                                    listOf(before, after).mapNotNull { ph ->
                                        loadBitmap(context, repo.photoFile(ph))?.let { CaptionedPhoto(it, caption(ph)) }
                                    }
                                }
                                if (pair.size == 2) shareCompare = pair
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Share, null)
                            Text("  Compartilhar antes e depois")
                        }
                    }
                    Text("Todas as fotos", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { ph ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Thumb(repo.photoFile(ph), Modifier.size(84.dp).clickable { deleting = ph })
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
        if (s.days.isEmpty()) item { Text("Ainda não há registros.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    shareCompare?.let { ShareSheet(onDismiss = { shareCompare = null }, initialPeriod = SharePeriod.Month, presetPhotos = it) }

    choosing?.let { which ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Escolha a foto de $which") },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { ph ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Thumb(repo.photoFile(ph), Modifier.size(96.dp).clickable {
                                if (which == "antes") beforeId = ph.id else afterId = ph.id
                                choosing = null
                            })
                            Text(LocalDate.ofEpochDay(ph.epochDay).format(ShortDateFormat), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("Fechar") } },
        )
    }
    deleting?.let { ph ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Foto de ${LocalDate.ofEpochDay(ph.epochDay).format(DateFormat)}") },
            text = { Thumb(repo.photoFile(ph), Modifier.fillMaxWidth().aspectRatio(0.75f)) },
            confirmButton = { TextButton(onClick = { deleting = null }) { Text("Fechar") } },
            dismissButton = {
                TextButton(onClick = { scope.launch { repo.deletePhoto(ph) }; deleting = null }) {
                    Icon(Icons.Filled.Delete, null)
                    Text("Apagar")
                }
            },
        )
    }
}

private fun caption(ph: PhotoEntity): String =
    LocalDate.ofEpochDay(ph.epochDay).format(ShortDateFormat) + (ph.weightKg?.let { " · ${it.fmt(1)} kg" } ?: "")

@Composable
private fun CompareSlot(label: String, photo: PhotoEntity?, modifier: Modifier, onClick: () -> Unit) {
    val repo = appContainer().repository
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) Thumb(repo.photoFile(photo), Modifier.fillMaxSize(), maxSide = 900)
            else Text("Toque para\nescolher", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label.uppercase(),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xCC0B0F0E), Color(0xCC0B0F0E))), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Evolui.Neon,
            )
        }
        if (photo != null) Text(caption(photo), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

/** Miniatura carregada fora da thread principal. */
@Composable
private fun Thumb(file: File, modifier: Modifier, maxSide: Int = 400) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { loadBitmap(context, file, maxSide) } }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
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
