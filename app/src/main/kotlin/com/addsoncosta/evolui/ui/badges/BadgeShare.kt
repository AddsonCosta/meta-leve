package com.addsoncosta.evolui.ui.badges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.Badge
import com.addsoncosta.evolui.data.BadgeColor
import com.addsoncosta.evolui.data.BadgeEntity
import com.addsoncosta.evolui.data.Badges
import com.addsoncosta.evolui.data.earnedDate
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.share.shareImage
import com.addsoncosta.evolui.ui.share.shareTextOnly
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cor do badge no tema atual. */
fun BadgeColor.themed(): Color = when (this) {
    BadgeColor.NEON -> Evolui.Neon
    BadgeColor.CYAN -> Evolui.Cyan
    BadgeColor.ORANGE -> Evolui.Orange
    BadgeColor.PURPLE -> Evolui.Purple
    BadgeColor.RED -> Evolui.Red
}

/** Texto para WhatsApp, Threads, X... */
fun badgeShareText(badge: Badge, date: LocalDate?, name: String?): String = buildString {
    appendLine("${badge.emoji} ${if (name.isNullOrBlank()) "Conquistei" else "${name.trim()} conquistou"}: ${badge.name}!")
    appendLine(badge.description)
    date?.let { appendLine("📅 ${it.format(DateFormat)}") }
    append("#Evolui")
}

/** Imagem 1080 x 1350 da conquista, no mesmo visual escuro das outras imagens do app. */
fun renderBadgeImage(badge: Badge, date: LocalDate?, name: String?): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val accent = badge.color.argb
    val muted = 0xFF9AA8A2.toInt()
    val white = 0xFFF2F5F3.toInt()
    val accentGlow = (accent and 0x00FFFFFF) or 0x66000000
    val accentNone = accent and 0x00FFFFFF

    c.drawColor(0xFF0B0F0E.toInt())
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h * 0.7f, accentGlow, 0x000B0F0E, Shader.TileMode.CLAMP)
    })

    fun text(size: Float, color: Int, bold: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val pad = 72f
    c.drawText("EVOLUI", pad, pad + 60f, text(56f, accent).apply { letterSpacing = 0.2f })

    // Medalha: brilho, anel e o símbolo no centro.
    val cx = w / 2f
    val cy = 520f
    c.drawCircle(cx, cy, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, 330f, accentGlow, accentNone, Shader.TileMode.CLAMP)
    })
    c.drawCircle(cx, cy, 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A18.toInt() })
    c.drawCircle(cx, cy, 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = accent
    })
    val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 210f
        color = white
        textAlign = Paint.Align.CENTER
    }
    c.drawText(badge.emoji, cx, cy - (emoji.descent() + emoji.ascent()) / 2f, emoji)

    var y = 860f
    c.drawText("CONQUISTA DESBLOQUEADA", cx, y, text(34f, muted).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.16f })
    y += 100f
    val title = text(88f, white).apply { textAlign = Paint.Align.CENTER }
    while (title.measureText(badge.name) > w - pad * 2 && title.textSize > 40f) title.textSize -= 4f
    c.drawText(badge.name, cx, y, title)
    y += 70f
    val desc = text(38f, white, bold = false).apply { textAlign = Paint.Align.CENTER }
    wrap(badge.description, desc, w - pad * 2).take(3).forEach { line ->
        c.drawText(line, cx, y, desc)
        y += 50f
    }
    y += 20f
    val who = listOfNotNull(name?.trim()?.takeIf { it.isNotEmpty() }, date?.format(DateFormat)).joinToString(" · ")
    if (who.isNotEmpty()) {
        val p = text(36f, accent).apply { textAlign = Paint.Align.CENTER }
        val tw = p.measureText(who)
        c.drawRoundRect(RectF(cx - tw / 2 - 36f, y - 50f, cx + tw / 2 + 36f, y + 22f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A18.toInt() })
        c.drawText(who, cx, y, p)
    }
    c.drawText("Feito com o app Evolui", pad, h - 72f, text(32f, muted, bold = false))
    return bmp
}

/** Quebra o texto em linhas que cabem na largura. */
private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(" ")) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) line = candidate
        else {
            lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

/** Gera a imagem fora da thread principal e abre o compartilhar do celular. */
suspend fun shareBadgeImage(context: Context, badge: Badge, date: LocalDate?, name: String?) {
    val bmp = withContext(Dispatchers.Default) { renderBadgeImage(badge, date, name) }
    shareImage(context, bmp, badgeShareText(badge, date, name))
}

/** Nome do perfil (para a imagem), lido em segundo plano. */
@Composable
fun rememberProfileName(): String? {
    val db = appContainer().database
    val name by produceState<String?>(null) { value = withContext(Dispatchers.IO) { db.profileDao().get()?.name } }
    return name
}

/** Botões de compartilhar um badge como imagem ou como texto, com opção de mostrar o nome. */
@Composable
fun BadgeShareButtons(badge: Badge, date: LocalDate?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileName = rememberProfileName()
    var withName by rememberSaveable { mutableStateOf(true) }
    val name = profileName.takeIf { withName }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!profileName.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = withName, onCheckedChange = { withName = it })
                Text("Mostrar meu nome ($profileName)", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { scope.launch { shareBadgeImage(context, badge, date, name) } }) {
                Icon(Icons.Filled.Image, null)
                Text("  Imagem")
            }
            OutlinedButton(onClick = { shareTextOnly(context, badgeShareText(badge, date, name)) }) {
                Icon(Icons.Filled.Share, null)
                Text("  Texto")
            }
        }
    }
}

/** Ícone redondo e colorido do badge. */
@Composable
fun BadgeIcon(badge: Badge, earned: Boolean, size: Int = 56) {
    val color = if (earned) badge.color.themed() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(
        Modifier.size(size.dp).background(color.copy(alpha = if (earned) 0.18f else 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(badge.icon, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Aviso comemorativo quando o usuário ganha conquistas novas. */
@Composable
fun BadgeEarnedDialog(earned: List<BadgeEntity>, onDismiss: () -> Unit) {
    val items = earned.mapNotNull { e -> Badges.byKey(e.key)?.let { it to e } }
    if (items.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Badges.celebrationIcon, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (items.size == 1) "Nova conquista!" else "${items.size} novas conquistas!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { (badge, entity) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BadgeIcon(badge, earned = true, size = 48)
                            Column(Modifier.fillMaxWidth()) {
                                Text(badge.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(badge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        BadgeShareButtons(badge, entity.earnedDate())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
