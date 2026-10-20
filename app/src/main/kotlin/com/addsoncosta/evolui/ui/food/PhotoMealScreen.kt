package com.addsoncosta.evolui.ui.food

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.ai.AiDocumentReader
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.Measure
import com.addsoncosta.evolui.data.MeasureKind
import com.addsoncosta.evolui.data.Nutrition
import com.addsoncosta.evolui.data.PortionChoice
import com.addsoncosta.evolui.data.Portions
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.data.nutritionFor
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.settings.MyAiDialog
import com.addsoncosta.evolui.ui.share.loadBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pasta temporária da foto (cache; apagada ao sair da tela). Precisa estar em res/xml/file_paths.xml. */
private const val PHOTO_DIR = "prato"
private const val PHOTO_FILE = "prato.jpg"

/** Lado maior da foto analisada e mostrada (a original da câmera é bem maior). */
private const val MAX_SIDE = 1024

/** Ponto de partida quando a foto não diz o que há no prato: arroz, feijão, carne e salada, porções médias. */
private val typicalPlate = listOf(
    "arroz, tipo 1, cozido",
    "feijao, carioca, cozido",
    "carne, bovina, patinho, sem gordura, grelhado",
    "alface, crespa, crua",
    "tomate, salada",
)

/** Tamanho da porção no prato. [word] é procurado no nome da medida caseira ("bife pequeno", "concha média"). */
internal enum class PlateSize(val label: String, val word: String, val factor: Double) {
    SMALL("Pequena", "pequen", 0.6),
    MEDIUM("Média", "medi", 1.0),
    LARGE("Grande", "grande", 1.5),
}

/** Um alimento do prato, com a medida escolhida. [size] nulo = medida ajustada à mão (ou estimada em gramas). */
internal data class PlateItem(
    val food: FoodEntity,
    val measures: List<Measure>,
    val choice: PortionChoice,
    val size: PlateSize?,
    val checked: Boolean,
    /** Medida caseira dita pela IA, só para mostrar. */
    val hint: String? = null,
) {
    /** Montado com os valores da IA (não está na tabela do app). */
    val fromAi: Boolean get() = food.id < 0

    val nutrition: Nutrition get() = food.nutritionFor(choice.grams)
}

/**
 * Porção pequena, média ou grande usando as medidas caseiras do alimento: primeiro "porção pequena/média/grande",
 * depois qualquer medida com o tamanho no nome ("bife médio", "concha pequena"); a média também aceita "pires".
 * Sem medida com o tamanho, usa a média vezes 0,6 (pequena) ou 1,5 (grande).
 */
internal fun sizedChoice(food: FoodEntity, measures: List<Measure>, size: PlateSize): PortionChoice {
    val household = measures.filter { it.kind == MeasureKind.PORTION || it.kind == MeasureKind.CUSTOM }
    fun norm(m: Measure) = Repository.normalize(m.name)
    fun named(s: PlateSize): Measure? =
        household.firstOrNull { norm(it).startsWith("porcao ${s.word}") }
            ?: (if (s == PlateSize.MEDIUM) household.firstOrNull { norm(it).startsWith("pires") } else null)
            ?: household.firstOrNull { norm(it).contains(s.word) }
    named(size)?.let { return it.choice(1.0) }
    val (m, a) = named(PlateSize.MEDIUM)?.let { it to 1.0 } ?: Portions.defaultChoice(food, measures, null)
    if (size == PlateSize.MEDIUM) return m.choice(a)
    val byWeight = m.kind == MeasureKind.GRAMS || m.kind == MeasureKind.ML
    val amount = if (byWeight) max(10.0, round(a * size.factor / 10) * 10) else max(0.5, round(a * size.factor * 2) / 2)
    if (amount != a) return m.choice(amount)
    // Meia unidade já é o mínimo: passa para gramas.
    val grams = max(10.0, round(m.grams(a) * size.factor / 10) * 10)
    return Portions.gramsMeasure(measures).choice(grams)
}

