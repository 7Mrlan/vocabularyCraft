package dev.wordloom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordloomDao {

    @Query("SELECT * FROM cached_words WHERE word = :word LIMIT 1")
    suspend fun cachedWord(word: String): CachedWordEntity?

    /** Every cached row for a page that lists a whole book; callers chunk the ids. */
    @Query("SELECT * FROM cached_words WHERE word IN (:words)")
    suspend fun cachedWordsFor(words: List<String>): List<CachedWordEntity>

    @Upsert
    suspend fun putWord(entity: CachedWordEntity)

    @Query("SELECT * FROM cached_words WHERE recentAt > 0 ORDER BY recentAt DESC LIMIT :limit")
    fun recentWords(limit: Int = 12): Flow<List<CachedWordEntity>>

    /** Everything cached, newest first — the pool the quiz draws questions from. */
    @Query("SELECT * FROM cached_words ORDER BY fetchedAt DESC LIMIT :limit")
    fun cachedWords(limit: Int): Flow<List<CachedWordEntity>>

    @Query("UPDATE cached_words SET recentAt = :at WHERE word = :word")
    suspend fun touchRecent(word: String, at: Long)

    @Query("SELECT * FROM cards ORDER BY dueAt ASC")
    fun cards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE word = :word LIMIT 1")
    suspend fun card(word: String): CardEntity?

    @Upsert
    suspend fun putCard(entity: CardEntity)

    @Query("DELETE FROM cards WHERE word = :word")
    suspend fun deleteCard(word: String)

    @Query("DELETE FROM cached_words WHERE fetchedAt < :before")
    suspend fun trimCache(before: Long)

    // ---- lists ----

    @Query("""
        SELECT l.id AS id, l.name AS name, l.createdAt AS createdAt,
               (SELECT COUNT(*) FROM list_words w WHERE w.listId = l.id) AS wordCount
        FROM lists l ORDER BY l.createdAt DESC
    """)
    fun listsWithCount(): Flow<List<ListWithCount>>

    @Insert
    suspend fun insertList(list: WordListEntity): Long

    /** List words joined with whatever we already cached, so rows can show a gloss. */
    @Query(
        """
        SELECT lw.word AS word, cw.json AS json
        FROM list_words lw
        LEFT JOIN cached_words cw ON cw.word = lw.word
        WHERE lw.listId = :id
        ORDER BY lw.addedAt DESC
        """,
    )
    fun listWordRows(id: Long): Flow<List<ListWordRow>>

    @Upsert
    suspend fun addToList(entry: ListWordEntity)

    @Query("DELETE FROM list_words WHERE listId = :id AND word = :word")
    suspend fun removeFromList(id: Long, word: String)

    // ---- backup ----

    @Query("SELECT * FROM cached_words")
    suspend fun allCachedWords(): List<CachedWordEntity>

    @Query("SELECT * FROM cards")
    suspend fun allCards(): List<CardEntity>

    @Query("SELECT * FROM lists")
    suspend fun allLists(): List<WordListEntity>

    @Query("SELECT * FROM list_words")
    suspend fun allListWords(): List<ListWordEntity>

    /** Import keeps the id we hand it, so list words can be re-pointed after a merge. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putListRaw(list: WordListEntity): Long
}
