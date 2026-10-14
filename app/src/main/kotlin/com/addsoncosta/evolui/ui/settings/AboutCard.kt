package com.addsoncosta.evolui.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.BuildConfig
import com.addsoncosta.evolui.ui.components.KeyValue
import com.addsoncosta.evolui.ui.components.Panel

private const val PRIVACY_URL = "https://github.com/AddsonCosta/meta-leve/blob/main/docs/privacidade.md"

/** "Sobre": versão do app, fontes dos dados e privacidade. */
@Composable
fun AboutCard() {
    val context = LocalContext.current
    Panel {
        Text("Sobre o Evolui", style = MaterialTheme.typography.titleMedium)
        KeyValue("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        KeyValue("Seus dados", "Ficam só neste celular")
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
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Política de privacidade") }
    }
}
