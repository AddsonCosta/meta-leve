package com.addsoncosta.evolui.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Leitura de fotos e documentos pela IA que o usuário conectou ("Minha IA").
 * Só envia algo quando chamado, e só para a empresa escolhida, com a chave do usuário.
 */
class AiDocumentReader(context: Context) {
    private val store = AiConfigStore(context)

    fun isConnected(): Boolean = store.isConnected()

    /** Ex.: "Claude (Anthropic)"; nulo se não houver IA conectada. */
    fun providerName(): String? = store.summary()?.let { "${it.provider.label} (${it.provider.company})" }

    /**
     * Manda as imagens (e o texto lido no aparelho, se houver) com as [instructions] do chamador e devolve
     * o objeto JSON da resposta. Os erros vêm como [AiException] com mensagem pronta para mostrar.
     */
    suspend fun readJson(images: List<Bitmap>, ocrText: String?, instructions: String): Result<JsonObject> {
        val config = withContext(Dispatchers.IO) { store.load() } ?: return Result.failure(
            AiException(
                AiErrorKind.NOT_CONNECTED,
                if (store.isConnected()) "Não consegui abrir a chave guardada. Conecte sua IA de novo em Configurações > Minha IA."
                else "Conecte sua IA em Configurações > Minha IA.",
            )
        )
        return try {
            val encoded = withContext(Dispatchers.Default) { images.map { AiImages.jpegBase64(it) } }
            val text = buildString {
                append(instructions.trim())
                val ocr = ocrText?.trim().orEmpty()
                if (ocr.isNotEmpty()) {
                    append("\n\nTexto lido no aparelho (pode ter erros de leitura):\n")
                    append(ocr.take(20_000))
                }
            }
            val answer = AiClient.complete(config, AiClient.SYSTEM_JSON, text, encoded)
            val obj = AiJson.extractObject(answer) ?: throw AiWire.badFormat()
            Result.success(obj)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiException(AiErrorKind.OTHER, "Não deu para falar com a IA agora. Tente de novo."))
        }
    }

    /** Foto do prato: alimentos, porções, kcal e macros, já conferidos. */
    suspend fun readPlate(photo: Bitmap): Result<PlateAnalysis> =
        readJson(listOf(photo), null, PlatePrompt.text).fold(
            onSuccess = { obj -> PlateAiParser.parse(obj)?.let { Result.success(it) } ?: Result.failure(AiWire.badFormat()) },
            onFailure = { Result.failure(it) },
        )
}
