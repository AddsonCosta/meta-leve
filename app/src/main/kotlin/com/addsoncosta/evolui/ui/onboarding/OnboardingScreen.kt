package com.addsoncosta.evolui.ui.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.core.Sex
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.KeyValue
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Divisões prontas de macronutrientes (% das calorias de carboidrato, proteína e gordura). */
private enum class MacroPreset(val label: String, val carb: Int, val protein: Int, val fat: Int, val about: String) {
    BALANCED("Equilibrada", 50, 20, 30, "A recomendação padrão para adultos. Boa para a maioria das pessoas."),
    PROTEIN("Mais proteína", 40, 30, 30, "Ajuda a manter os músculos durante o emagrecimento e a ganhar massa com treino."),
    LOW_CARB("Low carb", 25, 35, 40, "Menos pão, massa e açúcar; mais carnes, ovos e gorduras boas."),
    CUSTOM("Personalizada", 0, 0, 0, "Você define os percentuais. Use se um nutricionista passou outros valores."),
}

private const val STEPS = 5
private val StepTitles = listOf("Sobre você", "Seu dia a dia", "Seu objetivo", "Sua alimentação", "Tudo pronto")

/**
 * Cadastro do perfil e da meta, em passos. Com [initial] nulo é o primeiro uso (o rascunho fica
 * guardado se o app for fechado); caso contrário edita o perfil existente e [onCancel] volta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(initial: Profile?, onDone: () -> Unit, modifier: Modifier = Modifier, onCancel: (() -> Unit)? = null) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val editing = initial != null
    val draft = remember { if (editing) null else Draft(context) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: draft?.get("name") ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: draft?.get("sex")?.let { Sex.valueOf(it) } ?: Sex.MALE) }
    var birthDay by rememberSaveable {
        mutableStateOf(initial?.birthDate?.toEpochDay() ?: draft?.get("birth")?.toLongOrNull() ?: today.minusYears(30).toEpochDay())
    }
    var birthPicked by rememberSaveable { mutableStateOf(editing || draft?.get("birth") != null) }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.fmt(1) ?: draft?.get("height") ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.startWeightKg?.fmt(1) ?: draft?.get("weight") ?: "") }
    var activity by rememberSaveable { mutableStateOf(initial?.defaultActivity ?: draft?.get("activity")?.let { ActivityLevel.valueOf(it) } ?: ActivityLevel.LIGHT) }
    var goal by rememberSaveable { mutableStateOf(initial?.goalType ?: draft?.get("goal")?.let { GoalType.valueOf(it) } ?: GoalType.LOSE) }
    var target by rememberSaveable { mutableStateOf(initial?.targetWeightKg?.fmt(1) ?: draft?.get("target") ?: "") }
    var weekly by rememberSaveable { mutableStateOf(initial?.weeklyChangeKg?.toFloat()?.takeIf { it > 0 } ?: draft?.get("weekly")?.toFloatOrNull() ?: 0.5f) }
    val initialPreset = MacroPreset.entries.firstOrNull {
        it != MacroPreset.CUSTOM && it.carb == (initial?.carbPercent ?: 50) && it.protein == (initial?.proteinPercent ?: 20) && it.fat == (initial?.fatPercent ?: 30)
    } ?: MacroPreset.CUSTOM
    var preset by rememberSaveable { mutableStateOf(draft?.get("preset")?.let { MacroPreset.valueOf(it) } ?: initialPreset) }
    var carb by rememberSaveable { mutableStateOf((initial?.carbPercent ?: 50).toString()) }
    var protein by rememberSaveable { mutableStateOf((initial?.proteinPercent ?: 20).toString()) }
    var fat by rememberSaveable { mutableStateOf((initial?.fatPercent ?: 30).toString()) }
    var restart by rememberSaveable { mutableStateOf(false) }
    var pickBirth by rememberSaveable { mutableStateOf(false) }

    // Guarda o rascunho a cada mudança, para não perder nada se sair do app.
    LaunchedEffect(name, sex, birthDay, birthPicked, height, weight, activity, goal, target, weekly, preset) {
        draft?.save(
            "name" to name, "sex" to sex.name, "birth" to (if (birthPicked) birthDay.toString() else null),
            "height" to height, "weight" to weight, "activity" to activity.name, "goal" to goal.name,
            "target" to target, "weekly" to weekly.toString(), "preset" to preset.name,
        )
    }

    val birthDate = LocalDate.ofEpochDay(birthDay)
    val age = EnergyCalculator.ageOn(birthDate, today)
    val heightCm = height.toDecimal()
    val weightKg = weight.toDecimal()
    val currentKg = if (editing && !restart) initial!!.startWeightKg else weightKg
    val targetKg = if (goal == GoalType.KEEP) currentKg else target.toDecimal()
    val c = if (preset == MacroPreset.CUSTOM) carb.toIntOrNull() ?: 0 else preset.carb
    val pr = if (preset == MacroPreset.CUSTOM) protein.toIntOrNull() ?: 0 else preset.protein
    val f = if (preset == MacroPreset.CUSTOM) fat.toIntOrNull() ?: 0 else preset.fat

    // Erros de cada campo, em português claro.
    val nameError = if (name.isBlank()) "Escreva seu nome ou apelido." else null
    val ageError = if (age !in 14..100) "Confira a data de nascimento (idade entre 14 e 100 anos)." else null
    val heightError = when {
        height.isBlank() -> "Informe sua altura em centímetros."
        heightCm == null || heightCm !in 100.0..250.0 -> "A altura vai em centímetros, entre 100 e 250. Ex.: 175"
        else -> null
    }
    val weightError = when {
        !(editing && !restart) && weight.isBlank() -> "Informe seu peso atual."
        !(editing && !restart) && (weightKg == null || weightKg !in 25.0..400.0) -> "O peso vai em quilos, entre 25 e 400. Para 98,8 kg, digite 98,8"
        else -> null
    }
    val targetError = when {
        goal == GoalType.KEEP -> null
        target.isBlank() -> "Informe o peso que você quer alcançar."
        targetKg == null || targetKg !in 25.0..400.0 -> "O peso desejado vai em quilos, entre 25 e 400."
        currentKg != null && goal == GoalType.LOSE && targetKg >= currentKg -> "Para perder peso, a meta precisa ser menor que ${currentKg.fmt(1)} kg."
        currentKg != null && goal == GoalType.GAIN && targetKg <= currentKg -> "Para ganhar peso, a meta precisa ser maior que ${currentKg.fmt(1)} kg."
        else -> null
    }
    val macroError = if (c + pr + f != 100) "Os três precisam somar 100%. Agora somam ${c + pr + f}%." else null
    val stepErrors = listOf(
        listOfNotNull(nameError, ageError, heightError, weightError),
        emptyList(),
        listOfNotNull(targetError),
        listOfNotNull(macroError),
        emptyList(),
    )
    val allValid = stepErrors.all { it.isEmpty() }
    fun err(e: String?) = if (showErrors) e else null

    val back: () -> Unit = {
        showErrors = false
        if (step > 0) step-- else onCancel?.invoke()
    }
    BackHandler(enabled = step > 0 || onCancel != null, onBack = back)

    Column(modifier.fillMaxSize().then(if (editing) Modifier else Modifier.safeDrawingPadding()).imePadding()) {
        // Topo: voltar, passo e barra de progresso.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            else if (onCancel != null) IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Fechar sem salvar") }
            else Spacer(Modifier.padding(24.dp))
            Text("Passo ${step + 1} de $STEPS", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (step + 1f) / STEPS },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (step == 0 && !editing) Overline("Bem-vindo ao Evolui", color = Evolui.Neon)
            Text(StepTitles[step], style = MaterialTheme.typography.headlineMedium)

            when (step) {
                0 -> {
                    Text("Com estes dados o Evolui calcula quanto seu corpo gasta por dia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        name, { name = it },
                        label = { Text("Nome") },
                        supportingText = { Text(err(nameError) ?: "Como você quer ser chamado. Ex.: Addson") },
                        isError = err(nameError) != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Sexo biológico", fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(Sex.MALE to "Masculino", Sex.FEMALE to "Feminino").forEachIndexed { i, (value, label) ->
                            SegmentedButton(selected = sex == value, onClick = { sex = value }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                    Text("Usado na fórmula do metabolismo, que é diferente para homens e mulheres.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Data de nascimento", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { pickBirth = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (birthPicked) "${birthDate.format(DateFormat)} ($age anos)" else "Toque para escolher")
                    }
                    err(ageError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    NumberField(height, { height = it }, "Altura", Modifier.fillMaxWidth(), suffix = "cm", help = "Em centímetros, sem vírgula. Ex.: 175", error = err(heightError))
                    if (!editing || restart) {
                        NumberField(weight, { weight = it }, "Peso atual", Modifier.fillMaxWidth(), suffix = "kg", help = "Em quilos. Use vírgula para os gramas. Ex.: 98,8", error = err(weightError))
                    }
                }
                1 -> {
                    Text("Como é seu dia sem contar os exercícios? Os treinos você registra à parte, ou eles vêm do Samsung Health.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActivityLevel.entries.forEach { level ->
                        Choice(selected = activity == level, title = level.label, subtitle = level.description) { activity = level }
                    }
                }
                2 -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(GoalType.LOSE to "Perder", GoalType.KEEP to "Manter", GoalType.GAIN to "Ganhar").forEachIndexed { i, (value, label) ->
                            SegmentedButton(selected = goal == value, onClick = { goal = value }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                        }
                    }
                    if (goal != GoalType.KEEP) {
                        NumberField(
                            target, { target = it }, "Peso que quer alcançar", Modifier.fillMaxWidth(), suffix = "kg",
                            help = currentKg?.let { "Você está com ${it.fmt(1)} kg. Ex.: ${(it + if (goal == GoalType.LOSE) -10 else 5).fmt()}" } ?: "Em quilos. Ex.: 85",
                            error = err(targetError),
                        )
                        Text("Ritmo: ${weekly.toDouble().fmt(2)} kg por semana", fontWeight = FontWeight.Bold)
                        Slider(value = weekly, onValueChange = { weekly = (it * 4).roundToInt() / 4f }, valueRange = 0.25f..1.5f, steps = 4)
                        Text(
                            "Quanto mais rápido, menos você pode comer por dia. Entre 0,5 e 1 kg por semana é o mais indicado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (goal == GoalType.LOSE && weekly > EnergyCalculator.MAX_SAFE_WEEKLY_KG) {
                            Text("Atenção: perder mais de 1 kg por semana não é considerado seguro sem acompanhamento médico.", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("O Evolui vai calcular quanto comer para manter o peso de hoje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                3 -> {
                    Text(
                        "De onde vêm suas calorias: carboidratos (pão, arroz, frutas), proteínas (carnes, ovos, feijão) e gorduras (azeite, castanhas). Se não sabe, fique com Equilibrada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MacroPreset.entries.forEach { p ->
                        Choice(
                            selected = preset == p,
                            title = p.label + if (p != MacroPreset.CUSTOM) " · ${p.carb}/${p.protein}/${p.fat}" else "",
                            subtitle = p.about,
                        ) { preset = p }
                    }
                    if (preset == MacroPreset.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(carb, { carb = it }, "Carbo", Modifier.weight(1f), suffix = "%", decimal = false)
                            NumberField(protein, { protein = it }, "Proteína", Modifier.weight(1f), suffix = "%", decimal = false)
                            NumberField(fat, { fat = it }, "Gordura", Modifier.weight(1f), suffix = "%", decimal = false)
                        }
                        Text(macroError ?: "Soma: 100% ✓", color = if (macroError != null) MaterialTheme.colorScheme.error else Evolui.Neon)
                    }
                }
                else -> {
                    if (allValid) {
                        val profile = buildProfile(initial, restart, name, sex, birthDate, heightCm!!, currentKg!!, activity, goal, targetKg!!, weekly.toDouble(), c, pr, f, today)
                        SummaryCard(profile, currentKg, today)
                    } else {
                        Text("Faltam algumas informações:", fontWeight = FontWeight.Bold)
                        stepErrors.flatten().forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                    }
                    if (editing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(restart, { restart = it })
                            Text("Recomeçar a dieta a partir de hoje (pede o peso de hoje no passo 1)")
                        }
                    }
                    Text(
                        "O Evolui não substitui orientação médica ou de nutricionista. Consulte um profissional antes de iniciar uma dieta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Rodapé: avançar ou concluir.
        Column(Modifier.padding(20.dp)) {
            if (showErrors && stepErrors[step].isNotEmpty()) {
                Text("Corrija o que está em vermelho para continuar.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = {
                    if (step < STEPS - 1) {
                        if (stepErrors[step].isEmpty()) {
                            showErrors = false
                            step++
                        } else {
                            showErrors = true
                        }
                    } else if (allValid) {
                        val profile = buildProfile(initial, restart, name, sex, birthDate, heightCm!!, currentKg!!, activity, goal, targetKg!!, weekly.toDouble(), c, pr, f, today)
                        scope.launch {
                            container.repository.saveProfile(profile)
                            if (!editing || restart) container.repository.updateDay(today) { it.copy(weightKg = currentKg) }
                            draft?.clear()
                            onDone()
                        }
                    } else {
                        // Leva ao primeiro passo com problema.
                        step = stepErrors.indexOfFirst { it.isNotEmpty() }
                        showErrors = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when {
                        step < STEPS - 1 -> "Próximo"
                        editing -> "Salvar"
                        else -> "Começar a usar"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (pickBirth) {
        val state = rememberDatePickerState(initialSelectedDateMillis = birthDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickBirth = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        birthDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        birthPicked = true
                    }
                    pickBirth = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickBirth = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

/** Opção grande e tocável, com título e explicação. */
@Composable
private fun Choice(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Evolui.NeonDim.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Evolui.Neon else Color.Transparent),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) Evolui.Neon else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildProfile(
    initial: Profile?,
    restart: Boolean,
    name: String,
    sex: Sex,
    birthDate: LocalDate,
    heightCm: Double,
    weightKg: Double,
    activity: ActivityLevel,
    goal: GoalType,
    targetKg: Double,
    weekly: Double,
    carb: Int,
    protein: Int,
    fat: Int,
    today: LocalDate,
): Profile {
    val fresh = initial == null || restart
    return Profile(
        name = name.trim(),
        sex = sex,
        birthDate = birthDate,
        heightCm = heightCm,
        startWeightKg = if (fresh) weightKg else initial!!.startWeightKg,
        startDate = if (fresh) today else initial!!.startDate,
        goalType = goal,
        targetWeightKg = targetKg,
        weeklyChangeKg = if (goal == GoalType.KEEP) 0.0 else weekly,
        defaultActivity = activity,
        carbPercent = carb,
        proteinPercent = protein,
        fatPercent = fat,
    )
}

