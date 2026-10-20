package com.addsoncosta.evolui.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.ui.share.loadBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Leitura de texto de fotos e PDFs, feita no próprio celular (ML Kit). */
object Ocr {
    private const val MAX_PDF_PAGES = 5
    private const val PDF_WIDTH = 1600

    /**
     * Lê o texto do arquivo. Devolve nulo quando a leitura não está disponível no aparelho
     * ou não encontrou nada; o app segue normalmente sem o texto.
     */
    suspend fun read(context: Context, file: File, mimeType: String): String? = withContext(Dispatchers.Default) {
        val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull()
            ?: return@withContext null
        try {
            val pages: List<String> = if (mimeType == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)) {
                readPdf(file, recognizer)
            } else {
                val bmp = withContext(Dispatchers.IO) { loadBitmap(context, file, 2000) } ?: return@withContext null
                listOfNotNull(recognize(recognizer, bmp))
            }
            pages.joinToString("\n\n").trim().ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { recognizer.close() }
        }
    }

    /**
     * As páginas do arquivo como imagens (para mandar à IA do usuário): a foto reduzida, ou as
     * primeiras [maxPages] páginas do PDF. Lista vazia se não conseguir abrir.
     */
    suspend fun pageImages(context: Context, file: File, mimeType: String, maxPages: Int = 3, maxSide: Int = 1600): List<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (mimeType == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        try {
                            (0 until min(renderer.pageCount, maxPages)).map { i ->
                                renderer.openPage(i).use { page ->
                                    val scale = maxSide.toFloat() / page.width.coerceAtLeast(1)
                                    val h = (page.height * scale).toInt().coerceIn(1, maxSide * 3)
                                    val b = Bitmap.createBitmap(maxSide, h, Bitmap.Config.ARGB_8888)
                                    b.eraseColor(Color.WHITE)
                                    page.render(b, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    b
                                }
                            }
                        } finally {
                            renderer.close()
                        }
                    }
                } else {
                    listOfNotNull(loadBitmap(context, file, maxSide))
                }
            }.getOrDefault(emptyList())
        }

    private suspend fun readPdf(file: File, recognizer: TextRecognizer): List<String> {
        val out = mutableListOf<String>()
        val fd = withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
        fd.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                val count = min(renderer.pageCount, MAX_PDF_PAGES)
                for (i in 0 until count) {
                    val bmp = renderer.openPage(i).use { page ->
                        val scale = PDF_WIDTH.toFloat() / page.width.coerceAtLeast(1)
                        val w = PDF_WIDTH
                        val h = (page.height * scale).toInt().coerceIn(1, PDF_WIDTH * 3)
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        val m = Matrix().apply { setScale(scale, scale) }
                        page.render(b, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                    recognize(recognizer, bmp)?.let { out += it }
                    bmp.recycle()
                }
            } finally {
                renderer.close()
            }
        }
        return out
    }

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return rows(result).ifBlank { null }
    }

    /**
     * Junta as linhas que estão na mesma altura da página (nome do exame à esquerda, valor à direita),
     * para que cada linha da tabela do laudo fique numa linha de texto.
     */
    private fun rows(result: Text): String {
        class L(val text: String, val left: Int, val cy: Int, val h: Int)
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                L(line.text, box.left, box.centerY(), box.height().coerceAtLeast(1))
            }
        }.sortedBy { it.cy }
        if (lines.isEmpty()) return result.text
        val groups = mutableListOf<MutableList<L>>()
        for (l in lines) {
            val g = groups.lastOrNull()
            if (g != null && abs(g.first().cy - l.cy).toDouble() < g.first().h * 0.6) g.add(l) else groups.add(mutableListOf(l))
        }
        return groups.joinToString("\n") { g -> g.sortedBy { it.left }.joinToString("   ") { it.text } }
    }
}

/** Um valor encontrado no laudo. */
data class FoundValue(val item: LabItem, val value: Double, val line: String)

/** O que foi possível achar no texto do arquivo. */
data class ParsedExam(val date: LocalDate?, val lab: String?, val values: List<FoundValue>) {
    val isEmpty get() = date == null && lab == null && values.isEmpty()
}

