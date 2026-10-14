package com.addsoncosta.evolui.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.GoalType
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.fmt
import com.addsoncosta.evolui.ui.onboarding.OnboardingScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val repo = container.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by repo.profile.collectAsState(initial = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = repo.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(text) }
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup salvo" else "Não foi possível salvar o backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

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
            Text("Perfil e ajustes", style = MaterialTheme.typography.headlineSmall)
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

        HealthCard()

        Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Salve seus dados num arquivo para guardar ou levar para outro aparelho.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { exportLauncher.launch("evolui-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Exportar backup")
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restaurar backup")
        }

        Text("Sobre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Evolui calcula o gasto diário pela equação de Mifflin-St Jeor e o nível de atividade, e compara com o que você come e os exercícios que faz.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Alimentos: Tabela Brasileira de Composição de Alimentos (TACO), 4ª edição, NEPA/Unicamp. " +
                "Exercícios: valores MET do Compendium of Physical Activities.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Este app não substitui orientação médica ou de nutricionista.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restaurar backup?") },
            text = { Text("Seus dados atuais (perfil, registros, alimentos e exercícios próprios) serão substituídos pelos do arquivo.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val ok = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                            }
                            repo.importBackup(text)
                        }.isSuccess
                        Toast.makeText(context, if (ok) "Backup restaurado" else "Arquivo de backup inválido", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancelar") } },
        )
    }
}