private suspend fun makeItem(foods: FoodRepository, s: PlateSuggestion): PlateItem {
    val measures = Portions.measures(s.food, if (s.food.id > 0) foods.portionsOnce(s.food.id) else emptyList())
    val grams = s.grams
    val servingName = s.food.servingName
    val servingGrams = s.food.servingGrams
    return if (grams != null && grams > 0) {
        // Alimento da IA: mostra na medida caseira que ela disse ("2 colheres de sopa (≈ 30 g)").
        val choice = if (s.food.id < 0 && servingName != null && servingGrams != null && servingGrams > 0) {
            PortionChoice(Portions.round1(grams), Portions.round1(grams / servingGrams), servingName)
        } else {
            PortionChoice(Portions.round1(grams), null, null)
        }
        PlateItem(s.food, measures, choice, null, s.checked, hint = s.hint.takeIf { s.food.id > 0 })
    } else {
        PlateItem(s.food, measures, sizedChoice(s.food, measures, PlateSize.MEDIUM), PlateSize.MEDIUM, s.checked)
    }
}

/** Atalho na tela Comer. */
@Composable
internal fun PhotoMealCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Prato por foto")
                Text(
                    "Fotografe o prato e confira os alimentos sugeridos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledTonalButton(onClick = onOpen) {
                Icon(Icons.Filled.PhotoCamera, null)
                Text(" Abrir")
            }
        }
    }
}

