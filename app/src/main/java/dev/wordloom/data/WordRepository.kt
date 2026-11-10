package dev.wordloom.data

import dev.wordloom.data.local.CachedWordEntity
import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.local.WordListEntity
import dev.wordloom.data.local.ListWithCount
import dev.wordloom.data.local.ListWordEntity
import dev.wordloom.data.local.WordloomDao
import dev.wordloom.data.model.Suggestion
import dev.wordloom.data.model.WordEntry
import dev.wordloom.data.remote.DictionarySource
import dev.wordloom.srs.CardProgress
import dev.wordloom.srs.DAY_MS
import dev.wordloom.srs.Rating
import dev.wordloom.srs.ReviewOutcome
import dev.wordloom.srs.Scheduler
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class WordRepository(
    private val source: DictionarySource,
    private val dao: WordloomDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheTtl = 30 * DAY_MS

    suspend fun lookup(
        term: String,
        forceRefresh: Boolean = false,
        markRecent: Boolean = true,
    ): WordEntry? {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) return null
        val key = cleaned.lowercase()

        // Keep the last good copy even when it is past its freshness window: if
        // the network is down, a stale definition beats an error page. A forced
        // refresh skips the read above but still keeps this copy as a fallback.
        val cached = dao.cachedWord(key)
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - cached.fetchedAt < cacheTtl
        ) {
            decode(cached)?.let { entry ->
                if (markRecent) dao.touchRecent(key, System.currentTimeMillis())
                return entry
            }
        }
        val stale = cached?.let(::decode)

        Log.d(TAG, "cache miss for $key, hitting ${source.id}")
        val started = System.currentTimeMillis()
        val fetched = try {
            source.lookup(cleaned)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (stale != null) {
                Log.w(TAG, "source failed for $key, serving stale cache", error)
                if (markRecent) dao.touchRecent(key, System.currentTimeMillis())
                return stale
            }
            throw error
        }
        if (fetched == null) {
            // "Not found" for a word we had on disk is almost always a failed
            // request; the cached copy is still the best answer we have.
            if (stale != null && markRecent) dao.touchRecent(key, System.currentTimeMillis())
            return stale
        }
        val entry = fetched
        Log.d(TAG, "source returned '${entry.word}' in ${System.currentTimeMillis() - started}ms")
        dao.putWord(
            CachedWordEntity(
                word = key,
                json = json.encodeToString(WordEntry.serializer(), entry),
                fetchedAt = entry.fetchedAt,
                recentAt = if (markRecent) System.currentTimeMillis() else 0L,
            )
        )
        return entry
    }

    /**
     * Suggestions for [prefix]. Returns an empty list when nothing matched and
     * throws when the lookup could not be completed, so the caller can show a
     * retry instead of a false "no match".
     */
    suspend fun suggest(prefix: String): List<Suggestion> =
        if (prefix.isBlank()) emptyList() else source.suggest(prefix)

    suspend fun randomWord(): String? = source.random()

    fun recentWords(): Flow<List<WordEntry>> =
        dao.recentWords().map { list -> list.mapNotNull { decode(it) } }

    /**
     * Every word the person actually opened, newest first. Background fetches
     * (the daily word) write recentAt = 0, so they stay out until tapped.
     */
    fun openedWords(limit: Int = 400): Flow<List<WordEntry>> =
        dao.recentWords(limit).map { list -> list.mapNotNull { decode(it) } }

    /** Cached entries for the quiz; capped so a long history never slows the screen. */
    fun cachedEntries(limit: Int = 300): Flow<List<WordEntry>> =
        dao.cachedWords(limit).map { list -> list.mapNotNull { decode(it) } }

    // ---- backup ----

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            cards = dao.allCards(),
            lists = dao.allLists(),
            listWords = dao.allListWords(),
            words = dao.allCachedWords(),
        )
        json.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * Merge, never wipe: cards and words are upserted by key; lists are matched by
     * name + createdAt so importing the same file twice does not duplicate them.
     */
    suspend fun importBackup(text: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.version <= 1) { "备份文件版本过新" }

            file.words.forEach { dao.putWord(it) }
            file.cards.forEach { dao.putCard(it) }

            val existing = dao.allLists().associateBy { it.name to it.createdAt }
            val idMap = mutableMapOf<Long, Long>()
            file.lists.forEach { list ->
                val match = existing[list.name to list.createdAt]
                val newId = match?.id ?: dao.putListRaw(list.copy(id = 0))
                idMap[list.id] = newId
            }
            file.listWords.forEach { row ->
                val target = idMap[row.listId] ?: return@forEach
                dao.addToList(row.copy(listId = target))
            }
            Result.success(file.cards.size + file.lists.size)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    fun cards(): Flow<List<CardEntity>> = dao.cards()

    fun dueCards(): Flow<List<CardEntity>> =
        cards().map { list ->
            // Read the clock on every emission: a card added after the flow was
            // created must still show up as due.
            val now = System.currentTimeMillis()
            list.filter { it.dueAt <= now }.sortedBy { it.dueAt }
        }

    suspend fun isInDeck(word: String): Boolean = dao.card(word.lowercase()) != null

    suspend fun card(word: String): CardEntity? = dao.card(word.lowercase())

    suspend fun restoreCard(card: CardEntity) = dao.putCard(card)

    suspend fun addToDeck(entry: WordEntry) {
        val key = entry.word.lowercase()
        if (dao.card(key) != null) return
        val now = System.currentTimeMillis()
        val preview = entry.senses.firstOrNull()?.definition.orEmpty().take(200)
        dao.putCard(
            CardEntity(
                word = key,
                stability = 0.0,
                difficulty = 0.0,
                reps = 0,
                lapses = 0,
                lastReviewAt = 0L,
                dueAt = now,
                addedAt = now,
                preview = preview,
            )
        )
    }

    suspend fun removeFromDeck(word: String) = dao.deleteCard(word.lowercase())

    suspend fun review(card: CardEntity, rating: Rating): ReviewOutcome {
        val outcome = Scheduler.review(
            CardProgress(
                stability = card.stability,
                difficulty = card.difficulty,
                reps = card.reps,
                lapses = card.lapses,
                lastReviewAt = card.lastReviewAt,
                dueAt = card.dueAt,
            ),
            rating,
        )
        dao.putCard(
            card.copy(
                stability = outcome.progress.stability,
                difficulty = outcome.progress.difficulty,
                reps = outcome.progress.reps,
                lapses = outcome.progress.lapses,
                lastReviewAt = outcome.progress.lastReviewAt,
                dueAt = outcome.progress.dueAt,
            )
        )
        return outcome
    }

    // ---- word lists ----

    fun lists(): Flow<List<ListWithCount>> = dao.listsWithCount()

    fun wordsInList(listId: Long): Flow<List<ListWord>> =
        dao.listWordRows(listId).map { rows ->
            rows.map { ListWord(it.word, glossOf(it.json)) }
        }

    /** A one-line gloss from the cache; empty when this word was never opened. */
    private fun glossOf(jsonText: String?): String {
        if (jsonText == null) return ""
        val entry = runCatching {
            json.decodeFromString(WordEntry.serializer(), jsonText)
        }.getOrNull() ?: return ""
        return entry.shortBlurb?.takeIf { it.isNotBlank() }
            ?: entry.senses.firstOrNull()?.definition.orEmpty()
    }

    suspend fun createList(name: String): Long =
        dao.insertList(WordListEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun addToList(listId: Long, word: String) {
        dao.addToList(ListWordEntity(listId, word.trim().lowercase(), System.currentTimeMillis()))
    }

    suspend fun removeFromList(listId: Long, word: String) = dao.removeFromList(listId, word)

    suspend fun trimOldCache() = dao.trimCache(System.currentTimeMillis() - 90 * DAY_MS)

    private fun decode(entity: CachedWordEntity): WordEntry? =
        runCatching { json.decodeFromString(WordEntry.serializer(), entity.json) }.getOrNull()

    private companion object {
        const val TAG = "Wordloom"
    }
}

/** A word inside a list plus the gloss we already have cached for it. */
data class ListWord(
    val word: String,
    val gloss: String,
)
