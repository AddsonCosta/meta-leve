package com.addsoncosta.evolui.ui.health.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Pede a permissão de notificações (Android 13+) na primeira vez que a pessoa escolhe um lembrete,
 * explicando antes o porquê. Devolve a função a chamar quando um lembrete for escolhido.
 */
@Composable
internal fun rememberNotificationAsker(): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) showDenied = true
    }

    if (showWhy) {
        AlertDialog(
            onDismissRequest = { showWhy = false },
            title = { Text("Permitir avisos?") },
            text = { Text("Para lembrar você da consulta, do exame ou do remédio na hora certa, o Evolui precisa mostrar notificações. Na próxima tela, toque em \"Permitir\".") },
            confirmButton = {
                TextButton(onClick = {
                    showWhy = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { showWhy = false; showDenied = true }) { Text("Agora não") } },
        )
    }
    if (showDenied) {
        AlertDialog(
            onDismissRequest = { showDenied = false },
            title = { Text("Avisos desligados") },
            text = { Text("Sem a permissão, o lembrete fica salvo, mas o aviso não aparece. Para ligar depois: Configurações do celular > Aplicativos > Evolui > Notificações.") },
            confirmButton = { TextButton(onClick = { showDenied = false }) { Text("Entendi") } },
        )
    }

    return {
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs && !asked) {
            asked = true
            showWhy = true
        }
    }
}