/**
 * Prato por foto: tira ou escolhe a foto, reconhece no aparelho o que parece haver no prato, sugere alimentos
 * do banco com porções médias e registra tudo de uma vez. A foto fica só na memória e no cache enquanto a tela
 * está aberta. Só sai do celular se o usuário tocar em "Analisar com minha IA" (vai para a IA que ele conectou).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoMealScreen(vm: FoodViewModel, onClose: () -> Unit, estimator: PlateEstimator = remember { OnDevicePlateEstimator() }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val date by vm.date.collectAsStateWithLifecycle()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var seen by remember { mutableStateOf<List<PlateGuess>>(emptyList()) }
    var plate by remember { mutableStateOf<List<PlateItem>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<PlateItem?>(null) }
    var type by remember { mutableStateOf(defaultMealType()) }
    var job by remember { mutableStateOf<Job?>(null) }
    val dir = remember { File(context.cacheDir, PHOTO_DIR) }
    val ai = remember { AiDocumentReader(context) }
    var aiConnected by remember { mutableStateOf(ai.isConnected()) }
    var aiName by remember { mutableStateOf(ai.providerName()) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf(false) }
    var connectingAi by remember { mutableStateOf(false) }
    // O celular não reconheceu nada útil na foto.
    var unrecognized by remember { mutableStateOf(false) }
    // Itens que vieram da análise da foto (do celular ou da IA); os adicionados à mão ficam quando a IA responde.
    var autoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var nextTempId by remember { mutableStateOf(-1L) }

    // Ao sair da tela, a foto temporária é apagada.
    DisposableEffect(Unit) {
        onDispose { runCatching { dir.deleteRecursively() } }
    }

    suspend fun addSuggestions(list: List<PlateSuggestion>) {
        val have = plate.map { it.food.id }.toHashSet()
        val fresh = list.filter { have.add(it.food.id) }.map { makeItem(vm.foods, it) }
        val now = plate.map { it.food.id }.toHashSet()
        plate = plate + fresh.filter { it.food.id !in now }
    }

    fun addTypical() {
        scope.launch {
            val list = vm.foods.foodsByNames(typicalPlate).map { PlateSuggestion(it, true, null) }
            addSuggestions(list)
            if (list.isEmpty()) note = "Não encontrei os alimentos do prato típico. Adicione pela busca."
        }
    }

    fun analyze(load: () -> Bitmap?) {
        job?.cancel()
        job = scope.launch {
            busy = true
            aiBusy = false
            note = null
            aiNote = null
            unrecognized = false
            val bmp = withContext(Dispatchers.IO) {
                val b = load()
                // A foto da câmera não fica guardada: depois de aberta (já reduzida), o arquivo é apagado.
                runCatching { dir.deleteRecursively() }
                b
            }
            if (bmp == null) {
                note = "Não foi possível abrir a foto. Tente outra."
                busy = false
                return@launch
            }
            photo = bmp
            val guesses = try {
                estimator.estimate(bmp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (guesses == null) {
                seen = emptyList()
                unrecognized = true
            } else {
                seen = guesses
                val suggestions = resolveGuesses(vm.foods, guesses)
                addSuggestions(suggestions)
                autoIds = autoIds + suggestions.map { it.food.id }
                // Só rótulos gerais ("comida", "prato") ou nada: o celular não sabe que prato é.
                unrecognized = suggestions.isEmpty()
            }
            busy = false
        }
    }

    /** Manda a foto para a IA do usuário e troca as sugestões automáticas pelo que ela encontrou. */
    fun analyzeWithAi() {
        val bmp = photo ?: return
        job?.cancel()
        job = scope.launch {
            busy = true
            aiBusy = true
            aiNote = null
            val result = ai.readPlate(bmp)
            val analysis = result.getOrNull()
            if (analysis == null) {
                aiError = true
                aiNote = result.exceptionOrNull()?.message ?: "Não deu para analisar com a sua IA agora. Tente de novo."
            } else {
                val list = resolveAiPlate(vm.foods, analysis) { nextTempId.also { nextTempId = it - 1 } }
                if (list.isEmpty()) {
                    aiError = true
                    aiNote = analysis.note ?: "A sua IA não encontrou alimentos nesta foto. Adicione pela busca ou pelas sugestões."
                } else {
                    val fresh = list.map { makeItem(vm.foods, it) }
                    val freshIds = fresh.map { it.food.id }.toHashSet()
                    plate = fresh + plate.filter { it.food.id !in autoIds && it.food.id !in freshIds }
                    autoIds = freshIds
                    unrecognized = false
                    aiError = false
                    val local = fresh.count { !it.fromAi }
                    aiNote = buildString {
                        append("Sua IA encontrou ")
                        append(if (fresh.size == 1) "1 alimento" else "${fresh.size} alimentos")
                        append(" (≈ ${analysis.kcal.fmt()} kcal)")
                        analysis.confidenceLabel?.let { append(", confiança $it") }
                        append(". ")
                        if (local > 0) append("Os que estão na tabela do app usam os valores dela. ")
                        append("Confira as porções antes de registrar.")
                        analysis.note?.let { append("\n").append(it) }
                    }
                }
            }
            aiBusy = false
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) analyze { loadBitmap(context, File(dir, PHOTO_FILE), MAX_SIDE) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analyze { loadBitmap(context, uri, MAX_SIDE) }
    }

    fun takePhoto() {
        try {
            dir.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, PHOTO_FILE))
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            note = "Não encontrei um app de câmera. Escolha uma foto da galeria."
        } catch (e: IllegalArgumentException) {
            note = "Não foi possível abrir a câmera. Escolha uma foto da galeria."
        }
    }

    fun pickPhoto() {
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            note = "Não encontrei a galeria de fotos."
        }
    }

    if (adding) {
        // Buscar um alimento que a foto não mostrou.
        val closePicker = {
            adding = false
            vm.query.value = ""
        }
        BackHandler(onBack = closePicker)
        val query by vm.query.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(8.dp, 16.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = closePicker) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column {
                    Overline("Prato por foto")
                    Text("Adicionar alimento", style = MaterialTheme.typography.headlineMedium)
                }
            }
            SearchField(query, { vm.query.value = it })
            FoodList(
                vm = vm,
                query = query,
                onFood = { food ->
                    closePicker()
                    scope.launch { addSuggestions(listOf(PlateSuggestion(food, true, null))) }
                },
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        BackHandler(onBack = onClose)
        val chosen = plate.filter { it.checked }
        val total = chosen.fold(Nutrition(0.0, 0.0, 0.0, 0.0, 0.0)) { acc, item ->
            val n = item.nutrition
            Nutrition(acc.kcal + n.kcal, acc.protein + n.protein, acc.fat + n.fat, acc.carb + n.carb, acc.fiber + n.fiber)
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(8.dp, 16.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column {
                    Overline("Comer")
                    Text("Prato por foto", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Registrando em: ${dayLabel(date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "foto") {
                    Panel {
                        val bmp = photo
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Foto do prato",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
                            )
                        } else {
                            Text(
                                "Tire uma foto do prato (de cima, com boa luz) ou escolha uma da galeria. " +
                                    "O app sugere os alimentos e você confere antes de registrar.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { takePhoto() }, enabled = !busy) {
                                Icon(Icons.Filled.PhotoCamera, null)
                                Text(if (bmp == null) " Tirar foto" else " Outra foto")
                            }
                            OutlinedButton(onClick = { pickPhoto() }, enabled = !busy) {
                                Icon(Icons.Filled.PhotoLibrary, null)
                                Text(" Galeria")
                            }
                        }
                        Text(
                            "A foto é analisada no próprio celular e é apagada ao sair desta tela. Ela só vai para a internet " +
                                "se você tocar em \"Analisar com minha IA\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) {
                    item(key = "analisando") {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(if (aiBusy) "  Perguntando para a sua IA…" else "  Analisando a foto…")
                        }
                    }
                }
                if (photo != null && !busy && unrecognized) {
                    item(key = "nao-reconheci") {
                        Panel {
                            Text("Não reconheci este prato.", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (aiConnected) "Use sua IA para identificar os alimentos e as porções, ou escolha nas sugestões abaixo."
                                else "Use sua IA para identificar: conecte a sua (Claude, ChatGPT, Gemini e outras). " +
                                    "Ou escolha nas sugestões abaixo.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            AiButton(aiConnected, aiName, onAnalyze = { analyzeWithAi() }, onConnect = { connectingAi = true })
                        }
                    }
                }
                aiNote?.let { text ->
                    item(key = "ia-aviso") {
                        Text(text, color = if (aiError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
                note?.let { text ->
                    item(key = "aviso") { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (seen.isNotEmpty()) {
                    item(key = "visto") {
                        Text(
                            "Reconhecido na foto: " + seen.joinToString(", ") { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (plate.isNotEmpty()) {
                    item(key = "estimativa") {
                        Text(
                            "Estimativa: confira os alimentos e as porções.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(plate, key = { "i${it.food.id}" }) { item ->
                    PlateItemRow(
                        item = item,
                        onCheck = { c -> plate = plate.map { if (it.food.id == item.food.id) it.copy(checked = c) else it } },
                        onSize = { s ->
                            plate = plate.map {
                                if (it.food.id == item.food.id) {
                                    it.copy(choice = sizedChoice(it.food, it.measures, s), size = s, checked = true)
                                } else {
                                    it
                                }
                            }
                        },
                        onAdjust = { adjusting = item },
                        onRemove = { plate = plate.filter { it.food.id != item.food.id } },
                    )
                }
                item(key = "acoes") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            vm.query.value = ""
                            adding = true
                        }) {
                            Icon(Icons.Filled.Add, null)
                            Text(" Adicionar alimento")
                        }
                        OutlinedButton(onClick = { addTypical() }, enabled = !busy) {
                            Icon(Icons.Filled.Restaurant, null)
                            Text(" Prato típico")
                        }
                    }
                }
                if (photo != null && !busy && !unrecognized) {
                    item(key = "ia") { AiButton(aiConnected, aiName, onAnalyze = { analyzeWithAi() }, onConnect = { connectingAi = true }) }
                }
                item(key = "sugestoes") {
                    QuickSuggestions(
                        vm = vm,
                        hint = "Toque para pôr no prato.",
                        onFood = { food ->
                            scope.launch {
                                val last = vm.foods.lastMeal(food.id)
                                addSuggestions(listOf(PlateSuggestion(food, true, last?.grams)))
                            }
                        },
                        onMeal = { meal ->
                            scope.launch {
                                val list = meal.items.mapNotNull { mi ->
                                    mi.foodId?.let { vm.foods.food(it) }?.let { PlateSuggestion(it, true, mi.grams) }
                                }
                                addSuggestions(list)
                                if (list.size < meal.items.size) note = "Alguns itens de \"${meal.meal.name}\" não estão mais na lista de alimentos."
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (plate.isEmpty() && !busy) {
                    item(key = "dica-tipico") {
                        Text(
                            "Prato típico: arroz, feijão, carne e salada em porções médias, para você ajustar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (plate.isNotEmpty()) {
                    item(key = "total") {
                        Panel(Modifier.padding(top = 8.dp)) {
                            Overline("Total do prato (≈)")
                            Text(
                                "≈ ${total.kcal.fmt()} kcal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Carbo ${total.carb.fmt(1)} g · Proteína ${total.protein.fmt(1)} g · " +
                                    "Gordura ${total.fat.fmt(1)} g · Fibra ${total.fiber.fmt(1)} g",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${chosen.size} de ${plate.size} alimentos marcados",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Refeição", style = MaterialTheme.typography.labelLarge)
                            MealTypePicker(type, { type = it })
                            Button(
                                onClick = {
                                    vm.logPlate(chosen.map { it.food to it.choice }, type)
                                    val n = chosen.size
                                    Toast.makeText(
                                        context,
                                        (if (n == 1) "1 alimento registrado" else "$n alimentos registrados") + " em ${type.label}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    onClose()
                                },
                                enabled = chosen.isNotEmpty() && !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Adicionar ao ${type.label.lowercase()}") }
                        }
                    }
                }
            }
        }
    }

    if (connectingAi) {
        MyAiDialog(onDismiss = {
            connectingAi = false
            aiConnected = ai.isConnected()
            aiName = ai.providerName()
        })
    }

    adjusting?.let { item ->
        PortionDialog(
            food = item.food,
            foods = vm.foods,
            onDismiss = { adjusting = null },
            onConfirm = { choice, _ ->
                plate = plate.map { if (it.food.id == item.food.id) it.copy(choice = choice, size = null, checked = true) else it }
                adjusting = null
            },
            confirmLabel = "Usar",
            showMealType = false,
            initial = item.choice,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateItemRow(
    item: PlateItem,
    onCheck: (Boolean) -> Unit,
    onSize: (PlateSize) -> Unit,
    onAdjust: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.checked, onCheckedChange = onCheck)
            Column(Modifier.weight(1f).clickable(onClick = onAdjust)) {
                Text(item.food.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${amountLabel(item.choice.grams, item.choice.amount, item.choice.unit)} · ≈ ${item.nutrition.kcal.fmt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val extra = when {
                    item.fromAi -> "Valores da sua IA (não está na tabela do app)"
                    item.hint != null -> "Sua IA disse: ${item.hint}"
                    else -> null
                }
                if (extra != null) {
                    Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Tirar do prato") }
        }
        FlowRow(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlateSize.entries.forEach { s ->
                FilterChip(selected = item.size == s, onClick = { onSize(s) }, label = { Text(s.label) })
            }
            AssistChip(
                onClick = onAdjust,
                label = { Text("Medida") },
                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
    }
}

/** "Analisar com minha IA" (já conectada) ou "Conectar minha IA". */
@Composable
private fun AiButton(connected: Boolean, name: String?, onAnalyze: () -> Unit, onConnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (connected) {
            FilledTonalButton(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null)
                Text(" Analisar com minha IA")
            }
            Text(
                "A foto vai para ${name ?: "a IA que você conectou"}, com a sua chave. O custo fica na sua conta de lá.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null)
                Text(" Conectar minha IA")
            }
        }
    }
}
