package com.addsoncosta.evolui.ai

import com.addsoncosta.evolui.ai.AiJson.field
import com.addsoncosta.evolui.ai.AiJson.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class AiErrorKind { NOT_CONNECTED, INVALID_KEY, NO_INTERNET, TIMEOUT, LIMIT, CREDITS, MODEL, BAD_FORMAT, SERVER, OTHER }

/** Erro da IA com mensagem pronta para mostrar ao usuário. */
class AiException(val kind: AiErrorKind, message: String) : Exception(message)

/** Pedido HTTP pronto (sem rede, para dar para testar). */
data class AiHttpRequest(val url: String, val headers: Map<String, String>, val body: String) {
    // A chave vai nos cabeçalhos; nunca aparece em toString.
    override fun toString(): String = "AiHttpRequest(url=${url.substringBefore('?')})"
}

/** Monta os pedidos e lê as respostas de cada API. Sem Android, testável em JUnit. */
object AiWire {
    const val ANTHROPIC_VERSION = "2023-06-01"

    fun request(config: AiConfig, system: String, userText: String, imagesBase64: List<String>, maxTokens: Int = 4096): AiHttpRequest {
        val model = config.effectiveModel
        val base = config.baseUrl
        return when (config.provider.protocol) {
            AiProtocol.ANTHROPIC -> AiHttpRequest(
                url = "$base/messages",
                headers = mapOf(
                    "x-api-key" to config.key.trim(),
                    "anthropic-version" to ANTHROPIC_VERSION,
                    "content-type" to "application/json",
                ),
                body = buildJsonObject {
                    put("model", model)
                    put("max_tokens", maxTokens)
                    put("system", system)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("content") {
                                imagesBase64.forEach { img ->
                                    addJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", "image/jpeg")
                                            put("data", img)
                                        }
                                    }
                                }
                                addJsonObject {
                                    put("type", "text")
                                    put("text", userText)
                                }
                            }
                        }
                    }
                }.toString(),
            )
            AiProtocol.GEMINI -> AiHttpRequest(
                url = "$base/models/${model.removePrefix("models/")}:generateContent",
                headers = mapOf("x-goog-api-key" to config.key.trim(), "content-type" to "application/json"),
                body = buildJsonObject {
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") { addJsonObject { put("text", system) } }
                    }
                    putJsonArray("contents") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                imagesBase64.forEach { img ->
                                    addJsonObject {
                                        putJsonObject("inlineData") {
                                            put("mimeType", "image/jpeg")
                                            put("data", img)
                                        }
                                    }
                                }
                                addJsonObject { put("text", userText) }
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.2)
                        // Os modelos 2.5 "pensam" antes e isso conta neste limite.
                        put("maxOutputTokens", maxOf(maxTokens, 8192))
                    }
                }.toString(),
            )
            AiProtocol.OPENAI -> {
                val official = config.provider == AiProvider.CHATGPT
                AiHttpRequest(
                    url = "$base/chat/completions",
                    headers = buildMap {
                        put("Authorization", "Bearer ${config.key.trim()}")
                        put("content-type", "application/json")
                        if (config.provider == AiProvider.OPENROUTER) {
                            put("HTTP-Referer", "https://github.com/AddsonCosta/meta-leve")
                            put("X-Title", "Evolui")
                        }
                    },
                    body = buildJsonObject {
                        put("model", model)
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "system")
                                put("content", system)
                            }
                            addJsonObject {
                                put("role", "user")
                                if (imagesBase64.isEmpty()) {
                                    put("content", userText)
                                } else {
                                    putJsonArray("content") {
                                        imagesBase64.forEach { img ->
                                            addJsonObject {
                                                put("type", "image_url")
                                                putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$img") }
                                            }
                                        }
                                        addJsonObject {
                                            put("type", "text")
                                            put("text", userText)
                                        }
                                    }
                                }
                            }
                        }
                        if (official) {
                            // Modelos novos da OpenAI só aceitam max_completion_tokens e a temperatura padrão.
                            put("max_completion_tokens", maxTokens)
                            putJsonObject("response_format") { put("type", "json_object") }
                        } else {
                            put("max_tokens", maxTokens)
                            put("temperature", 0.2)
                        }
                    }.toString(),
                )
            }
        }
    }

    /** O texto da resposta de sucesso, ou erro de formato. */
    fun responseText(protocol: AiProtocol, body: String): String {
        val obj = runCatching { AiJson.json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw badFormat()
        val text = runCatching {
            when (protocol) {
                AiProtocol.ANTHROPIC -> obj["content"]?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { (it["type"] as? JsonPrimitive)?.content == "text" }
                    .joinToString("") { it["text"].text().orEmpty() }
                AiProtocol.GEMINI -> obj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { (it["thought"] as? JsonPrimitive)?.content != "true" }
                    .joinToString("") { it["text"].text().orEmpty() }
                AiProtocol.OPENAI -> {
                    val content = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject?.get("content")
                    when (content) {
                        is JsonArray -> content.mapNotNull { it as? JsonObject }.joinToString("") { it["text"].text().orEmpty() }
                        else -> content.text().orEmpty()
                    }
                }
            }
        }.getOrDefault("")
        if (text.isBlank()) throw badFormat()
        return text
    }

    fun badFormat() = AiException(AiErrorKind.BAD_FORMAT, "A IA respondeu fora do formato esperado. Tente de novo.")

    /** Mensagem do erro devolvido pela API (sem a chave, se ela aparecer). */
    fun providerMessage(body: String, key: String): String? {
        val obj = runCatching { AiJson.json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val err = obj["error"]
        val msg = when (err) {
            is JsonObject -> err.field("message").text()
            else -> err.text() ?: obj.field("message").text()
        } ?: return null
        val k = key.trim()
        return (if (k.length >= 6) msg.replace(k, "•••") else msg).take(200)
    }

    /** Erro de HTTP traduzido para o usuário. */
    fun error(status: Int, body: String, config: AiConfig): AiException {
        val p = config.provider
        val raw = providerMessage(body, config.key)
        val m = raw.orEmpty().lowercase()
        val code = runCatching {
            (AiJson.json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.field("code", "type", "status").text()
        }.getOrNull().orEmpty().lowercase()
        fun has(vararg words: String) = words.any { m.contains(it) || code.contains(it) }
        val credits = AiException(
            AiErrorKind.CREDITS,
            "Sem créditos na sua conta ${p.ofCompany}. Adicione créditos no site de desenvolvedor e tente de novo.",
        )
        val invalidKey = AiException(
            AiErrorKind.INVALID_KEY,
            "Chave inválida ou sem permissão. Confira se copiou a chave inteira, criada no site de desenvolvedor ${p.ofCompany}.",
        )
        val model = AiException(
            AiErrorKind.MODEL,
            "O modelo \"${config.effectiveModel}\" não foi encontrado ou não aceita fotos. Confira o nome do modelo.",
        )
        return when {
            status == 401 || status == 403 -> invalidKey
            status == 402 || has("insufficient_quota", "credit balance", "credits", "billing", "insufficient balance") -> credits
            status == 429 -> AiException(
                AiErrorKind.LIMIT,
                "Limite de uso atingido ${p.atCompany}. Espere alguns minutos ou confira os limites da sua conta lá.",
            )
            has("api key not valid", "api_key_invalid", "invalid api key", "invalid x-api-key", "incorrect api key") -> invalidKey
            status == 404 || has("model_not_found", "does not exist", "not found", "invalid model", "unknown model", "not support image", "does not support image") -> model
            status >= 500 -> AiException(AiErrorKind.SERVER, "O serviço ${p.ofCompany} está com problema agora. Tente mais tarde.")
            else -> AiException(
                AiErrorKind.OTHER,
                "O serviço ${p.ofCompany} recusou o pedido (erro $status)" + (raw?.let { ": $it" } ?: "."),
            )
        }
    }
}
