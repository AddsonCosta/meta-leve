package com.addsoncosta.evolui.ui.badges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.Badge
import com.addsoncosta.evolui.data.BadgeColor
import com.addsoncosta.evolui.data.BadgeEntity
import com.addsoncosta.evolui.data.Badges
import com.addsoncosta.evolui.data.earnedDate
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.share.drawShareBackground
import com.addsoncosta.evolui.ui.share.shareCardColor
import com.addsoncosta.evolui.ui.share.shareImage
import com.addsoncosta.evolui.ui.share.sharePaint
import com.addsoncosta.evolui.ui.share.shareTextOnly
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.theme.Palette
import com.addsoncosta.evolui.ui.theme.Section
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
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

/** Imagem 1080 x 1350 da conquista: fundo do tema, raios, confete e a medalha grande no centro. */
fun renderBadgeImage(badge: Badge, date: LocalDate?, name: String?, palette: Palette = Evolui.palette): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val tier = badge.tier
    val accent = withAlpha(badge.color.argbIn(palette), 255)
    val brand = withAlpha(palette.accent.toArgb(), 255)
    val textC = withAlpha(palette.text.toArgb(), 255)
    val muted = withAlpha(palette.textMuted.toArgb(), 255)

    drawShareBackground(c, w.toFloat(), h.toFloat(), palette, Section.BADGES, artAlpha = 0.5f)

    val cx = w / 2f
    val cy = 500f
    val medalR = 330f

    // Brilho grande atrás da medalha.
    c.drawCircle(cx, cy, 620f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, 620f, intArrayOf(withAlpha(accent, 0x99), withAlpha(accent, 0x33), withAlpha(accent, 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    })

    // Raios de sol girando em volta (cor entre o badge e o metal).
    val rayColor = mix(accent, tier.light, 0.45f)
    val rayLen = 760f
    val rays = Path()
    val count = 22
    for (i in 0 until count) {
        val a = 2 * Math.PI * i / count - Math.PI / 2
        val d = Math.PI / count * 0.55
        rays.moveTo(cx, cy)
        rays.lineTo(cx + rayLen * cos(a - d).toFloat(), cy + rayLen * sin(a - d).toFloat())
        rays.lineTo(cx + rayLen * cos(a + d).toFloat(), cy + rayLen * sin(a + d).toFloat())
        rays.close()
    }
    c.drawPath(rays, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, rayLen, intArrayOf(withAlpha(rayColor, 0x90), withAlpha(rayColor, 0x38), withAlpha(rayColor, 0)), floatArrayOf(0.2f, 0.55f, 1f), Shader.TileMode.CLAMP)
    })

    // Anéis tracejados.
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = withAlpha(rayColor, 0x66)
        pathEffect = DashPathEffect(floatArrayOf(18f, 16f), 0f)
    }
    c.drawCircle(cx, cy, 395f, ring)
    ring.strokeWidth = 2f
    ring.color = withAlpha(rayColor, 0x40)
    c.drawCircle(cx, cy, 445f, ring)

    // Confete nas cores do tema (sempre o mesmo para o mesmo badge).
    val rnd = Random(badge.key.hashCode())
    val confettiColors = intArrayOf(
        accent, tier.light, tier.shine,
        withAlpha(palette.accent2.toArgb(), 255), withAlpha(palette.accent3.toArgb(), 255), withAlpha(palette.accent4.toArgb(), 255),
    )
    val piece = Paint(Paint.ANTI_ALIAS_FLAG)
    var placed = 0
    var tries = 0
    while (placed < 70 && tries < 400) {
        tries++
        val x = rnd.nextFloat() * w
        val y = 140f + rnd.nextFloat() * 720f
        val dx = x - cx
        val dy = y - cy
        if (dx * dx + dy * dy < 370f * 370f) continue
        placed++
        piece.color = withAlpha(confettiColors[rnd.nextInt(confettiColors.size)], 150 + rnd.nextInt(105))
        when (rnd.nextInt(4)) {
            0 -> c.drawCircle(x, y, 5f + rnd.nextFloat() * 6f, piece)
            1 -> c.drawPath(sparklePath(x, y, 12f + rnd.nextFloat() * 14f), piece)
            else -> {
                val pw = 14f + rnd.nextFloat() * 16f
                val ph = 6f + rnd.nextFloat() * 6f
                c.save()
                c.rotate(rnd.nextFloat() * 180f, x, y)
                c.drawRoundRect(RectF(x - pw / 2, y - ph / 2, x + pw / 2, y + ph / 2), 3f, 3f, piece)
                c.restore()
            }
        }
    }

    drawBadgeMedal(c, cx, cy, medalR, badge, earned = true, palette = palette)

    val pad = 72f
    c.drawText("EVOLUI", pad, pad + 60f, sharePaint(56f, brand, palette).apply { letterSpacing = 0.2f })

    // Cartão com o texto, sobre o fundo do tema.
    val card = RectF(pad, 900f, w - pad, 1262f)
    c.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shareCardColor(palette) })
    c.drawRoundRect(card, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = withAlpha(accent, 0x80)
    })

    // Selo do metal ("MEDALHA DE OURO") em cima do cartão.
    val tierText = "MEDALHA DE ${tier.label.uppercase()}"
    val tierPaint = sharePaint(30f, mix(tier.dark, 0xFF000000.toInt(), 0.35f), palette, halo = false).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.14f
    }
    val tw = tierPaint.measureText(tierText)
    val pill = RectF(cx - tw / 2 - 40f, card.top - 34f, cx + tw / 2 + 40f, card.top + 34f)
    c.drawRoundRect(pill, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(pill.left, pill.top, pill.right, pill.bottom, intArrayOf(tier.mid, tier.shine, tier.light, tier.mid), null, Shader.TileMode.CLAMP)
    })
    c.drawRoundRect(pill, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = tier.dark
    })
    c.drawText(tierText, cx, card.top + 11f, tierPaint)

    var y = card.top + 92f
    c.drawText("CONQUISTA DESBLOQUEADA", cx, y, sharePaint(30f, muted, palette, halo = false).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.16f })
    y += 84f
    val title = sharePaint(80f, textC, palette, halo = false).apply { textAlign = Paint.Align.CENTER }
    while (title.measureText(badge.name) > card.width() - 64f && title.textSize > 40f) title.textSize -= 4f
    c.drawText(badge.name, cx, y, title)
    y += 58f
    val desc = sharePaint(34f, textC, palette, bold = false, halo = false).apply { textAlign = Paint.Align.CENTER }
    wrap(badge.description, desc, card.width() - 80f).take(2).forEach { line ->
        c.drawText(line, cx, y, desc)
        y += 46f
    }
    val who = listOfNotNull(name?.trim()?.takeIf { it.isNotEmpty() }, date?.format(DateFormat)).joinToString(" · ")
    if (who.isNotEmpty()) {
        c.drawText(who, cx, card.bottom - 32f, sharePaint(34f, accent.takeIf { contrastOk(it, palette) } ?: brand, palette, halo = false).apply { textAlign = Paint.Align.CENTER })
    }
    c.drawText("Feito com o app Evolui", pad, h - 40f, sharePaint(30f, muted, palette, bold = false))
    return bmp
}

