package com.addsoncosta.evolui.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.addsoncosta.evolui.data.PeriodStats
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.fmt
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Período escolhido para compartilhar. */
sealed class SharePeriod(val label: String) {
    data object Today : SharePeriod("Hoje")
    data object Week : SharePeriod("Últimos 7 dias")
    data object Month : SharePeriod("Últimos 30 dias")
    data class Months(val count: Int) : SharePeriod(if (count == 1) "Último mês" else "Últimos $count meses")

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        Today -> today to today
        Week -> today.minusDays(6) to today
        Month -> today.minusDays(29) to today
        is Months -> today.minusMonths(count.toLong()).plusDays(1) to today
    }
}

enum class ImageShape(val label: String, val width: Int, val height: Int) {
    STORY("Stories / Status", 1080, 1920),
    FEED("Feed", 1080, 1350),
}

/**
 * Uma foto e a legenda que aparece sobre ela (data e peso, por exemplo).
 * [title] é o título pequeno no topo ("ANTES"/"DEPOIS"); [photoId], quando é uma foto de progresso,
 * permite salvar o enquadramento escolhido.
 */
class CaptionedPhoto(
    val bitmap: Bitmap,
    val caption: String,
    val framing: Framing = Framing(),
    val title: String? = null,
    val photoId: Long? = null,
) {
    fun withFraming(f: Framing) = CaptionedPhoto(bitmap, caption, f, title, photoId)
}

// Medidas da imagem gerada, usadas também pelo editor de enquadramento para ter a mesma proporção.
private const val PAD = 72f
private const val PHOTO_GAP = 24f

private fun photoAreaHeight(shape: ImageShape): Float = if (shape == ImageShape.STORY) shape.height * 0.42f else shape.height * 0.36f

/** Proporção (largura / altura) de cada foto na imagem final, com [count] fotos lado a lado. */
fun photoSlotAspect(shape: ImageShape, count: Int): Float {
    val n = count.coerceAtLeast(1)
    val cellW = (shape.width - PAD * 2 - PHOTO_GAP * (n - 1)) / n
    return cellW / photoAreaHeight(shape)
}

private fun signed(kg: Double): String = (if (kg > 0) "+" else if (kg < 0) "−" else "") + abs(kg).fmt(1)

/** Texto simples para WhatsApp, Threads, X ou copiar e colar. */
fun shareText(s: PeriodStats, period: SharePeriod): String = buildString {
    appendLine("💪 Meu progresso no Evolui · ${period.label.lowercase()}")
    val change = s.weightChange
    if (s.startWeight != null && s.endWeight != null && change != null && abs(change) >= 0.05) {
        appendLine("⚖️ Peso: ${s.startWeight.fmt(1)} → ${s.endWeight.fmt(1)} kg (${signed(change)} kg)")
    } else if (s.endWeight != null) {
        appendLine("⚖️ Peso atual: ${s.endWeight.fmt(1)} kg")
    }
    if (s.daysLogged > 0) {
        appendLine("🍽️ ${s.daysLogged} ${if (s.daysLogged == 1) "dia registrado" else "dias registrados"} · média de ${s.avgEaten.fmt()} kcal/dia")
    }
    if (s.workouts > 0) {
        appendLine("🏃 ${s.workouts} ${if (s.workouts == 1) "treino" else "treinos"} · ${minutes(s.workoutMinutes)} · ${s.workoutKcal.fmt()} kcal gastas")
    }
    if (s.fasts > 0) {
        appendLine("⏱️ ${s.fasts} ${if (s.fasts == 1) "jejum" else "jejuns"} · ${s.fastHours.fmt()} h · ${s.fastsOnTarget} com meta batida")
    }
    val target = s.targetWeight
    if (target != null && s.endWeight != null && abs(s.endWeight - target) >= 0.05) {
        appendLine("🎯 Faltam ${abs(s.endWeight - target).fmt(1)} kg para a meta")
    } else if (target != null && s.endWeight != null) {
        appendLine("🎯 Meta alcançada!")
    }
    append("#Evolui")
}

private fun minutes(m: Double): String {
    val total = m.toInt()
    return if (total < 60) "$total min" else "${total / 60}h%02d".format(total % 60)
}

private fun periodDates(s: PeriodStats): String =
    if (s.from == s.to) s.to.format(DateFormat) else "${s.from.format(DateFormat)} a ${s.to.format(DateFormat)}"