/** Procura data, laboratório e valores do catálogo no texto lido. O usuário confere antes de salvar. */
object ExamParser {
    private val knownLabs = listOf(
        "Fleury", "Dasa", "Delboni", "Lavoisier", "Hermes Pardini", "Pardini", "Sabin", "DB Diagnósticos", "Labi",
        "Sérgio Franco", "Bronstein", "Lâmina", "Richet", "Frischmann Aisengart", "Alta Diagnósticos", "a+ Medicina",
        "Amaral Costa", "Unimed", "Hapvida", "São Marcos", "Weinmann", "Carlos Chagas", "Diagnósticos da América",
        "Laboratório Oswaldo Cruz", "Cerpe", "Lustosa", "Bioclínico", "Biocor", "LabCenter", "Laboclin",
    )

    private val dateRegex = Regex("""(\d{1,2})[/.-](\d{1,2})[/.-](\d{4}|\d{2})""")
    private val numberRegex = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:[.,]\d+)?""")

    private class Pattern(val item: LabItem, val synonym: String, val regex: Regex)

    private fun patterns(items: List<LabItem>): List<Pattern> = items.flatMap { item ->
        (item.synonyms + Repository.normalize(item.name)).distinct().map { syn ->
            Pattern(item, syn, Regex("(?<![a-z0-9])" + Regex.escape(syn) + "(?![a-z0-9])"))
        }
    }

    fun parse(text: String, items: List<LabItem>, usedLabs: List<String> = emptyList()): ParsedExam {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val normLines = rawLines.map { Repository.normalize(it) }
        val pats = patterns(items)
        val found = LinkedHashMap<String, FoundValue>()
        normLines.forEachIndexed { i, line ->
            // O sinônimo mais longo que aparece na linha vence (ex.: "hemoglobina glicada" antes de "hemoglobina").
            var best: Pair<Pattern, MatchResult>? = null
            for (p in pats) {
                val m = p.regex.find(line) ?: continue
                val b = best
                if (b == null || p.synonym.length > b.first.synonym.length) best = p to m
            }
            val (p, m) = best ?: return@forEachIndexed
            if (found.containsKey(p.item.name)) return@forEachIndexed
            val rest = line.substring(m.range.last + 1)
            val gap = rest.indexOfFirst { it.isDigit() }
            if (gap < 0 || gap > 45) return@forEachIndexed
            // Ignora o que parece data no lugar do valor.
            val num = numberRegex.find(rest, gap) ?: return@forEachIndexed
            val after = rest.substring(num.range.last + 1).take(1)
            if (after == "/") return@forEachIndexed
            val v = toNumber(num.value, p.item) ?: return@forEachIndexed
            found[p.item.name] = FoundValue(p.item, v, rawLines[i])
        }
        return ParsedExam(findDate(normLines), findLab(rawLines, usedLabs), found.values.toList())
    }

    /** Converte "14,2", "4.500" ou "1.020" de acordo com o item. */
    fun toNumber(s: String, item: LabItem?): Double? {
        val t = s.trim()
        if (t.contains(',')) return t.replace(".", "").replace(',', '.').toDoubleOrNull()
        val lastDot = t.lastIndexOf('.')
        if (lastDot >= 0 && t.length - lastDot - 1 == 3) {
            val big = (item?.high ?: item?.low ?: 0.0) > 500
            return if (big) t.replace(".", "").toDoubleOrNull() else t.toDoubleOrNull()
        }
        return t.toDoubleOrNull()
    }

    private fun findDate(lines: List<String>): LocalDate? {
        val today = LocalDate.now()
        fun dates(line: String) = dateRegex.findAll(line).mapNotNull { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }.filter { it.year >= 1990 && !it.isAfter(today) }
        val keywords = listOf("colet", "data do exame", "realizado", "atendimento", "data:")
        for (line in lines) if (keywords.any { line.contains(it) }) dates(line).firstOrNull()?.let { return it }
        // Sem palavra-chave: a data mais recente do laudo (a de nascimento costuma ser antiga).
        return lines.flatMap { dates(it).toList() }.maxOrNull()
    }

    private fun findLab(lines: List<String>, usedLabs: List<String>): String? {
        val all = (usedLabs + knownLabs).distinct()
        val head = lines.take(40)
        for (lab in all) {
            val n = Repository.normalize(lab)
            if (n.length < 3) continue
            val r = Regex("(?<![a-z])" + Regex.escape(n) + "(?![a-z])")
            if (head.any { r.containsMatchIn(Repository.normalize(it)) }) return lab
        }
        val line = head.firstOrNull { Repository.normalize(it).contains("laboratorio") } ?: return null
        return line.take(60).trim()
    }
}
