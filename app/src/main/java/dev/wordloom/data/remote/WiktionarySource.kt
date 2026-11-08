package dev.wordloom.data.remote

import dev.wordloom.data.model.AudioClip
import dev.wordloom.data.model.Pronunciation
import dev.wordloom.data.model.Sense
import dev.wordloom.data.model.SynonymGroup
import dev.wordloom.data.model.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.concurrent.TimeUnit

/**
 * Wiktionary (CC BY-SA 4.0) as the v1 content source.
 *
 * Two calls per word:
 *  1. REST `page/definition/{term}` — structured senses, small payload.
 *  2. `action=parse` — the English section only, for IPA and Commons audio.
 *
 * Wikimedia asks every client to identify itself; keep the User-Agent set.
 */
class WiktionarySource(
    private val client: OkHttpClient = defaultClient(),
) : DictionarySource {

    override val id = "wiktionary"
    override val displayName = "Wiktionary"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun lookup(term: String): WordEntry? = withContext(Dispatchers.IO) {
        val cleaned = term.trim().replace('_', ' ')
        if (cleaned.isEmpty()) return@withContext null

        android.util.Log.d("Wordloom", "wiktionary: senses for $cleaned")
        val senses = runCatching { fetchSenses(cleaned) }
            .onFailure { android.util.Log.w("Wordloom", "senses failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrElse { emptyList() }
        android.util.Log.d("Wordloom", "wiktionary: ${senses.size} senses")

        // Pronunciation is best-effort: a missing page must not hide the senses.
        android.util.Log.d("Wordloom", "wiktionary: fetching page html")
        val page = runCatching { fetchPageHtml(cleaned) }
            .onFailure { android.util.Log.w("Wordloom", "parse failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()
        val ipa = page?.let { extractIpa(it) }.orEmpty()
        val audio = page?.let { extractAudio(it) }.orEmpty()
        val etymology = page?.let { extractEtymology(it) }
        android.util.Log.d("Wordloom", "wiktionary: ipa=${ipa.size} audio=${audio.size} html=${page?.length ?: 0}")

        if (senses.isEmpty() && ipa.isEmpty() && audio.isEmpty()) return@withContext null

        val sensesWithSyn = attachSynonyms(page.orEmpty(), senses)
        android.util.Log.d(
            "Wordloom",
            "wiktionary: synonyms attached to " + sensesWithSyn.count { it.synonyms.isNotEmpty() } + "/" + senses.size + " senses",
        )

        WordEntry(
            word = cleaned,
            ipa = ipa,
            audio = audio,
            etymology = etymology,
            senses = sensesWithSyn,
            sourceName = displayName,
            sourceUrl = "https://en.wiktionary.org/wiki/${cleaned.replace(' ', '_')}",
            license = "CC BY-SA 4.0",
            fetchedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun suggest(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://en.wiktionary.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "opensearch")
            .addQueryParameter("search", prefix.trim())
            .addQueryParameter("limit", "8")
            .addQueryParameter("namespace", "0")
            .addQueryParameter("format", "json")
            .build()
        runCatching {
            val body = get(url.toString())
            val root = json.parseToJsonElement(body).jsonArray
            root.getOrNull(1)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    override suspend fun random(): String? = withContext(Dispatchers.IO) {
        repeat(3) {
            val url = "https://en.wiktionary.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "random")
                .addQueryParameter("rnnamespace", "0")
                .addQueryParameter("rnlimit", "10")
                .addQueryParameter("format", "json")
                .build()
            val candidates = runCatching {
                json.parseToJsonElement(get(url.toString()))
                    .jsonObject["query"]?.jsonObject
                    ?.get("random")?.jsonArray
                    ?.mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.content }
                    ?: emptyList()
            }.getOrElse { emptyList() }

            candidates.firstOrNull { it.matches(Regex("^[a-z][a-z-]{3,}$")) }?.let { return@withContext it }
        }
        null
    }

    private fun fetchSenses(term: String): List<Sense> {
        val url = "https://en.wiktionary.org/api/rest_v1/page/definition".toHttpUrl().newBuilder()
            .addPathSegment(term.replace(' ', '_'))
            .build()
            .toString()
        val body = get(url)
        val root = json.parseToJsonElement(body).jsonObject
        val english = root["en"]?.jsonArray ?: return emptyList()

        return english.flatMap { group ->
            val groupObj = group.jsonObject
            val pos = groupObj["partOfSpeech"]?.jsonPrimitive?.content.orEmpty()
            val defs = groupObj["definitions"]?.jsonArray ?: return@flatMap emptyList()
            defs.flatMap { d ->
                val raw = d.jsonObject["definition"]?.jsonPrimitive?.content ?: return@flatMap emptyList()
                val examples = d.jsonObject["examples"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
                    ?.map { Jsoup.parse(it).text().trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.take(2)
                    .orEmpty()
                // A single REST "definition" often carries a nested <ol> of subsenses.
                // Split those out so the UI gets one numbered sense per line.
                splitSenses(raw).map { text ->
                    Sense(partOfSpeech = pos, definition = text, examples = examples)
                }
            }.distinctBy { it.definition }
        }
    }

    /** One sense per nested `<li>` when the payload has subsenses, otherwise the whole text. */
    private fun splitSenses(raw: String): List<String> {
        val doc = Jsoup.parse("<div id=\"wl-root\">$raw</div>")
        val items = doc.select("#wl-root li")
        val texts = if (items.size > 1) {
            items.map { li ->
                val clone = li.clone()
                clone.select("ol, ul").remove()
                clone.text().trim()
            }
        } else {
            listOf(doc.text().trim())
        }
        return texts.filter { it.isNotEmpty() && it.length > 2 }
    }

    private fun fetchPageHtml(term: String): String {
        val url = "https://en.wiktionary.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", term)
            .addQueryParameter("prop", "text")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val body = get(url.toString())
        return json.parseToJsonElement(body).jsonObject["parse"]
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: ""
    }

    /**
     * The Etymology section's prose. This is the closest honest equivalent of
     * the reference's lead paragraph: a single readable block about where the
     * word comes from, instead of another bullet list.
     */
    private fun extractEtymology(html: String): String? {
        val doc = Jsoup.parse(html)
        val section = englishSection(doc)
        val heading = section.asSequence()
            .mapNotNull { it.selectFirst("h3[id^=Etymology]") }
            .firstOrNull() ?: return null
        val start = if (heading.parent()?.hasClass("mw-heading") == true) heading.parent()!! else heading

        val parts = mutableListOf<String>()
        var sibling: Element? = start.nextElementSibling()
        while (sibling != null && parts.joinToString(" ").length < 700) {
            val tag = sibling.tagName()
            if (tag == "h2" || tag == "h3" || tag == "h4") break
            if (tag == "div" && (sibling.hasClass("mw-heading2") || sibling.hasClass("mw-heading3"))) break
            if (tag == "p" || tag == "ul" || tag == "dl") {
                val clone = sibling.clone()
                clone.select("sup.reference, span.mw-editsection, style").remove()
                val text = clone.text().trim()
                if (text.isNotEmpty()) parts.add(text)
            }
            sibling = sibling.nextElementSibling()
        }
        return parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            .takeIf { it.length > 40 }
    }

    /**
     * Synonyms from one part-of-speech section's `<h4>Synonyms</h4>` block.
     * Lines look like "(moving with speed): fast, speedy, rapid, swift;
     * see also Thesaurus:speedy". Returns qualifier -> word list, keeping order.
     */
    private fun extractSynonymGroups(posSection: String): List<SynonymGroup> {
        android.util.Log.d("Wordloom", "syn: section len=" + posSection.length)
        val h4 = posSection.split(Regex("(?=<h4[^>]*>)")).firstOrNull { block ->
            block.startsWith("<h4") && block.substringBefore("</h4>").contains("Synonyms")
        } ?: run { android.util.Log.d("Wordloom", "syn: NO h4 block"); return emptyList() }
        val block = h4.substringBefore("<h4", h4)  // stop at the next h4
        val lis = Regex("<li[^>]*>(.*?)</li>").findAll(block).map { it.groupValues[1] }

        return lis.mapNotNull { li ->
            val plain = Jsoup.parse(li).text().trim()
            if (plain.isEmpty()) return@mapNotNull null
            if (plain.startsWith("See Thesaurus")) return@mapNotNull null

            val qualified = Regex("^\\(([^)]+)\\)[:：]\\s*(.+)$").find(plain)
            if (qualified != null) {
                val sense = qualified.groupValues[1].trim()
                val words = cleanWordList(qualified.groupValues[2])
                if (words.isEmpty()) null else SynonymGroup(sense, words)
            } else {
                val words = cleanWordList(plain)
                if (words.size < 2) null else SynonymGroup("", words)
            }
        }.toList()
    }

    private fun cleanWordList(raw: String): List<String> =
        raw.substringBefore(";")
            .split(",", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !it.contains("Thesaurus") && !it.startsWith("see also") && !it.startsWith("See ") }
            .map { it.removeSuffix(")") }
            .take(6)

    /**
     * Slice one POS section (h3 "Noun"/"Verb"/...) out of the page HTML with
     * JSoup: regex windows over 175KB sections matched the wrong boundaries.
     */
    private fun posSection(html: String, posTitle: String): String {
        val doc = org.jsoup.Jsoup.parse(html)
        for (h3 in doc.select("h3")) {
            val text = h3.text().trim()
            if (!text.equals(posTitle, ignoreCase = true)) continue
            val parent = if (h3.parent()?.hasClass("mw-heading") == true) h3.parent()!! else h3
            val out = StringBuilder()
            var sibling: Element? = parent.nextElementSibling()
            while (sibling != null) {
                val isNextLang = sibling.tagName() == "h2" ||
                    (sibling.tagName() == "div" && sibling.hasClass("mw-heading2"))
                if (isNextLang) break
                out.append(sibling.outerHtml())
                sibling = sibling.nextElementSibling()
            }
            return out.toString()
        }
        return ""
    }

    /**
     * REST senses are flat; synonym groups live in the page HTML per POS.
     * Unqualified groups apply to the first sense of that POS; qualified ones
     * are matched by their qualifier or words appearing in the definition,
     * else handed out in order so each sense gets at most one group.
     */
    private fun attachSynonyms(pageHtml: String, senses: List<Sense>): List<Sense> {
        if (senses.isEmpty() || pageHtml.isEmpty()) return senses
        val out = senses.toMutableList()

        senses.groupBy { it.partOfSpeech.lowercase() }.forEach { (pos, posSenses) ->
            val section = posSection(pageHtml, pos.replaceFirstChar { it.uppercase() })
            if (section.isEmpty()) return@forEach
            val groups = extractSynonymGroups(section)
            if (groups.isEmpty()) return@forEach

            val qualified = groups.filter { it.sense.isNotBlank() }
            val plain = groups.filter { it.sense.isBlank() }
            val used = mutableSetOf<SynonymGroup>()

            posSenses.forEach { sense ->
                val byQualifier = qualified.firstOrNull { g ->
                    g.sense.length > 3 && sense.definition.contains(g.sense, ignoreCase = true)
                }
                val byWord = qualified.firstOrNull { g ->
                    g !in used && g.words.any { sense.definition.contains(it, ignoreCase = true) }
                }
                val fallback = (plain + qualified).firstOrNull { it !in used }
                val match = byQualifier ?: byWord ?: fallback

                if (match != null) {
                    used.add(match)
                    val i = out.indexOfFirst { it === sense }
                    if (i >= 0) out[i] = sense.copy(synonyms = listOf(match))
                }
            }
        }
        return out
    }

    private fun extractIpa(html: String): List<Pronunciation> {
        val doc = Jsoup.parse(html)
        val section = englishSection(doc)
        val seen = LinkedHashSet<String>()
        section.select("span.IPA").forEach { el ->
            val text = el.text().trim()
            // Skip rhyme fragments such as "-uːʒən".
            if (text.length in 3..40 && text.startsWith("/") && text.endsWith("/")) seen.add(text)
        }
        return seen.take(3).mapIndexed { i, text ->
            Pronunciation(label = if (seen.size == 1) "" else "${i + 1}", text = text)
        }
    }

    private fun extractAudio(html: String): List<AudioClip> {
        val doc = Jsoup.parse(html)
        val section = englishSection(doc)
        val urls = LinkedHashSet<String>()

        section.select("audio source[src]").forEach { s ->
            val src = s.attr("src")
            if (src.isNotBlank() && src.endsWith(".mp3")) urls.add(normalize(src))
        }
        if (urls.isEmpty()) {
            section.select("audio source[src]").forEach { s ->
                val src = s.attr("src")
                if (src.isNotBlank()) urls.add(normalize(src))
            }
        }
        if (urls.isEmpty()) {
            section.select("a[href$=.ogg], a[href$=.oga]").forEach { a ->
                urls.add(normalize(a.attr("href")))
            }
        }
        return urls.filter { it.startsWith("https://") }.take(3)
            .mapIndexed { i, url -> AudioClip(label = "${i + 1}", url = url) }
    }

    /** Everything after the level-2 heading `English`, up to the next level-2 heading. */
    private fun englishSection(doc: org.jsoup.nodes.Document): Elements {
        val heading = doc.selectFirst("h2#English")
            ?: doc.selectFirst("h2:matchesOwn(^English$)")
            ?: return Elements()
        val start = if (heading.parent()?.hasClass("mw-heading") == true) heading.parent()!! else heading
        val out = Elements()
        var sibling: Element? = start.nextElementSibling()
        while (sibling != null) {
            val isNextLanguage = sibling.tagName() == "h2" ||
                (sibling.tagName() == "div" && sibling.hasClass("mw-heading2"))
            if (isNextLanguage) break
            out.add(sibling)
            sibling = sibling.nextElementSibling()
        }
        return out
    }

    private fun normalize(src: String): String = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "https://en.wiktionary.org$src"
        else -> src
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        private const val USER_AGENT =
            "Wordloom/0.1 (open-source dictionary client; https://github.com/) OkHttp"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