/** A cor do badge só é usada em texto se contrastar com o cartão (temas claros pedem cores mais fortes). */
private fun contrastOk(color: Int, palette: Palette): Boolean {
    fun lum(c: Int) = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
    val bg = lum(withAlpha(palette.surface.toArgb(), 255))
    return kotlin.math.abs(lum(color) - bg) > 0.3
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
    val palette = Evolui.palette
    val bmp = withContext(Dispatchers.Default) { renderBadgeImage(badge, date, name, palette) }
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

/**
 * Medalha do badge (metal, fitas, símbolo e brilho), desenhada uma vez por tema e tamanho
 * e guardada em cache para a grade rolar leve. Bloqueada: em cinza, com cadeado.
 */
@Composable
fun BadgeIcon(badge: Badge, earned: Boolean, size: Int = 56, modifier: Modifier = Modifier) {
    val palette = Evolui.palette
    val px = with(LocalDensity.current) { size.dp.roundToPx() }
    // Desenha fora da thread principal na primeira vez; depois vem direto do cache.
    val image by produceState(cachedMedalImage(badge, earned, palette, px), badge.key, earned, palette, px) {
        value = cachedMedalImage(badge, earned, palette, px) ?: withContext(Dispatchers.Default) { medalImage(badge, earned, palette, px) }
    }
    val description = if (earned) "Medalha de ${badge.tier.label.lowercase()}: ${badge.name}" else "${badge.name} (bloqueada)"
    val img = image
    if (img != null) {
        Image(bitmap = img, contentDescription = description, modifier = modifier.size(size.dp))
    } else {
        Box(modifier.size(size.dp))
    }
}

/** Medalha grande que entra crescendo, com raios girando e um brilho pulsando atrás. */
@Composable
fun CelebrationMedal(badge: Badge, size: Int = 176) {
    val accent = badge.color.themed()
    val metal = Color(badge.tier.light)
    val pop = remember(badge.key) { Animatable(0.3f) }
    LaunchedEffect(badge.key) {
        pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow))
    }
    val transition = rememberInfiniteTransition(label = "comemoracao")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "raios",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "brilho",
    )
    Box(
        Modifier.size(size.dp).drawWithCache {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            val rays = androidx.compose.ui.graphics.Path()
            val n = 16
            for (i in 0 until n) {
                val a = 2 * Math.PI * i / n
                val d = Math.PI / n * 0.5
                rays.moveTo(c.x, c.y)
                rays.lineTo(c.x + r * cos(a - d).toFloat(), c.y + r * sin(a - d).toFloat())
                rays.lineTo(c.x + r * cos(a + d).toFloat(), c.y + r * sin(a + d).toFloat())
                rays.close()
            }
            val rayBrush = Brush.radialGradient(listOf(metal.copy(alpha = 0.75f), accent.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r)
            val glowBrush = Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0f)), center = c, radius = r * 0.62f)
            onDrawBehind {
                rotate(angle, c) { drawPath(rays, rayBrush) }
                scale(pulse, c) { drawCircle(glowBrush, radius = r * 0.62f, center = c) }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        BadgeIcon(
            badge,
            earned = true,
            size = (size * 0.8f).toInt(),
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = ((pop.value - 0.3f) / 0.4f).coerceIn(0f, 1f)
            },
        )
    }
}

/** Aviso comemorativo quando o usuário ganha conquistas novas. */
@Composable
fun BadgeEarnedDialog(earned: List<BadgeEntity>, onDismiss: () -> Unit) {
    val items = earned.mapNotNull { e -> Badges.byKey(e.key)?.let { it to e } }
    if (items.isEmpty()) return
    // A medalha em destaque é a mais rara das novas.
    val (hero, _) = items.maxBy { it.first.tier.ordinal }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { CelebrationMedal(hero) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "MEDALHA DE ${hero.tier.label.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = hero.color.themed(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (items.size == 1) "Nova conquista!" else "${items.size} novas conquistas!",
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { (badge, entity) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BadgeIcon(badge, earned = true, size = 56)
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
