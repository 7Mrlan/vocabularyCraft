package dev.wordloom.data.remote

import dev.wordloom.data.model.AudioClip
import dev.wordloom.data.model.Pronunciation
import dev.wordloom.data.model.Sense
import dev.wordloom.data.model.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class VocabularyComSource(
    private val client: OkHttpClient = defaultClient(),
    private val fallback: WiktionarySource = WiktionarySource(client),
) : DictionarySource {

    override val id: String = "vocabulary_com"
    override val displayName: String = "Vocabulary.com"

    override suspend fun lookup(term: String): WordEntry? = withContext(Dispatchers.IO) {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return@withContext null

        val vocabEntry = runCatching { fetchFromVocab(cleaned) }.getOrNull()
        if (vocabEntry != null && vocabEntry.hasContent) {
            return@withContext vocabEntry
        }

        // Fallback to Wiktionary if Vocabulary.com doesn't have the word
        fallback.lookup(cleaned)
    }

    override suspend fun suggest(prefix: String): List<String> = fallback.suggest(prefix)

    override suspend fun random(): String? = fallback.random()

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
            val synonyms = li.select(".instances")
                .filter { it.selectFirst(".detail")?.text()?.contains("synonym", ignoreCase = true) == true }
                .flatMap { inst -> inst.select("a.word").map { it.text().trim() } }
                .distinct()

            Sense(
                partOfSpeech = pos,
                definition = defText,
                examples = examples,
                synonymsList = synonyms,
            )
        }

        return WordEntry(
            word = wordAttr,
            ipa = ipaList,
            audio = audioList,
            forms = forms,
            shortBlurb = shortBlurb,
            longBlurb = longBlurb,
            senses = senses,
            sourceName = "Vocabulary.com",
            sourceUrl = "https://www.vocabulary.com/dictionary/$encoded",
            license = "",
            fetchedAt = System.currentTimeMillis(),
        )
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
