package com.addsoncosta.evolui.i18n

import android.content.Context
import android.util.LruCache
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Idiomas do app (todos de alfabeto latino). O português é o texto original do código. */
enum class AppLanguage(val code: String, val label: String) {
    PT("pt", "Português"),
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    IT("it", "Italiano"),
    ;

    val locale: Locale get() = if (this == PT) Locale.forLanguageTag("pt-BR") else Locale.forLanguageTag(code)

    companion object {
        fun byCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** Idioma do celular, se o app tiver; senão português. */
        fun fromSystem(): AppLanguage = byCode(Locale.getDefault().language) ?: PT
    }
}

/**
 * Tradução dos textos do app. O código é escrito em português; cada idioma tem um dicionário
 * (assets/i18n/<código>.json) do texto em português para o texto traduzido. Textos com valores
 * no meio ("Faltam 300 ml") usam modelos com {} no lugar de cada valor.
 */
object I18n {
    var language by mutableStateOf(AppLanguage.PT)
        private set

    private var exact: Map<String, String> = emptyMap()
    private var templates: Map<String, List<Template>> = emptyMap()
    private val cache = LruCache<String, String>(1500)
    private var appContext: Context? = null

    val locale: Locale get() = language.locale

    /** Ao abrir o app: carrega o idioma salvo (ou o do celular, na primeira vez). */
    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = prefs(context).getString(KEY, null)
        load(context, AppLanguage.byCode(saved) ?: AppLanguage.fromSystem())
    }

    /** Já escolheu o idioma alguma vez? (para perguntar na primeira abertura) */
    fun hasChosen(context: Context): Boolean = prefs(context).contains(KEY)

    fun choose(context: Context, lang: AppLanguage) {
        prefs(context).edit().putString(KEY, lang.code).apply()
        load(context, lang)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("idioma", Context.MODE_PRIVATE)

    private fun load(context: Context, lang: AppLanguage) {
        cache.evictAll()
        if (lang == AppLanguage.PT) {
            exact = emptyMap()
            templates = emptyMap()
        } else {
            val map = runCatching {
                val text = context.assets.open("i18n/${lang.code}.json").bufferedReader().use { it.readText() }
                (Json.parseToJsonElement(text) as JsonObject).mapValues { it.value.jsonPrimitive.content }
            }.getOrElse { emptyMap() }
            exact = map.filterKeys { HOLE !in it }
            templates = map.filterKeys { HOLE in it }
                .map { (k, v) -> Template.of(k, v) }
                .groupBy { it.bucket }
        }
        language = lang
    }

    /** Traduz um texto da interface; o que não estiver no dicionário fica como está. */
    fun tr(text: String): String {
        @Suppress("UNUSED_VARIABLE") val l = language // lido aqui para a tela se redesenhar ao trocar o idioma
        if (exact.isEmpty() && templates.isEmpty()) return text
        if (text.isBlank() || text.none { it.isLetter() }) return text
        cache.get(text)?.let { return it }
        val result = translate(text)
        cache.put(text, result)
        return result
    }

    private fun translate(text: String): String {
        // Mantém os espaços e quebras das pontas (usados para separar de ícones).
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() }
        val core = text.substring(start, end + 1)
        val translated = exact[core] ?: matchTemplate(core) ?: return text
        return text.substring(0, start) + translated + text.substring(end + 1)
    }

    private fun matchTemplate(core: String): String? {
        val candidates = (templates[bucketOf(core)].orEmpty()) + templates[""].orEmpty()
        for (t in candidates) {
            val m = t.regex.matchEntire(core) ?: continue
            // Modelos curtos ("{} de {}", "{} dias") só valem com números, para não mexer em nomes como "Pão de queijo".
            if (t.needsDigits && m.groupValues.drop(1).any { v -> v.none { it.isDigit() } }) continue
            val values = m.groupValues.drop(1).map { v -> exact[v.trim()]?.let { v.replace(v.trim(), it) } ?: v }
            return t.fill(values)
        }
        return null
    }

    /** Primeira palavra (até o primeiro valor), para achar rápido os modelos candidatos. */
    internal fun bucketOf(s: String): String = s.takeWhile { !it.isWhitespace() }.take(12)

    internal const val HOLE = "{}"

    internal class Template(val bucket: String, val regex: Regex, private val target: String, val needsDigits: Boolean) {
        /** Põe os valores no texto traduzido: {} na ordem, ou {0}, {1}... quando a ordem muda. */
        fun fill(values: List<String>): String {
            var i = 0
            val sb = StringBuilder()
            var k = 0
            while (k < target.length) {
                if (target.startsWith("{}", k)) {
                    sb.append(values.getOrElse(i) { "" }); i++; k += 2; continue
                }
                if (target[k] == '{') {
                    val close = target.indexOf('}', k)
                    val n = if (close > k) target.substring(k + 1, close).toIntOrNull() else null
                    if (n != null) {
                        sb.append(values.getOrElse(n) { "" }); k = close + 1; continue
                    }
                }
                sb.append(target[k]); k++
            }
            return sb.toString()
        }

        companion object {
            fun of(source: String, target: String): Template {
                val parts = source.split(HOLE)
                val pattern = parts.joinToString("(.+?)") { Regex.escape(it) }
                val first = source.takeWhile { !it.isWhitespace() }
                val bucket = if (first.contains('{')) "" else first.take(12)
                val letters = parts.sumOf { p -> p.count { it.isLetter() } }
                return Template(bucket, Regex(pattern, RegexOption.DOT_MATCHES_ALL), target, needsDigits = letters < 6)
            }
        }
    }

    private const val KEY = "idioma"
}

/** Atalho: traduz um texto da interface. */
fun tr(text: String): String = I18n.tr(text)

/** Toast já traduzido (troca de Toast.makeText). */
object Toasts {
    fun makeText(context: Context, text: CharSequence, duration: Int): Toast = Toast.makeText(context, tr(text.toString()), duration)
}
