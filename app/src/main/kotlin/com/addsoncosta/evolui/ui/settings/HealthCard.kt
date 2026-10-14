package com.addsoncosta.evolui.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui

/** Ligação com o Samsung Health, pelo Health Connect do Android. */
@Composable
fun HealthCard() {
    val context = LocalContext.current
    val health = appContainer().health
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(health.permissionContract()) { refresh++ }

    LaunchedEffect(refresh) { connected = health.hasPermissions() }

    Panel {
        Text("Samsung Health", style = MaterialTheme.typography.titleMedium)
        when {
            connected == true -> {
                Text("Conectado ✓", color = Evolui.Neon)
                Text(
                    "Na tela Hoje aparecem seus passos e calorias, e os treinos do Samsung Health entram sozinhos na lista de exercícios. O peso registrado lá também é usado.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            health.available -> {
                Text(
                    "Traga passos, calorias gastas, treinos e peso do Samsung Health. No Samsung Health, abra Configurações › Health Connect e deixe a sincronização ligada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { launcher.launch(health.permissions) }, modifier = Modifier.fillMaxWidth()) { Text("Conectar") }
            }
            health.needsInstall -> {
                Text("Para conectar, atualize o Health Connect pela Play Store.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Abrir na Play Store") }
            }
            else -> Text("Este celular não tem o Health Connect.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
