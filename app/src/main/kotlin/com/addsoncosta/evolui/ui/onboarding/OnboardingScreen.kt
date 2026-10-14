package com.addsoncosta.evolui.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.components.toDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Cadastro do perfil e da meta. Com [initial] nulo é o primeiro uso;
 * caso contrário edita o perfil existente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(initial: Profile?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val editing = initial != null

    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sex by rememberSaveable { mutableStateOf(initial?.sex ?: Sex.FEMALE) }
    var birthDay by rememberSaveable { mutableStateOf(initial?.birthDate?.toEpochDay() ?: today.minusYears(30).toEpochDay()) }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.fmt(1) ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.startWeightKg?.fmt(1) ?: "") }
    var activity by rememberSaveable { mutableStateOf(initial?.defaultActivity ?: ActivityLevel.LIGHT) }
    var goal by rememberSaveable { mutableStateOf(initial?.goalType ?: GoalType.LOSE) }
    var target by rememberSaveable { mutableStateOf(initial?.targetWeightKg?.fmt(1) ?: "") }
    var weekly by rememberSaveable { mutableStateOf(initial?.weeklyChangeKg?.toFloat() ?: 0.5f) }
    var carb by rememberSaveable { mutableStateOf((initial?.carbPercent ?: 50).toString()) }
    var protein by rememberSaveable { mutableStateOf((initial?.proteinPercent ?: 20).toString()) }
    var fat by rememberSaveable { mutableStateOf((initial?.fatPercent ?: 30).toString()) }
    var restart by rememberSaveable { mutableStateOf(false) }
    var pickBirth by rememberSaveable { mutableStateOf(false) }

    val birthDate = LocalDate.ofEpochDay(birthDay)
    val heightCm = height.toDecimal()
    val weightKg = weight.toDecimal()
    val targetKg = if (goal == GoalType.KEEP) weightKg else target.toDecimal()
    val macroSum = (carb.toIntOrNull() ?: 0) + (protein.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)
    val directionOk = when (goal) {
        GoalType.LOSE -> weightKg != null && targetKg != null && targetKg < weightKg
        GoalType.GAIN -> weightKg != null && targetKg != null && targetKg > weightKg
        GoalType.KEEP -> true
    }
    val valid = name.isNotBlank() && heightCm != null && heightCm in 100.0..250.0 &&
        weightKg != null && weightKg in 25.0..400.0 && targetKg != null && directionOk && macroSum == 100

    Column(
        modifier
            .fillMaxSize()
            .then(if (editing) Modifier else Modifier.safeDrawingPadding())
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!editing) {
            Text("Bem-vindo ao Evolui", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Conte um pouco sobre você para calcularmos sua meta diária.")
        }

        Section("Sobre você")
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Sex.FEMALE to "Feminino", Sex.MALE to "Masculino").forEachIndexed { i, (value, label) ->
                SegmentedButton(selected = sex == value, onClick = { sex = value }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                    Text(label)
                }
            }
        }
        OutlinedButton(onClick = { pickBirth = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Nascimento: ${birthDate.format(DateFormat)} (${EnergyCalculator.ageOn(birthDate, today)} anos)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(height, { height = it }, "Altura", Modifier.weight(1f), suffix = "cm")
            if (!editing || restart) {
                NumberField(weight, { weight = it }, "Peso atual", Modifier.weight(1f), suffix = "kg")
            }
        }

        Section("Nível de atividade no dia a dia")
        Text(
            "Não conte os exercícios aqui; eles são registrados à parte.",
            style = MaterialTheme.typography.bodySmall,
        )
        ActivityLevel.entries.forEach { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = activity == level, onClick = { activity = level }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = activity == level, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(level.label)
                    Text(level.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Section("Objetivo")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(GoalType.LOSE to "Perder", GoalType.KEEP to "Manter", GoalType.GAIN to "Ganhar").forEachIndexed { i, (value, label) ->
                SegmentedButton(selected = goal == value, onClick = { goal = value }, shape = SegmentedButtonDefaults.itemShape(i, 3)) {
                    Text(label)
                }
            }
        }
        if (goal != GoalType.KEEP) {
            NumberField(target, { target = it }, "Peso desejado", Modifier.fillMaxWidth(), suffix = "kg")
            if (target.isNotEmpty() && !directionOk) {
                Text(
                    if (goal == GoalType.LOSE) "O peso desejado precisa ser menor que o atual." else "O peso desejado precisa ser maior que o atual.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text("Ritmo: ${weekly.toDouble().fmt(2)} kg por semana")
            Slider(value = weekly, onValueChange = { weekly = (it * 4).roundToInt() / 4f }, valueRange = 0.25f..1.5f, steps = 4)
            if (goal == GoalType.LOSE && weekly > EnergyCalculator.MAX_SAFE_WEEKLY_KG) {
                Text(
                    "Atenção: perder mais de 1 kg por semana não é considerado seguro sem acompanhamento médico.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Section("Distribuição de macronutrientes (% das calorias)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(carb, { carb = it }, "Carbo", Modifier.weight(1f), suffix = "%", decimal = false)
            NumberField(protein, { protein = it }, "Proteína", Modifier.weight(1f), suffix = "%", decimal = false)
            NumberField(fat, { fat = it }, "Gordura", Modifier.weight(1f), suffix = "%", decimal = false)
        }
        if (macroSum != 100) Text("A soma precisa ser 100% (agora: $macroSum%).", color = MaterialTheme.colorScheme.error)

        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(restart, { restart = it })
                Text("Recomeçar a dieta a partir de hoje")
            }
        }

        if (valid) {
            val profile = buildProfile(
                initial, restart, name, sex, birthDate, heightCm!!, weightKg!!, activity, goal, targetKg!!, weekly.toDouble(),
                carb.toInt(), protein.toInt(), fat.toInt(), today,
            )
            SummaryCard(profile, weightKg, today)
        }

        Button(
            enabled = valid,
            onClick = {
                val profile = buildProfile(
                    initial, restart, name, sex, birthDate, heightCm!!, weightKg!!, activity, goal, targetKg!!, weekly.toDouble(),
                    carb.toInt(), protein.toInt(), fat.toInt(), today,
                )
                scope.launch {
                    container.repository.saveProfile(profile)
                    if (!editing || restart) container.repository.updateDay(today) { it.copy(weightKg = weightKg) }
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (editing) "Salvar" else "Começar") }
        Text(
            "O Evolui não substitui orientação médica ou de nutricionista. Consulte um profissional antes de iniciar uma dieta.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (pickBirth) {
        val state = rememberDatePickerState(initialSelectedDateMillis = birthDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickBirth = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        birthDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickBirth = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickBirth = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Resumo", fontWeight = FontWeight.Bold)
            Text("IMC: ${bmi.fmt(1)} (${EnergyCalculator.bmiLabel(bmi)})")
            Text("Gasto diário estimado: ${s.burn.fmt()} kcal")
            Text("Meta de consumo: ${s.intakeGoal.fmt()} kcal por dia")
            due?.let { Text("Previsão para chegar a ${profile.targetWeightKg.fmt(1)} kg: ${it.format(DateFormat)}") }
            if (s.belowSafeMinimum) {
                Text(
                    "A meta fica abaixo de ${EnergyCalculator.MIN_SAFE_INTAKE.fmt()} kcal por dia. Considere um ritmo mais lento.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}
