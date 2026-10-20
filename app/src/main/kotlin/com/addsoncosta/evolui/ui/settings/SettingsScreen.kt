package com.addsoncosta.evolui.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.backup.BackupSection
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.onboarding.OnboardingScreen
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val profile by repo.profile.collectAsState(initial = null)
    var editing by rememberSaveable { mutableStateOf(false) }

    val p = profile ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

    if (editing) {
        OnboardingScreen(initial = p, onDone = { editing = false }, onCancel = { editing = false })
        return
    }
    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        }
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text("${EnergyCalculator.ageOn(p.birthDate, LocalDate.now())} anos · ${p.heightCm.fmt()} cm")
                val goal = when (p.goalType) {
                    GoalType.LOSE -> "Perder peso até ${p.targetWeightKg.fmt(1)} kg (${p.weeklyChangeKg.fmt(2)} kg/semana)"
                    GoalType.GAIN -> "Ganhar peso até ${p.targetWeightKg.fmt(1)} kg (${p.weeklyChangeKg.fmt(2)} kg/semana)"
                    GoalType.KEEP -> "Manter o peso"
                }
                Text(goal)
                Text("Dieta iniciada em ${p.startDate.format(DateFormat)} com ${p.startWeightKg.fmt(1)} kg")
                Text("Atividade padrão: ${p.defaultActivity.label}")
                Text("Macros: carbo ${p.carbPercent}% · proteína ${p.proteinPercent}% · gordura ${p.fatPercent}%")
            }
        }
        Button(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil e meta") }

        ThemePicker()

        // Minha IA (prato por foto com a IA do próprio usuário).
        MyAiSettingsEntry()

        HealthCard()

        BackupSection()

        AboutCard()
    }

}
