package dev.wordloom.data.wordbook

import android.content.Context

/** The three curated books. Their order is the study order. */
enum class WordBook(
    val asset: String,
    val title: String,
    val note: String,
    val count: Int,
) {
    Core("core3000.txt", "通用核心", "日常与考试通用", 3000),
    Cet("cet2500.txt", "四六级核心", "CET-4/6 大纲裁剪", 2500),
    Kaoyan("kaoyan3000.txt", "考研核心", "考研大纲裁剪", 3000);

    companion object {
        fun fromId(id: String?): WordBook = entries.firstOrNull { it.name == id } ?: Core
    }
}

/**
 * Reading a book off disk is just splitting lines and dropping repeats; the
 * unit slicing is the only arithmetic, so both live here and are tested.
 */
object WordLists {

    const val UNIT_SIZE = 100

    fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /** Zero-based unit; an index past the end is an empty unit, not a crash. */
    fun unit(words: List<String>, unitIndex: Int, unitSize: Int = UNIT_SIZE): List<String> {
        if (unitIndex < 0 || unitSize <= 0) return emptyList()
        val start = unitIndex * unitSize
        if (start >= words.size) return emptyList()
        return words.subList(start, minOf(start + unitSize, words.size))
    }

    fun unitCount(words: List<String>, unitSize: Int = UNIT_SIZE): Int =
        if (words.isEmpty()) 0 else (words.size + unitSize - 1) / unitSize
}

class WordBookStore(private val context: Context) {

    private val cache = mutableMapOf<WordBook, List<String>>()

    @Synchronized
    fun words(book: WordBook): List<String> = cache.getOrPut(book) {
        context.assets.open("wordbooks/${book.asset}")
            .bufferedReader()
            .use { WordLists.parse(it.readText()) }
    }

    @Synchronized
    fun unit(book: WordBook, unitIndex: Int): List<String> =
        WordLists.unit(words(book), unitIndex)
}
