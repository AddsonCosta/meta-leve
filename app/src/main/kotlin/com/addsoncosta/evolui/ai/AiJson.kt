package com.addsoncosta.evolui.ai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Leitura tolerante do JSON que as IAs devolvem (com ```json, texto antes ou depois, vírgula sobrando). */
object AiJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        allowComments = true
    }

    /** O primeiro objeto JSON válido do texto, ou nulo. */
    fun extractObject(text: String?): JsonObject? {
        if (text.isNullOrBlank()) return null
        val candidates = buildList {
            // Blocos de código primeiro (```json ... ```), depois o texto inteiro.
            Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").findAll(text).forEach { add(it.groupValues[1]) }
            add(text)
        }
        for (c in candidates) {
            parseObject(c.trim())?.let { return it }
            for (start in c.indices) {
                if (c[start] != '{') continue
                val end = matchingBrace(c, start) ?: continue
                parseObject(c.substring(start, end + 1))?.let { return it }
            }
        }
        return null
    }

    private fun parseObject(s: String): JsonObject? =
        if (s.startsWith("{")) runCatching { json.parseToJsonElement(s) as? JsonObject }.getOrNull() else null

    /** Índice da chave que fecha a aberta em [start], ignorando chaves dentro de textos. */
    private fun matchingBrace(s: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val ch = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    /** O primeiro campo presente entre os nomes aceitos (sem diferenciar maiúsculas). */
    fun JsonObject.field(vararg names: String): JsonElement? {
        for (n in names) {
            this[n]?.let { if (it !is JsonNull) return it }
        }
        val lower = entries.associate { it.key.lowercase() to it.value }
        for (n in names) {
            lower[n.lowercase()]?.let { if (it !is JsonNull) return it }
        }
        return null
    }

    /** Número de um campo: aceita 80, "80", "80 g", "80,5". */
    fun JsonElement?.number(): Double? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (!p.isString) return p.content.toDoubleOrNull()?.takeIf { it.isFinite() }
        val m = Regex("-?\\d+(?:[.,]\\d+)?").find(p.content) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Texto de um campo (nulo se vazio ou não for texto/número). */
    fun JsonElement?.text(): String? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content.trim().takeIf { it.isNotEmpty() }
    }

    fun JsonElement?.array(): JsonArray? = this as? JsonArray
}