@Composable
private fun SummaryCard(profile: Profile, weightKg: Double, today: LocalDate) {
    val s = EnergyCalculator.summary(profile, today, weightKg, profile.defaultActivity, 0.0, 0.0)
    val bmi = EnergyCalculator.bmi(weightKg, profile.heightCm)
    val due = EnergyCalculator.projectedDate(today, weightKg, profile.targetWeightKg, profile.weeklyChangeKg)
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Sua meta diária", color = Evolui.Neon)
            Text("${s.intakeGoal.fmt()} kcal", style = MaterialTheme.typography.displaySmall, color = Evolui.Neon)
            Text("é quanto você pode comer por dia (sem contar treinos).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyValue("Gasto diário estimado", "${s.burn.fmt()} kcal")
            KeyValue("IMC", "${bmi.fmt(1)} · ${EnergyCalculator.bmiLabel(bmi)}")
            KeyValue("Carbo / proteína / gordura", "${s.macros.carbG.fmt()} / ${s.macros.proteinG.fmt()} / ${s.macros.fatG.fmt()} g")
            due?.let { KeyValue("Previsão para ${profile.targetWeightKg.fmt(1)} kg", it.format(DateFormat)) }
            if (s.belowSafeMinimum) {
                Text(
                    "A meta fica abaixo de ${EnergyCalculator.MIN_SAFE_INTAKE.fmt()} kcal por dia. Volte ao passo 3 e escolha um ritmo mais lento.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Rascunho do primeiro cadastro, guardado no celular até concluir. */
private class Draft(context: Context) {
    private val prefs = context.getSharedPreferences("cadastro_rascunho", Context.MODE_PRIVATE)

    fun get(key: String): String? = prefs.getString(key, null)

    fun save(vararg values: Pair<String, String?>) {
        prefs.edit().apply { values.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
