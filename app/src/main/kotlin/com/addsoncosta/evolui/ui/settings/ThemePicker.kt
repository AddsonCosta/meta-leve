package com.addsoncosta.evolui.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.theme.Palette
import com.addsoncosta.evolui.ui.theme.Palettes

/** Escolha do tema de cores. A troca vale na hora, no app inteiro. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker() {
    val settings = appContainer().settings
    val current by settings.theme.collectAsStateWithLifecycle()
    val selected = Palettes.byKey(current)

    Panel {
        Text("Aparência", style = MaterialTheme.typography.titleMedium)
        Text(
            "Escolha as cores do app. Você pode trocar quando quiser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Palettes.all.forEach { palette ->
                ThemeSwatch(palette, palette.key == selected.key) {
                    settings.setTheme(palette.key)
                    Evolui.palette = palette
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(palette.accent, palette.accent2, palette.accent3).forEach {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(it))
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check, null, tint = palette.onAccent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(palette.accent),
                )
            }
        }
        Text(
            palette.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
