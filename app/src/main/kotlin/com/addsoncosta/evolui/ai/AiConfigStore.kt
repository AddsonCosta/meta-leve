package com.addsoncosta.evolui.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** O que dá para mostrar da conexão sem abrir a chave. */
data class AiSummary(val provider: AiProvider, val model: String, val customBaseUrl: String?, val keyTail: String)

/**
 * Guarda a conexão "Minha IA". A chave fica criptografada (AES/GCM) com uma chave do Android Keystore, que não
 * sai do aparelho. O arquivo de preferências se chama exatamente [PREFS] e fica fora do backup do Android.
 */
class AiConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConnected(): Boolean = summary() != null

    fun summary(): AiSummary? {
        val provider = AiProvider.byName(prefs.getString(K_PROVIDER, null)) ?: return null
        if (prefs.getString(K_KEY, null).isNullOrEmpty()) return null
        return AiSummary(
            provider = provider,
            model = prefs.getString(K_MODEL, null).orEmpty().ifBlank { provider.defaultModel },
            customBaseUrl = prefs.getString(K_BASE_URL, null),
            keyTail = prefs.getString(K_TAIL, null).orEmpty(),
        )
    }

    /** A conexão com a chave aberta, ou nulo se não houver (ou se o Keystore não abrir mais a chave). */
    fun load(): AiConfig? {
        val s = summary() ?: return null
        val key = runCatching { decrypt(prefs.getString(K_KEY, null).orEmpty()) }.getOrNull() ?: return null
        return AiConfig(s.provider, key, s.model, s.customBaseUrl)
    }

    fun save(config: AiConfig) {
        val key = config.key.trim()
        prefs.edit()
            .putString(K_PROVIDER, config.provider.name)
            .putString(K_MODEL, config.model.trim())
            .putString(K_BASE_URL, config.customBaseUrl?.trim()?.ifEmpty { null })
            .putString(K_KEY, encrypt(key))
            .putString(K_TAIL, key.takeLast(4))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val enc = Base64.getEncoder()
        return enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(stored: String): String? {
        val parts = stored.split(':')
        if (parts.size != 2) return null
        val dec = Base64.getDecoder()
        val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, dec.decode(parts[0])))
        return cipher.doFinal(dec.decode(parts[1])).toString(Charsets.UTF_8)
    }

    companion object {
        /** Nome do arquivo de preferências; o backup exclui este arquivo. Não renomear. */
        const val PREFS = "minha_ia"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "evolui_minha_ia"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val K_PROVIDER = "provider"
        private const val K_MODEL = "model"
        private const val K_BASE_URL = "base_url"
        private const val K_KEY = "key"
        private const val K_TAIL = "key_tail"
    }
}
