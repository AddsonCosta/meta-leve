package com.addsoncosta.evolui.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.i18n.AppLanguage
import com.addsoncosta.evolui.i18n.I18n
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.i18n.Text

/** Configurações › Idioma. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker() {
    val context = LocalContext.current
    Panel {
        Text("Idioma", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                // O nome de cada idioma fica sempre na própria língua (não passa pela tradução).
                FilterChip(
                    selected = I18n.language == lang,
                    onClick = { I18n.choose(context, lang) },
                    label = { androidx.compose.material3.Text(lang.label) },
                )
            }
        }
        Text(
            "Os nomes dos alimentos da tabela TACO continuam em português.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Primeira abertura: escolher o idioma antes do cadastro. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstLanguageScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary)
        Text("Escolha o idioma", style = MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = I18n.language == lang,
                    onClick = { I18n.choose(context, lang) },
                    label = { androidx.compose.material3.Text(lang.label) },
                )
            }
        }
        Text(
            "Dá para mudar depois em Configurações.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                I18n.choose(context, I18n.language)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Continuar", fontWeight = FontWeight.Bold) }
    }
}
