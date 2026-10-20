package com.addsoncosta.evolui.ai

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Base64
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Chamada às APIs de IA com HttpURLConnection (sem bibliotecas novas). Nunca registra a chave em log. */
object AiClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 120_000

    /** System line: um único objeto JSON, sem texto em volta. */
    const val SYSTEM_JSON =
        "Você é um assistente que analisa fotos e documentos para um app brasileiro de saúde. " +
            "Responda somente com um único objeto JSON válido, sem nenhum texto antes ou depois e sem blocos de código markdown. " +
            "Use números sem unidades. Quando não souber um valor, use null."

    /** Envia o pedido e devolve o texto da resposta. Erros viram [AiException] com mensagem em português. */
    suspend fun complete(config: AiConfig, system: String, userText: String, imagesBase64: List<String>): String {
        checkConfig(config)
        val req = AiWire.request(config, system, userText, imagesBase64)
        val (status, body) = post(req)
        if (status !in 200..299) throw AiWire.error(status, body, config)
        return AiWire.responseText(config.provider.protocol, body)
    }

    /** Pedido pequeno, só texto, para conferir chave e modelo. */
    suspend fun test(config: AiConfig) {
        val text = complete(config, SYSTEM_JSON, "Teste de conexão. Responda exatamente com o objeto JSON {\"ok\": true}.", emptyList())
        if (AiJson.extractObject(text) == null) throw AiWire.badFormat()
    }

    private fun checkConfig(config: AiConfig) {
        if (config.key.isBlank()) throw AiException(AiErrorKind.NOT_CONNECTED, "Cole a chave de API.")
        if (config.effectiveModel.isBlank()) throw AiException(AiErrorKind.MODEL, "Escreva o nome do modelo.")
        if (!config.baseUrl.startsWith("https://")) {
            throw AiException(AiErrorKind.OTHER, "O endereço da API precisa começar com https://")
        }
    }

    private suspend fun post(req: AiHttpRequest): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            runInterruptible {
                val c = URL(req.url).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = CONNECT_TIMEOUT_MS
                    c.readTimeout = READ_TIMEOUT_MS
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.useCaches = false
                    c.setRequestProperty("Accept", "application/json")
                    req.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                    val bytes = req.body.toByteArray(Charsets.UTF_8)
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                    val status = c.responseCode
                    val stream = if (status in 200..299) c.inputStream else c.errorStream
                    val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                    status to text
                } finally {
                    c.disconnect()
                }
            }
        } catch (e: SocketTimeoutException) {
            throw AiException(AiErrorKind.TIMEOUT, "A IA demorou demais para responder. Tente de novo.")
        } catch (e: UnknownHostException) {
            throw AiException(AiErrorKind.NO_INTERNET, "Sem internet. Confira a conexão e tente de novo.")
        } catch (e: SSLException) {
            throw AiException(AiErrorKind.NO_INTERNET, "Não foi possível conectar com segurança. Confira a internet e a data do celular.")
        } catch (e: IOException) {
            throw AiException(AiErrorKind.NO_INTERNET, "Sem internet ou conexão interrompida. Tente de novo.")
        }
    }
}

/** Fotos para a IA: JPEG em base64, com o lado maior em até [maxSide] px. */
object AiImages {
    fun jpegBase64(bitmap: Bitmap, maxSide: Int = 1024, quality: Int = 80): String {
        val side = max(bitmap.width, bitmap.height)
        val scaled = if (side > maxSide) {
            val f = maxSide.toFloat() / side
            Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * f).roundToInt()), max(1, (bitmap.height * f).roundToInt()), true)
        } else {
            bitmap
        }
        val soft = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
        val out = ByteArrayOutputStream()
        soft.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (soft !== bitmap && soft !== scaled) soft.recycle()
        if (scaled !== bitmap) scaled.recycle()
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
