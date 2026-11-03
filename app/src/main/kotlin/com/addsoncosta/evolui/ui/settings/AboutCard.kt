package com.addsoncosta.evolui.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.addsoncosta.evolui.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.BuildConfig
import com.addsoncosta.evolui.ui.components.KeyValue
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.update.UpdateChecker
import com.addsoncosta.evolui.update.UpdateResult
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://github.com/AddsonCosta/meta-leve/blob/main/docs/privacidade.md"

/** "Sobre": versão do app, fontes dos dados e privacidade. */
@Composable
fun AboutCard() {
    val context = LocalContext.current
    Panel {
        Text("Sobre o Evolui", style = MaterialTheme.typography.titleMedium)
        KeyValue("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        KeyValue("Seus dados", "Ficam só neste celular")
        KeyValue("Loja", "Galaxy Store (Samsung)")
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            "O Evolui acompanha alimentação, treinos, jejum, peso e saúde. O gasto diário é calculado pela equação de " +
                "Mifflin-St Jeor e pelo nível de atividade; os treinos usam os valores MET do Compendium of Physical Activities " +
                "ajustados ao seu gasto em repouso.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Alimentos: Tabela Brasileira de Composição de Alimentos (TACO), 4ª edição, NEPA/Unicamp, com complementos de " +
                "tabelas brasileiras de medidas caseiras e composição de alimentos.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Este app não substitui orientação médica, de nutricionista ou de outro profissional de saúde.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UpdateButton()
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Política de privacidade") }
    }
}

/** "Procurar atualização": pergunta à Galaxy Store se há versão nova. */
@Composable
private fun UpdateButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    Button(
        onClick = {
            checking = true
            scope.launch {
                result = UpdateChecker.check(context.applicationContext)
                checking = false
            }
        },
        enabled = !checking,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (checking) "Procurando…" else "Procurar atualização") }
    when (val r = result) {
        is UpdateResult.Available -> {
            Text("Há uma versão nova: ${r.versionName ?: "toque para ver"}.", color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { UpdateChecker.openStore(context) }, modifier = Modifier.fillMaxWidth()) { Text("Atualizar na Galaxy Store") }
        }
        UpdateResult.UpToDate -> Text("Você já tem a versão mais nova.", style = MaterialTheme.typography.bodySmall)
        UpdateResult.Unknown -> {
            Text(
                "Não deu para verificar agora (sem internet ou loja fora do ar). Você pode abrir a página do Evolui na loja.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { UpdateChecker.openStore(context) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir na Galaxy Store") }
        }
        null -> Unit
    }
}
