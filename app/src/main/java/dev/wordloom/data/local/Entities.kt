package dev.wordloom.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_words")
data class CachedWordEntity(
    @PrimaryKey val word: String,
    val json: String,
    val fetchedAt: Long,
    /** When the person last opened this word; 0 for background fetches. */
    val recentAt: Long = 0L,
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val word: String,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewAt: Long,
    val dueAt: Long,
    val addedAt: Long,
    val preview: String,
)

@Entity(tableName = "lists")
data class WordListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "list_words", primaryKeys = ["listId", "word"])
data class ListWordEntity(
    val listId: Long,
    val word: String,
    val addedAt: Long,
)

/** A list with its word count, for the lists overview. */
data class ListWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val wordCount: Int,
)
