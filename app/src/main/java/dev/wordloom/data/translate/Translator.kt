package dev.wordloom.data.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Youdao first, its own dictionary and translator, so a selected word comes back
 * with the part-of-speech line the site shows. If the network is gone, ML Kit's
 * on-device model takes over (English → Chinese, downloaded once, works offline).
 * The UI only depends on [translate].
 */
object TranslationService {

    private val cache = ConcurrentHashMap<String, String>()
    private var client: Translator? = null

    fun init() {
        if (client == null) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build()
            client = Translation.getClient(options)
        }
    }

    suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext Result.success("")
        cache[cleaned]?.let { return@withContext Result.success(it) }

        val online = YoudaoTranslator.translate(cleaned)
        if (online.isSuccess) {
            val value = online.getOrThrow()
            cache[cleaned] = value
            return@withContext Result.success(value)
        }

        runCatching {
            val translator = client ?: error("翻译未初始化")
            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().build(),
            ).await()
            translator.translate(cleaned).await()
        }.onSuccess { cache[cleaned] = it }
    }

    fun close() {
        client?.close()
        client = null
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
