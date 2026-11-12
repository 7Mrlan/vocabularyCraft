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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class StudyExample(val en: String, val zh: String, val audioUrl: String = "")

data class StudyDetails(
    val senses: List<String> = emptyList(),
    val examples: List<StudyExample> = emptyList(),
    val phrases: List<Pair<String, String>> = emptyList(),
)

/**
 * The Chinese side of a study card, taken from the same public dictionary page
 * the site uses: part-of-speech lines, bilingual example sentences and common
 * phrases. Fetched once per word and kept in memory for the session; the English
 * side always comes from our own entry.
 */
object StudyContent {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, StudyDetails>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun details(word: String): StudyDetails? = withContext(Dispatchers.IO) {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return@withContext null
        cache[key] ?: try {
            fetch(key)?.also { cache[key] = it }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private fun fetch(word: String): StudyDetails? {
        val url = "https://dict.youdao.com/jsonapi?q=" + URLEncoder.encode(word, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://dict.youdao.com/")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return null
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null

        val senses = root["ec"]?.jsonObject?.get("word")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("trs")?.jsonArray
            ?.flatMap { it.jsonObject["tr"]?.jsonArray ?: JsonArray(emptyList()) }
            ?.mapNotNull { tr ->
                when (val item = tr.jsonObject["l"]?.jsonObject?.get("i")) {
                    is JsonArray -> item.joinToString("") { it.jsonPrimitive.content }
                    null -> null
                    else -> item.jsonPrimitive.content
                }
            }
            .orEmpty()

        val examples = root["blng_sents_part"]?.jsonObject
            ?.get("sentence-pair")?.jsonArray
            ?.mapNotNull { pair ->
                val obj = pair.jsonObject
                val en = obj["sentence"]?.jsonPrimitive?.content?.trim().orEmpty()
                val zh = obj["sentence-translation"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (en.isEmpty() || zh.isEmpty()) {
                    null
                } else {
                    StudyExample(en, zh, speechUrl(en))
                }
            }
            ?.distinctBy { it.en }
            ?.take(8)
            .orEmpty()

        val phrases = root["phrs"]?.jsonObject
            ?.get("phrs")?.jsonArray
            ?.mapNotNull { item ->
                val phr = item.jsonObject["phr"]?.jsonObject ?: return@mapNotNull null
                val head = phr["headword"]?.jsonObject?.get("l")?.jsonObject
                    ?.get("i")?.jsonPrimitive?.content.orEmpty()
                val meaning = phr["trs"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("tr")?.jsonObject?.get("l")?.jsonObject
                    ?.get("i")?.jsonPrimitive?.content.orEmpty()
                if (head.isEmpty() || meaning.isEmpty()) null else head to meaning
            }
            ?.take(6)
            .orEmpty()

        return StudyDetails(senses = senses, examples = examples, phrases = phrases)
    }

    private fun speechUrl(sentence: String): String =
        "https://dict.youdao.com/dictvoice?audio=" + URLEncoder.encode(sentence, "UTF-8") + "&type=2"

    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
