package com.addsoncosta.evolui.backup

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Panel
import java.io.File
import kotlinx.coroutines.launch

/** Arquivo de e-mail montado, aguardando a pessoa decidir (estava acima do limite do Gmail). */
private data class TooBig(val file: File)

/**
 * Seção Backup das Configurações: Google Drive automático (pelo seletor de arquivos do Android),
 * enviar por e-mail, restaurar e a lista de fotos/anexos que não vieram no último backup restaurado.
 */
@Composable
fun BackupSection() {
    val container = appContainer()
    val repo = container.repository
    val backup = container.backup
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by backup.state.collectAsState()
    val missing by repo.missingFiles.items.collectAsState()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var emailBusy by remember { mutableStateOf(false) }
    var tooBig by remember { mutableStateOf<TooBig?>(null) }
    var reattaching by remember { mutableStateOf<OmittedFile?>(null) }

    val chooseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupRules.MIME)) { uri ->
        if (uri != null) {
            if (backup.chooseTarget(uri)) {
                backup.setAuto(true)
                backup.backupNow()
                Toast.makeText(context, "Local escolhido. Fazendo o primeiro backup…", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Não foi possível usar este local. Tente escolher de novo.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }
    val reattachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val item = reattaching
        reattaching = null
        if (uri != null && item != null) scope.launch {
            val ok = repo.reattachFile(item, uri)
            Toast.makeText(context, if (ok) "Arquivo anexado de novo" else "Não foi possível ler o arquivo", Toast.LENGTH_SHORT).show()
        }
    }

    fun openEmail(file: File, includeFiles: Boolean) {
        try {
            context.startActivity(backup.emailIntent(file, includeFiles))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nenhum app de e-mail encontrado", Toast.LENGTH_LONG).show()
        }
    }

    fun sendEmail(includeFiles: Boolean) {
        if (emailBusy) return
        emailBusy = true
        scope.launch {
            val file = runCatching { backup.buildEmailFile(includeFiles) }.getOrNull()
            emailBusy = false
            when {
                file == null -> Toast.makeText(context, "Não foi possível montar o backup", Toast.LENGTH_SHORT).show()
                includeFiles && BackupRules.tooBigForEmail(file.length()) -> tooBig = TooBig(file)
                else -> openEmail(file, includeFiles)
            }
        }
    }

    Panel {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Seus dados ficam só neste celular. Faça um backup para não perder nada se trocar ou perder o aparelho.",
            color = muted,
        )

        // Google Drive automático
        Text("Google Drive automático", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "No seletor que abrir, toque no menu e escolha Google Drive (e a pasta). O Evolui regrava esse mesmo arquivo todo dia.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        val target = state.target
        if (target == null) {
            Button(onClick = { chooseLauncher.launch(BackupRules.DRIVE_FILE_NAME) }, modifier = Modifier.fillMaxWidth()) {
                Text("Escolher local no Google Drive")
            }
        } else {
            state.targetName?.let { Text("Arquivo: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Backup automático")
                    Text("Uma vez por dia, com fotos e anexos", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Switch(checked = state.auto, onCheckedChange = { backup.setAuto(it) })
            }
            Text(
                state.lastMillis?.let { "Último backup: ${BackupRules.formatMoment(it)}" } ?: "Ainda não foi feito nenhum backup neste local.",
                fontWeight = FontWeight.SemiBold,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.needsNewLocation) {
                Button(onClick = { chooseLauncher.launch(BackupRules.DRIVE_FILE_NAME) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Escolher local de novo")
                }
            }
            OutlinedButton(
                onClick = { backup.backupNow() },
                enabled = !state.running && !state.needsNewLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Fazendo backup…")
                } else {
                    Text("Fazer backup agora")
                }
            }
            if (!state.needsNewLocation) {
                TextButton(onClick = { chooseLauncher.launch(BackupRules.DRIVE_FILE_NAME) }) { Text("Trocar o local do backup") }
            }
        }

        HorizontalDivider()

        // E-mail
        Text("Enviar por e-mail", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Gera o arquivo de backup e abre o Gmail (ou outro app de e-mail) com ele anexado. Nada passa por servidor do Evolui.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        OutlinedButton(onClick = { sendEmail(includeFiles = true) }, enabled = !emailBusy, modifier = Modifier.fillMaxWidth()) {
            if (emailBusy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Montando o arquivo…")
            } else {
                Text("Enviar por e-mail")
            }
        }

        HorizontalDivider()

        // Restaurar
        Text("Restaurar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Escolha o arquivo de backup no Google Drive ou no celular. Recebeu por e-mail? Salve o anexo antes (no Drive ou em Downloads).",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "*/*")) },
            enabled = !restoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (restoring) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Restaurando…")
            } else {
                Text("Restaurar backup")
            }
        }
    }

    if (missing.isNotEmpty()) {
        Panel {
            Text("Arquivos que não vieram no backup (${missing.size})", style = MaterialTheme.typography.titleMedium)
            Text(
                "O backup restaurado foi feito sem fotos e anexos. Os dados continuam aqui; se tiver os arquivos, anexe de novo.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            missing.forEach { item ->
                MissingFileNotice(
                    item = item,
                    onAttachAgain = {
                        reattaching = item
                        reattachLauncher.launch(if (item.isPhoto) arrayOf("image/*") else arrayOf("application/pdf", "image/*"))
                    },
                    onDismiss = { repo.missingFiles.remove(item.fileName) },
                )
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restaurar backup?") },
            text = { Text("Seus dados atuais (perfil, registros, saúde, alimentos e exercícios próprios) serão substituídos pelos do arquivo.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    restoring = true
                    scope.launch {
                        val ok = runCatching {
                            repo.importBackupFile { context.contentResolver.openInputStream(uri)!! }
                        }.isSuccess
                        restoring = false
                        val msg = when {
                            !ok -> "Arquivo de backup inválido"
                            repo.missingFiles.items.value.isNotEmpty() -> "Backup restaurado. Algumas fotos e anexos não vieram no arquivo; veja a lista em Backup."
                            else -> "Backup restaurado"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") } },
        )
    }

    tooBig?.let { big ->
        AlertDialog(
            onDismissRequest = { tooBig = null },
            title = { Text("Arquivo grande demais para o e-mail") },
            text = {
                Text(
                    "O backup ficou com ${BackupRules.formatSize(big.file.length())}, e o Gmail aceita até 25 MB. " +
                        "Você pode enviar só os dados: no lugar de cada foto e anexo fica uma descrição, para anexar de novo depois.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tooBig = null
                    sendEmail(includeFiles = false)
                }) { Text("Enviar sem fotos e anexos") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        tooBig = null
                        openEmail(big.file, includeFiles = true)
                    }) { Text("Enviar mesmo assim") }
                    TextButton(onClick = { tooBig = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

/**
 * Aviso de uma foto ou anexo que não veio no backup: "Anexo não incluído no backup: <descrição>",
 * com botão para anexar de novo. Também pode ser usado na tela do registro.
 */
@Composable
fun MissingFileNotice(item: OmittedFile, onAttachAgain: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (if (item.isPhoto) "Foto não incluída no backup: " else "Anexo não incluído no backup: ") + item.description(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAttachAgain) { Text(if (item.isPhoto) "Adicionar a foto de novo" else "Anexar de novo") }
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Dispensar") }
        }
    }
}
