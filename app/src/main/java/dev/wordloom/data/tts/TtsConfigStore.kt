package dev.wordloom.data.tts

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The TTS endpoint and key live on this phone only, encrypted with a key that
 * never leaves Android Keystore. Nothing here is written to BuildConfig,
 * backups or logs; a release APK therefore contains no credentials.
 */
class TtsConfigStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "tts.encrypted"))

    fun load(): TtsConfig? = synchronized(lock) {
        if (!file.baseFile.isFile && !File(file.baseFile.path + ".bak").isFile) return null
        runCatching {
            val bytes = file.readFully()
            if (bytes.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
            )
            val text = String(
                cipher.doFinal(bytes.copyOfRange(12, bytes.size)),
                Charsets.UTF_8,
            )
            TtsConfig.decode(text)
        }.getOrNull()
    }

    fun save(config: TtsConfig, allowKeyless: Boolean = false) = synchronized(lock) {
        if (!allowKeyless) {
            require(config.enabled) { "AI 朗读配置不完整，请检查接口、模型和密钥" }
        } else {
            require(config.endpoint.isNotBlank() && config.model.isNotBlank()) {
                "AI 朗读配置不完整，请检查接口和模型"
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(TtsConfig.encode(config).toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun clear() = synchronized(lock) {
        file.delete()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val ALIAS = "WordloomTts"
        val lock = Any()
    }
}
