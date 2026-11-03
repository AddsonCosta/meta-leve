package com.addsoncosta.evolui.update

import androidx.compose.material3.AlertDialog
import com.addsoncosta.evolui.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Ao abrir o app (no máximo uma vez por dia): se houver versão nova na Galaxy Store, avisa. */
@Composable
fun UpdatePromptOnOpen() {
    val context = LocalContext.current
    var found by remember { mutableStateOf<UpdateResult.Available?>(null) }
    LaunchedEffect(Unit) {
        if (UpdateChecker.shouldCheckOnOpen(context)) {
            found = UpdateChecker.check(context.applicationContext) as? UpdateResult.Available
        }
    }
    found?.let { r ->
        AlertDialog(
            onDismissRequest = { found = null },
            title = { Text("Nova versão do Evolui") },
            text = { Text("A versão ${r.versionName ?: "nova"} já está na Galaxy Store, com correções e melhorias.") },
            confirmButton = {
                TextButton(onClick = {
                    found = null
                    UpdateChecker.openStore(context)
                }) { Text("Atualizar") }
            },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Depois") } },
        )
    }
}
