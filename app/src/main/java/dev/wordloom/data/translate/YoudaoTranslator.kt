package dev.wordloom.data.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Youdao's own web endpoints, the ones fanyi.youdao.com and dict.youdao.com call:
 * the dictionary for a word (part-of-speech lines, the way the site shows them)
 * and the demo translator for a phrase or a sentence. No key, no login.
 */
object YoudaoTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
        val query = text.trim()
        if (query.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("没有选中内容"))
        }
        runCatching {
            val word = query.split(Regex("\\s+")).size == 1 && query.all { it.isLetter() || it == '-' }
            val result = if (word) dictionary(query) ?: sentence(query) else sentence(query)
            result?.takeIf { it.isNotBlank() } ?: error("有道没有返回译文")
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://fanyi.youdao.com/")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    /** "n. 融合，结合；核聚变…" — the dictionary's own line for the word. */
    private fun dictionary(word: String): String? {
        val body = get("https://dict.youdao.com/jsonapi?q=" + URLEncoder.encode(word, "UTF-8")) ?: return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val entries = root["ec"]?.jsonObject?.get("word")?.jsonArray ?: return null
        val lines = entries.firstOrNull()?.jsonObject?.get("trs")?.jsonArray?.flatMap { tr ->
            tr.jsonObject["tr"]?.jsonArray ?: JsonArray(emptyList())
        }?.mapNotNull { tr ->
            val item = tr.jsonObject["l"]?.jsonObject?.get("i") ?: return@mapNotNull null
            if (item is JsonArray) item.joinToString("") { it.jsonPrimitive.content } else item.jsonPrimitive.content
        }.orEmpty()
        return lines.joinToString("；").takeIf { it.isNotBlank() }
    }

    /** The demo translator: plain text in, plain text out. */
    private fun sentence(text: String): String? {
        val url = "https://aidemo.youdao.com/trans?q=" + URLEncoder.encode(text, "UTF-8") + "&from=Auto&to=Auto"
        val body = get(url) ?: return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val translation = root["translation"]?.jsonArray ?: return null
        return translation.joinToString("") { it.jsonPrimitive.content }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
