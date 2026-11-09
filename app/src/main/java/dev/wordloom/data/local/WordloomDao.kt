package dev.wordloom.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordloomDao {

    @Query("SELECT * FROM cached_words WHERE word = :word LIMIT 1")
    suspend fun cachedWord(word: String): CachedWordEntity?

    @Upsert
    suspend fun putWord(entity: CachedWordEntity)

    @Query("SELECT * FROM cached_words WHERE recentAt > 0 ORDER BY recentAt DESC LIMIT :limit")
    fun recentWords(limit: Int = 12): Flow<List<CachedWordEntity>>

    @Query("UPDATE cached_words SET recentAt = :at WHERE word = :word")
    suspend fun touchRecent(word: String, at: Long)

    @Query("SELECT * FROM cards ORDER BY dueAt ASC")
    fun cards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE word = :word LIMIT 1")
    suspend fun card(word: String): CardEntity?

    @Query("SELECT * FROM cards WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT 40")
    suspend fun dueCards(now: Long): List<CardEntity>

    @Upsert
    suspend fun putCard(entity: CardEntity)

    @Query("DELETE FROM cards WHERE word = :word")
    suspend fun deleteCard(word: String)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun cardCount(): Int

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

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM list_words WHERE listId = :id")
    suspend fun clearListWords(id: Long)

    @Query("SELECT * FROM list_words WHERE listId = :id ORDER BY addedAt DESC")
    fun wordsInList(id: Long): Flow<List<ListWordEntity>>

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
}
