package com.addsoncosta.evolui.ui.health.pressure

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.BloodPressureEntity
import com.addsoncosta.evolui.health.BpCategory
import com.addsoncosta.evolui.health.PressureGuess
import com.addsoncosta.evolui.health.PressureOcr
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.health.BackHeader
import com.addsoncosta.evolui.ui.health.EmptyNote
import com.addsoncosta.evolui.ui.share.copyText
import com.addsoncosta.evolui.ui.share.shareTextOnly
import com.addsoncosta.evolui.ui.theme.Evolui
import java.io.File
import kotlinx.coroutines.launch

/** O que vai aberto no formulário: a medida (id 0 = nova) e, se veio de foto, o que foi lido. */
private class FormRequest(
    val reading: BloodPressureEntity,
    val fromPhoto: Boolean = false,
    val guesses: List<PressureGuess> = emptyList(),
    val ocrText: String? = null,
)

/** Períodos do gráfico, em dias (0 = tudo). */
private val ChartPeriods = listOf(7 to "7 dias", 30 to "30 dias", 90 to "90 dias", 0 to "Tudo")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PressureScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val dao = container.database.bloodPressureDao()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readings by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())

    var form by remember { mutableStateOf<FormRequest?>(null) }
    var reading by remember { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(30) }
    var shareMenu by remember { mutableStateOf(false) }

    val latest = readings.firstOrNull() // do mais recente para o mais antigo

    /** Medida nova com data de agora e o braço e a posição da última vez. */
    fun blank(guess: PressureGuess? = null) = BloodPressureEntity(
        epochMillis = System.currentTimeMillis(),
        systolic = guess?.systolic ?: 0,
        diastolic = guess?.diastolic ?: 0,
        pulse = guess?.pulse,
        arm = latest?.arm,
        position = latest?.position,
    )

    fun readPhoto(uri: Uri) {
        if (reading) return
        reading = true
        scope.launch {
            try {
                val result = PressureOcr.read(context, uri)
                if (result.candidates.isEmpty()) {
                    Toast.makeText(
                        context,
                        if (result.text == null) "Não deu para ler a foto neste celular. Digite os valores olhando o visor."
                        else "Não encontramos os números da pressão na foto. Digite os valores olhando o visor.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                form = FormRequest(blank(result.candidates.firstOrNull()), fromPhoto = true, guesses = result.candidates, ocrText = result.text)
            } finally {
                reading = false
            }
        }
    }

    val cameraFile = remember { File(File(context.cacheDir, "share"), "pressao-foto.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) readPhoto(Uri.fromFile(cameraFile))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readPhoto(uri)
    }
    fun openCamera() {
        try {
            cameraFile.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile)
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app de câmera encontrado. Escolha uma foto da galeria.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Não foi possível abrir a câmera.", Toast.LENGTH_LONG).show()
        }
    }

    val week = remember(readings) { averageOf(readings.lastDays(7)) }
    val month = remember(readings) { averageOf(readings.lastDays(30)) }
    val chartPoints = remember(readings, period) {
        (if (period == 0) readings else readings.lastDays(period)).sortedBy { it.epochMillis }
    }
    val sysColor = MaterialTheme.colorScheme.primary
    val diaColor = Evolui.Orange

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            BackHeader("Saúde", "Pressão arterial", onBack) {
                Box {
                    IconButton(onClick = { shareMenu = true }) { Icon(Icons.Filled.Share, "Compartilhar medidas") }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Compartilhar como texto") },
                            onClick = {
                                shareMenu = false
                                shareTextOnly(context, pressureShareText(readings))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copiar texto") },
                            onClick = {
                                shareMenu = false
                                copyText(context, pressureShareText(readings))
                            },
                        )
                    }
                }
            }
        }

        // Última medida e botões para registrar.
        item(key = "acao") {
            Panel {
                if (latest == null) {
                    Text("Registre sua pressão", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Digite os números do aparelho ou tire uma foto do visor (ou de uma anotação) que o app tenta ler para você.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Overline("Última medida")
                    ReadingSummary(latest)
                }
                Button(onClick = { form = FormRequest(blank()) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Text("  Nova medida", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openCamera() }, enabled = !reading, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                        Text(" Tirar foto", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !reading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                        Text(" Da galeria", maxLines = 1)
                    }
                }
                if (reading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text("Lendo a foto...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (readings.isNotEmpty()) {
            // Médias.
            item(key = "medias") {
                Panel {
                    Text("Médias", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AverageBox("Últimos 7 dias", week, Modifier.weight(1f))
                        AverageBox("Últimos 30 dias", month, Modifier.weight(1f))
                    }
                    Text(
                        "Para a média valer mais, meça sempre do mesmo jeito: sentado, após 5 minutos de descanso, com o braço apoiado na altura do coração.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Gráfico.
            item(key = "grafico") {
                Panel {
                    Text("Evolução", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartPeriods.forEach { (days, label) ->
                            FilterChip(selected = period == days, onClick = { period = days }, label = { Text(label) })
                        }
                    }
                    if (chartPoints.isEmpty()) {
                        Text("Nenhuma medida neste período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PressureChart(chartPoints, sysColor, diaColor, Modifier.fillMaxWidth().height(220.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Legend(sysColor, "Sistólica (máxima)")
                            Legend(diaColor, "Diastólica (mínima)")
                            Legend(Evolui.Red.copy(alpha = 0.6f), "140/90: limite")
                        }
                    }
                }
            }
        }

        // Tabela da diretriz.
        item(key = "tabela") { ClassificationTable() }

        item(key = "hist") { Overline("Histórico") }
        if (readings.isEmpty()) {
            item(key = "vazio") { EmptyNote("Nenhuma medida ainda", "As medidas que você registrar aparecem aqui, da mais recente para a mais antiga.") }
        }
        items(readings, key = { it.id }) { r ->
            Panel(Modifier.clickable { form = FormRequest(r) }) { ReadingSummary(r) }
        }

        item(key = "aviso") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(BP_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    form?.let { req ->
        PressureForm(
            initial = req.reading,
            fromPhoto = req.fromPhoto,
            guesses = req.guesses,
            ocrText = req.ocrText,
            onDismiss = { form = null },
            onSave = { r ->
                form = null
                container.scope.launch { dao.save(r) }
            },
            onDelete = { r ->
                form = null
                container.scope.launch { dao.delete(r) }
            },
        )
    }
}

/** Valores, classificação, data e detalhes de uma medida. */
@Composable
private fun ReadingSummary(r: BloodPressureEntity) {
    val cat = r.category
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(cat.color()))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.pairText(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(" mmHg", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                r.pulse?.let { Text("   pulso $it", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
            }
            Text(cat.label, color = cat.color(), fontWeight = FontWeight.Bold)
            Text(
                whenText(r.epochMillis) + (r.detailText()?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            r.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AverageBox(title: String, avg: BpAverage?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (avg == null) {
            Text("—", style = MaterialTheme.typography.titleLarge)
            Text("sem medidas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("${avg.systolic}/${avg.diastolic}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(avg.category.short, color = avg.category.color(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(
                "${avg.count} ${if (avg.count == 1) "medida" else "medidas"}" + (avg.pulse?.let { " · pulso $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Faixas da Diretriz Brasileira de Hipertensão 2020 (medida no consultório). */
@Composable
private fun ClassificationTable() {
    val rows = listOf(
        BpCategory.OTIMA to "menor que 120 e menor que 80",
        BpCategory.NORMAL to "120 a 129 e/ou 80 a 84",
        BpCategory.PRE_HIPERTENSAO to "130 a 139 e/ou 85 a 89",
        BpCategory.ESTAGIO_1 to "140 a 159 e/ou 90 a 99",
        BpCategory.ESTAGIO_2 to "160 a 179 e/ou 100 a 109",
        BpCategory.ESTAGIO_3 to "180 ou mais e/ou 110 ou mais",
    )
    Panel {
        Text("Como ler os números", style = MaterialTheme.typography.titleMedium)
        rows.forEach { (cat, range) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(cat.color()))
                Text(cat.label, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Primeiro número: sistólica (máxima). Segundo: diastólica (mínima). Quando cada um cai numa faixa, vale a mais alta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
