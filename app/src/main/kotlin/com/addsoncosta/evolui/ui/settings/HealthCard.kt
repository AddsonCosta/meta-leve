package com.addsoncosta.evolui.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui

/** Ligação com Samsung Health, Strava e outros apps, pelo Health Connect do Android. */
@Composable
fun HealthCard() {
    val context = LocalContext.current
    val container = appContainer()
    val health = container.health
    val settings = container.settings
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var sendWorkouts by remember { mutableStateOf(settings.writeWorkoutsToHealth) }
    val launcher = rememberLauncherForActivityResult(health.permissionContract()) { refresh++ }

    LaunchedEffect(refresh) { granted = health.grantedPermissions() }
    // Ao voltar do Health Connect (permissões mudadas lá), confere de novo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val given = granted.orEmpty()
    val canRead = given.any { it in health.readPermissions }
    val allRead = given.containsAll(health.readPermissions)
    val canWrite = given.containsAll(health.writePermissions)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Panel {
        Text("Samsung Health, Strava e outros apps", style = MaterialTheme.typography.titleMedium)
        Text(
            "O Evolui lê seus treinos, passos, calorias gastas e peso do Samsung Health, do Strava e de outros apps " +
                "que mandam dados para o Health Connect (o \"cofre\" de saúde do Android). O mesmo treino em dois apps conta uma vez só.",
            color = muted,
        )
        when {
            granted == null && health.available -> Text("Verificando...", color = muted)
            canRead -> {
                Text("Conectado ✓", color = Evolui.Neon, fontWeight = FontWeight.Bold)
                Text(
                    "Na tela Hoje aparecem seus passos e calorias, e os treinos dos outros apps entram sozinhos na lista de exercícios. " +
                        "O peso registrado lá também é usado.",
                    color = muted,
                )
                if (!allRead) {
                    Text("Alguns dados não foram liberados (ex.: passos ou peso).", style = MaterialTheme.typography.bodySmall, color = muted)
                    OutlinedButton(onClick = { launcher.launch(health.permissions) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Revisar permissões")
                    }
                }
            }
            health.available -> {
                Button(onClick = { launcher.launch(health.permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
                Text(
                    "Na próxima tela, marque o que o Evolui pode ler. Se preferir, deixe sem a parte de gravar: o app funciona só lendo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            health.needsInstall -> {
                Text("Para conectar, atualize o Health Connect pela Play Store.", color = muted)
                Button(onClick = {
                    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Abrir na Play Store") }
            }
            else -> Text("Este celular não tem o Health Connect.", color = muted)
        }

        if (health.available) {
            Text("Como ligar nos outros apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• Samsung Health: Configurações › Health Connect, e deixe a sincronização ligada.", color = muted)
            Text("• Strava: Configurações › Aplicativos/Dispositivos conectados › Health Connect.", color = muted)
            Text("• Google Fit, Garmin, Fitbit e outros: procure \"Health Connect\" nas configurações do app.", color = muted)

            if (canRead) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Mandar meus treinos do Evolui para o Samsung Health", fontWeight = FontWeight.Bold)
                        Text(
                            "Os exercícios que você registra aqui vão para o Health Connect e aparecem no Samsung Health. " +
                                "Se apagar aqui, apaga lá também.",
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                    Switch(
                        checked = sendWorkouts,
                        onCheckedChange = { on ->
                            sendWorkouts = on
                            settings.writeWorkoutsToHealth = on
                            if (on && !canWrite) launcher.launch(health.writePermissions)
                        },
                    )
                }
                if (sendWorkouts && !canWrite) {
                    Text("Falta liberar a permissão de gravar treinos.", style = MaterialTheme.typography.bodySmall, color = Evolui.Orange)
                    OutlinedButton(onClick = { launcher.launch(health.writePermissions) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Permitir gravar treinos")
                    }
                }
            }

            OutlinedButton(onClick = { openHealthConnect(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir o Health Connect")
            }
        }
    }
}

/** Abre as configurações do Health Connect (o nome da tela muda a partir do Android 14). */
private fun openHealthConnect(context: android.content.Context) {
    val action = if (Build.VERSION.SDK_INT >= 34) "android.health.connect.action.HEALTH_HOME_SETTINGS" else "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
    runCatching { context.startActivity(Intent(action)) }
}
