package com.addsoncosta.evolui.ui.score

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.BadgeEntity
import com.addsoncosta.evolui.data.HealthScore
import com.addsoncosta.evolui.data.checkBadges
import com.addsoncosta.evolui.data.healthScoreFlow
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.badges.BadgeEarnedDialog
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.Ring
import com.addsoncosta.evolui.ui.theme.Evolui

/** Cor da nota: verde quando vai bem, laranja no meio, vermelho quando precisa de atenção. */
private fun scoreColor(score: Int): Color = when {
    score >= 70 -> Evolui.Neon
    score >= 40 -> Evolui.Orange
    else -> Evolui.Red
}

/** Nota de autocuidado para a tela Hoje: anel com a nota, rótulo e a dica principal. Tocar mostra as partes. */
@Composable
fun HealthScoreCard(onOpenBadges: () -> Unit, modifier: Modifier = Modifier) {
    val container = appContainer()
    val db = container.database
    val settings = container.settings
    val score by remember { healthScoreFlow(db, settings) }.collectAsStateWithLifecycle<HealthScore?>(initialValue = null)
    val badgeCount by remember { db.badgeDao().observeCount() }.collectAsStateWithLifecycle(initialValue = 0)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fresh by remember { mutableStateOf<List<BadgeEntity>>(emptyList()) }

    // Sempre que os dados mudam (a nota é recalculada), confere se ganhou conquistas novas.
    LaunchedEffect(score) {
        if (score != null) {
            val new = checkBadges(db, settings)
            if (new.isNotEmpty()) fresh = fresh + new
        }
    }

    Panel(modifier.animateContentSize()) {
        val s = score
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val value = s?.score ?: 0
            Ring(value / 100f, scoreColor(value), size = 88.dp, stroke = 9.dp) {
                Text(if (s == null) "…" else "$value", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline("Seu autocuidado · 7 dias")
                Text(s?.label ?: "Calculando...", style = MaterialTheme.typography.titleMedium, color = if (s == null) MaterialTheme.colorScheme.onSurface else scoreColor(value))
                s?.tips?.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Recolher" else "Ver detalhes")
        }
        if (expanded && s != null) ScoreDetails(s)
        FilledTonalButton(onClick = onOpenBadges, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.EmojiEvents, null)
            Text(if (badgeCount > 0) "  Conquistas ($badgeCount)" else "  Conquistas")
        }
    }

    if (fresh.isNotEmpty()) BadgeEarnedDialog(fresh, onDismiss = { fresh = emptyList() })
}

@Composable
private fun ScoreDetails(s: HealthScore) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            "A nota vai de 0 a 100 e olha os últimos 7 dias. O que você não usa (como jejum) não conta e não tira pontos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.components.forEach { c ->
            val v = c.value
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(
                        if (v == null) "não conta" else "${(v * 100).toInt()}% · vale ${s.share(c)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (v != null) {
                    LinearProgressIndicator(
                        progress = { v },
                        modifier = Modifier.fillMaxWidth(),
                        color = scoreColor((v * 100).toInt()),
                    )
                }
                Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (s.tips.isNotEmpty()) {
            Text("O que melhorar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            s.tips.forEach { tip -> Text("• $tip", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
