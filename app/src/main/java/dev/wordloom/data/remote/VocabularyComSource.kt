package dev.wordloom.data.remote

import dev.wordloom.data.model.AudioClip
import dev.wordloom.data.model.Pronunciation
import dev.wordloom.data.model.Sense
import dev.wordloom.data.model.Suggestion
import dev.wordloom.data.model.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class VocabularyComSource(
    private val client: OkHttpClient = defaultClient(),
    private val noFollowClient: OkHttpClient = defaultClient().newBuilder()
        .followRedirects(false)
        .build(),
    private val fallback: WiktionarySource = WiktionarySource(client),
) : DictionarySource {

    override val id: String = "vocabulary_com"
    override val displayName: String = "Vocabulary.com"

    override suspend fun lookup(term: String): WordEntry? = withContext(Dispatchers.IO) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return@withContext null

        // A failed Vocabulary.com request still goes to the fallback, but a
        // cancelled coroutine must stay cancelled instead of starting a second
        // network call.
        val vocabEntry = attempt { fetchFromVocab(cleaned) }.getOrNull()
        if (vocabEntry != null && vocabEntry.hasContent) {
            return@withContext vocabEntry
        }

        // Fallback to Wiktionary if Vocabulary.com doesn't have the word
        fallback.lookup(cleaned)
    }

    /**
     * The site's own smart-search box calls `dictionary/autocomplete-ss` and renders
     * the returned HTML fragment; we parse the same fragment so suggestions carry the
     * one-line gloss the reference UI shows under each word.
     */
    override suspend fun suggest(prefix: String): List<Suggestion> = withContext(Dispatchers.IO) {
        val cleaned = prefix.trim()
        if (cleaned.isEmpty()) return@withContext emptyList()

        // Try the site's own search first, then Wiktionary. A request that failed
        // is not the same as a request that answered "nothing", so keep the
        // failures and only report empty once at least one source actually replied.
        val primary = attempt { suggestFromSmartSearch(cleaned) }
        primary.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        val fallbackResult = attempt { fallback.suggest(cleaned) }
        fallbackResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        if (primary.isSuccess || fallbackResult.isSuccess) return@withContext emptyList()
        throw primary.exceptionOrNull() ?: fallbackResult.exceptionOrNull()!!
    }

    /** [runCatching] without swallowing cancellation; the caller still rethrows it. */
    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun random(): String? = withContext(Dispatchers.IO) {
        // /randomword 302s to /dictionary/{word}; read the Location instead of following.
        attempt {
            val request = Request.Builder()
                .url("https://www.vocabulary.com/randomword")
                .header("User-Agent", USER_AGENT)
                .build()
            noFollowClient.newCall(request).execute().use { resp ->
                val location = resp.header("Location").orEmpty()
                location.substringAfterLast("/dictionary/", "").takeIf { it.isNotBlank() }
            }
        }.getOrNull() ?: fallback.random()
    }

    private fun suggestFromSmartSearch(prefix: String): List<Suggestion> {
        val encoded = URLEncoder.encode(prefix, "UTF-8")
        val url = "https://www.vocabulary.com/dictionary/autocomplete-ss?search=$encoded&maxResults=8"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.vocabulary.com/dictionary/$encoded")
            .build()

        val body = client.newCall(request).execute().use { resp ->
            // A 4xx/5xx is a failed lookup, not "no matches"; let suggest() decide
            // whether the fallback source can still answer.
            if (!resp.isSuccessful) error("HTTP ${resp.code} for autocomplete")
            resp.body?.string().orEmpty()
        }
        val doc = Jsoup.parse(body)
        return doc.select("div.word-result").mapNotNull { row ->
            val word = row.selectFirst(".word")?.text()?.trim().orEmpty()
            if (word.isEmpty()) return@mapNotNull null
            Suggestion(
                word = word,
                definition = row.selectFirst(".definition")?.text()?.trim().orEmpty(),
            )
        }
    }

    private fun fetchFromVocab(word: String): WordEntry? {
        val encoded = URLEncoder.encode(word, "UTF-8")
        val url = "https://www.vocabulary.com/dictionary/definition.ajax?search=$encoded&lang=en"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val responseBody = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }

        if (!responseBody.contains("wordPage")) return null

        val doc = Jsoup.parse(responseBody)
        val wordAttr = doc.selectFirst(".wordPage")?.attr("data-word")?.takeIf { it.isNotBlank() } ?: word

        // 1. Other forms: <p class="word-forms">Other forms: <b>quicker; quickest; quicks</b></p>
        val forms = doc.selectFirst("p.word-forms b")?.text()?.trim()

        // 2. Short & Long blurb
        val shortBlurb = doc.selectFirst("p.short")?.text()?.trim()
        val longBlurb = doc.selectFirst("p.long")?.text()?.trim()

        // 3. Pronunciation & Audio
        val ipaList = mutableListOf<Pronunciation>()
        val audioList = mutableListOf<AudioClip>()

        doc.select(".ipa-with-audio").forEach { block ->
            val isUk = block.selectFirst(".uk-flag-icon") != null
            val regLabel = if (isUk) "UK" else "US"
            val ipaText = block.selectFirst(".span-replace-h3")?.text()?.trim()
            if (!ipaText.isNullOrEmpty() && ipaList.none { it.text == ipaText }) {
                ipaList.add(Pronunciation(regLabel, ipaText))
            }
            val audioKey = block.selectFirst("a[data-audio]")?.attr("data-audio")?.takeIf { it.isNotBlank() }
            if (audioKey != null) {
                val regPath = if (isUk) "uk" else "us"
                val audioUrl = "https://audio.vocabulary.com/1.0/$regPath/$audioKey.mp3"
                if (audioList.none { it.url == audioUrl }) {
                    audioList.add(AudioClip(regLabel, audioUrl))
                }
            }
        }

        // 4. Senses
        val senses = doc.select("li.sense").mapNotNull { li ->
            val pos = li.selectFirst(".pos-icon")?.text()?.trim().orEmpty()
            val defEl = li.selectFirst(".definition") ?: return@mapNotNull null
            val defClone = defEl.clone()
            defClone.select(".pos-icon").remove()
            val defText = defClone.text().trim()
            if (defText.isEmpty()) return@mapNotNull null

            val examples = li.select(".example").map { it.text().trim() }
            val synonymGroups = li.select(".instances")
                .filter { it.selectFirst(".detail")?.text()?.contains("synonym", ignoreCase = true) == true }
            val synonyms = synonymGroups
                .flatMap { inst -> inst.select("a.word").map { it.text().trim() } }
                .distinct()
            // "type of:" is the hypernym line the reference page also shows.
            val hypernyms = li.select(".instances")
                .filter { it.selectFirst(".detail")?.text()?.contains("type of", ignoreCase = true) == true }
                .flatMap { inst -> inst.select("a.word").map { it.text().trim() } }
                .distinct()

            Sense(
                partOfSpeech = pos,
                definition = defText,
                examples = examples,
                synonymsList = synonyms,
                hypernyms = hypernyms,
            )
        }

        return WordEntry(
            word = wordAttr,
            ipa = ipaList,
            audio = audioList,
            forms = forms,
            shortBlurb = shortBlurb,
            longBlurb = longBlurb,
            wordFamily = parseWordFamily(doc, wordAttr),
            senses = senses,
            sourceName = "Vocabulary.com",
            sourceUrl = "https://www.vocabulary.com/dictionary/$encoded",
            license = "",
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /**
     * The fragment embeds a `<vcom:wordfamily data="[{word,type,…}]">` custom element.
     * The attribute is HTML-escaped JSON, so unescape before parsing.
     */
    private fun parseWordFamily(doc: Document, headword: String): List<String> {
        val raw = Regex("<vcom:wordfamily[^>]*\\sdata=\"([^\"]+)\"")
            .find(doc.html())
            ?.groupValues
            ?.get(1)
            ?: return emptyList()
        val decoded = Parser.unescapeEntities(raw, false)
        return runCatching {
            Json.parseToJsonElement(decoded).jsonArray
                .mapNotNull { it.jsonObject["word"]?.jsonPrimitive?.content }
                .filter { !it.equals(headword, ignoreCase = true) }
                .distinct()
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