/** Desenha a imagem de compartilhamento no visual escuro esportivo. */
fun renderShareImage(s: PeriodStats, period: SharePeriod, shape: ImageShape, photos: List<CaptionedPhoto>): Bitmap {
    val w = shape.width
    val h = shape.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val neon = 0xFF39FF88.toInt()
    val muted = 0xFF9AA8A2.toInt()
    val white = 0xFFF2F5F3.toInt()

    // Fundo escuro com brilho verde no canto.
    c.drawColor(0xFF0B0F0E.toInt())
    val glow = Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h * 0.6f, 0x5539FF88, 0x000B0F0E, Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glow)

    fun text(size: Float, color: Int, bold: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    val pad = PAD
    var y = pad + 60f
    c.drawText("EVOLUI", pad, y, text(56f, neon).apply { letterSpacing = 0.2f })
    y += 56f
    c.drawText("${period.label} · ${periodDates(s)}", pad, y, text(34f, muted, bold = false))
    y += 40f

    // Fotos (uma, ou duas lado a lado para antes e depois).
    if (photos.isNotEmpty()) {
        val area = RectF(pad, y, w - pad, y + photoAreaHeight(shape))
        val gap = PHOTO_GAP
        val cellW = (area.width() - gap * (photos.size - 1)) / photos.size
        photos.forEachIndexed { i, p ->
            val cell = RectF(area.left + i * (cellW + gap), area.top, area.left + i * (cellW + gap) + cellW, area.bottom)
            drawFramed(c, p.bitmap, cell, 36f, p.framing)
            p.title?.let { t -> drawPhotoTitle(c, t, cell, 36f) }
            val label = text(if (photos.size > 1) 30f else 34f, white)
            val tw = label.measureText(p.caption)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB30B0F0E.toInt() }
            c.drawRoundRect(RectF(cell.left + 20f, cell.bottom - 84f, cell.left + 52f + tw, cell.bottom - 20f), 32f, 32f, bg)
            c.drawText(p.caption, cell.left + 36f, cell.bottom - 40f, label)
        }
        y = area.bottom + 40f
    } else {
        y += 40f
    }

    // Destaque: variação de peso.
    val change = s.weightChange
    val headline = when {
        change != null && abs(change) >= 0.05 -> "${signed(change)} kg"
        s.endWeight != null -> "${s.endWeight.fmt(1)} kg"
        else -> "${s.daysLogged} dias"
    }
    val sub = when {
        change != null && abs(change) >= 0.05 -> if (change < 0) "de peso perdido" else "de peso ganho"
        s.endWeight != null -> "peso atual"
        else -> "registrados"
    }
    val big = text(if (photos.isEmpty()) 200f else 150f, neon)
    y += big.textSize * 0.8f
    c.drawText(headline, pad, y, big)
    y += 64f
    c.drawText(sub, pad, y, text(44f, white, bold = false))
    y += 50f

    // Grade de números.
    val tiles = buildList {
        if (s.daysLogged > 0) add(s.avgEaten.fmt() to "kcal/dia em média")
        if (s.workouts > 0) add("${s.workouts}" to if (s.workouts == 1) "treino" else "treinos")
        if (s.workoutKcal > 0) add(s.workoutKcal.fmt() to "kcal em treinos")
        if (s.fasts > 0) add("${s.fasts}" to if (s.fasts == 1) "jejum" else "jejuns")
        val t = s.targetWeight
        if (t != null && s.endWeight != null) add(abs(s.endWeight - t).fmt(1) to "kg para a meta")
        if (isEmpty()) add("${s.daysLogged}" to "dias registrados")
    }.take(4)
    val tileGap = 24f
    val cols = 2
    val tileW = (w - pad * 2 - tileGap) / cols
    val tileH = 190f
    val tileBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A18.toInt() }
    tiles.forEachIndexed { i, (value, label) ->
        val col = i % cols
        val row = i / cols
        val left = pad + col * (tileW + tileGap)
        val top = y + row * (tileH + tileGap)
        if (top + tileH > h - 140f) return@forEachIndexed
        c.drawRoundRect(RectF(left, top, left + tileW, top + tileH), 40f, 40f, tileBg)
        c.drawText(value, left + 36f, top + 100f, text(72f, white))
        c.drawText(label, left + 36f, top + 150f, text(32f, muted, bold = false))
    }

    c.drawText("Feito com o app Evolui", pad, h - 72f, text(32f, muted, bold = false))
    return bmp
}

/** Desenha a foto no retângulo com o enquadramento escolhido, cortando o que sobra, com cantos arredondados. */
internal fun drawFramed(c: Canvas, bmp: Bitmap, dst: RectF, radius: Float, framing: Framing) {
    val r = framing.rectFor(bmp.width, bmp.height, dst.width(), dst.height())
    val target = RectF(dst.left + r.left, dst.top + r.top, dst.left + r.left + r.width, dst.top + r.top + r.height)
    c.save()
    c.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
    c.drawBitmap(bmp, null, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    c.restore()
}

/** Título pequeno ("ANTES") no topo da foto, sobre um degradê leve que não cobre o rosto. */
private fun drawPhotoTitle(c: Canvas, title: String, cell: RectF, radius: Float) {
    val fadeH = 110f
    val fade = Paint().apply {
        shader = LinearGradient(0f, cell.top, 0f, cell.top + fadeH, 0x990B0F0E.toInt(), 0x000B0F0E, Shader.TileMode.CLAMP)
    }
    c.save()
    c.clipPath(Path().apply { addRoundRect(cell, radius, radius, Path.Direction.CW) })
    c.drawRect(cell.left, cell.top, cell.right, cell.top + fadeH, fade)
    c.restore()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = 0xFFF2F5F3.toInt()
        letterSpacing = 0.18f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    c.drawText(title.uppercase(), cell.left + 28f, cell.top + 52f, paint)
}

/** Abre uma foto já reduzida, respeitando a rotação da câmera. */
fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = max(info.size.width, info.size.height)
            if (big > maxSide) {
                val f = maxSide.toFloat() / big
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}.getOrNull()

fun loadBitmap(context: Context, file: File, maxSide: Int = 1600): Bitmap? = loadBitmap(context, Uri.fromFile(file), maxSide)

/** Abre a lista de apps do celular para compartilhar a imagem (com o texto junto). */
fun shareImage(context: Context, bitmap: Bitmap, text: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "evolui.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

fun shareTextOnly(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar"))
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Evolui", text))
}

/** Quantos dias o período cobre (para mostrar na tela). */
fun daysIn(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to) + 1

internal fun clampMonths(n: Int) = min(24, max(1, n))
