package com.addsoncosta.evolui.ui.badges

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.data.BadgeEntity
import com.addsoncosta.evolui.data.BadgeStatus
import com.addsoncosta.evolui.data.badgeStatuses
import com.addsoncosta.evolui.data.checkBadges
import com.addsoncosta.evolui.data.earnedDate
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.components.fmt

private enum class BadgeFilter(val label: String) { ALL("Todas"), EARNED("Conquistadas"), LOCKED("Faltam") }

/** Conquistas: as ganhas coloridas, com data; as que faltam em cinza, com o progresso. */
@Composable
fun BadgesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val container = appContainer()
    val db = container.database
    val settings = container.settings
    val earnedRows by remember { db.badgeDao().observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var statuses by remember { mutableStateOf<List<BadgeStatus>?>(null) }
    var fresh by remember { mutableStateOf<List<BadgeEntity>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf(BadgeFilter.ALL) }
    var open by remember { mutableStateOf<BadgeStatus?>(null) }

    // Confere conquistas novas ao abrir e recalcula quando a lista de ganhas muda.
    LaunchedEffect(Unit) { fresh = checkBadges(db, settings) }
    LaunchedEffect(earnedRows) { statuses = badgeStatuses(db, settings) }

    val all = statuses
    val earnedCount = all?.count { it.earned != null } ?: 0
    val shown = all.orEmpty().filter {
        when (filter) {
            BadgeFilter.ALL -> true
            BadgeFilter.EARNED -> it.earned != null
            BadgeFilter.LOCKED -> it.earned == null
        }
    }.sortedByDescending { if (it.earned != null) 2f else it.fraction }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) {
                    Overline("Gamificação")
                    Text("Conquistas", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Panel {
                Text(
                    if (all == null) "Calculando..." else "$earnedCount de ${all.size} conquistas",
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { if (all.isNullOrEmpty()) 0f else earnedCount.toFloat() / all.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Registre refeições, treinos, água, jejuns, fotos e cuidados de saúde para ganhar medalhas. Toque numa conquista para ver detalhes e compartilhar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (all == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (filter == BadgeFilter.EARNED) "Você ainda não tem conquistas. Comece registrando uma refeição na aba Comer: é a primeira medalha!"
                    else "Você ganhou todas as conquistas. Parabéns!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { it.badge.key }) { st -> BadgeTile(st, onClick = { open = st }) }
    }

    open?.let { st -> BadgeDetailDialog(st, onDismiss = { open = null }) }
    if (fresh.isNotEmpty()) BadgeEarnedDialog(fresh, onDismiss = { fresh = emptyList() })
}

@Composable
private fun BadgeTile(st: BadgeStatus, onClick: () -> Unit) {
    val earned = st.earned != null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (earned) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                BadgeIcon(st.badge, earned)
                if (!earned) Icon(Icons.Filled.Lock, "Bloqueada", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(0.dp))
            }
            Text(
                st.badge.name,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (earned) {
                Text(
                    st.earned!!.earnedDate().format(DateFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = st.badge.color.themed(),
                )
            } else {
                LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
                Text(missing(st), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** "Faltam 3 dias", "Faltam 40 min"... */
private fun missing(st: BadgeStatus): String {
    val left = (st.target - st.current).coerceAtLeast(0.0)
    val unit = st.badge.unit
    if (unit.isEmpty() || st.target <= 1.0 && unit != "kg") return "Ainda não"
    val decimals = if (unit == "kg" || unit == "h") 1 else 0
    return "Faltam ${left.fmt(decimals)} $unit · ${st.current.fmt(decimals)}/${st.target.fmt(decimals)}"
}

@Composable
private fun BadgeDetailDialog(st: BadgeStatus, onDismiss: () -> Unit) {
    val earned = st.earned
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { BadgeIcon(st.badge, earned != null, size = 72) },
        title = { Text(st.badge.name, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(st.badge.description)
                if (earned != null) {
                    Text("Conquistada em ${earned.earnedDate().format(DateFormat)}", fontWeight = FontWeight.Bold, color = st.badge.color.themed())
                    Text("Compartilhar", style = MaterialTheme.typography.labelLarge)
                    BadgeShareButtons(st.badge, earned.earnedDate())
                } else {
                    LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
                    Text(missing(st), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
