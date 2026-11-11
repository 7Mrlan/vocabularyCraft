package dev.wordloom.ui.books

import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry
import java.util.Locale
import kotlin.math.roundToInt

enum class WordFilter(val label: String) {
    All("全部"),
    New("未学习"),
    Learning("学习中"),
    Mastered("已掌握"),
}

enum class WordStatus { New, Learning, Mastered }

/** One unit of a book: the words as they appear in the book's own order. */
data class BookSection(val unit: Int, val words: List<WordEntry>)

/**
 * The arithmetic behind a book's page — progress, status and unit grouping.
 *
 * Kept free of Compose so the rules that decide what a reader sees ("is this
 * word learned?", "which unit is this?") can be tested without a device.
 */
object BookProgress {

    const val UNIT_SIZE = 100

    /** Stability in days at which a card counts as mastered on this page. */
    const val MASTERED_STABILITY = 21.0

    fun ratio(learned: Int, total: Int): Float =
        if (total <= 0) 0f else (learned.toFloat() / total).coerceIn(0f, 1f)

    /**
     * Two significant shapes: one decimal below ten percent, whole numbers
     * above it. A reader at 0.3% should see that they have started, not "0%".
     */
    fun percentText(learned: Int, total: Int): String {
        if (learned <= 0 || total <= 0) return "0%"
        val percent = ratio(learned, total) * 100f
        return if (percent >= 10f) {
            "${percent.roundToInt()}%"
        } else {
            String.format(Locale.US, "%.1f%%", percent)
        }
    }

    fun statusOf(stability: Double?): WordStatus = when {
        stability == null -> WordStatus.New
        stability >= MASTERED_STABILITY -> WordStatus.Mastered
        else -> WordStatus.Learning
    }

    fun statusMap(cards: List<CardEntity>): Map<String, WordStatus> =
        cards.associate { it.word.lowercase() to statusOf(it.stability) }

    fun matches(status: WordStatus, filter: WordFilter): Boolean = when (filter) {
        WordFilter.All -> true
        WordFilter.New -> status == WordStatus.New
        WordFilter.Learning -> status == WordStatus.Learning
        WordFilter.Mastered -> status == WordStatus.Mastered
    }

    /**
     * Units are a property of the book's order, not of the filter: a filtered
     * list keeps its original unit numbers and only drops the empty ones.
     */
    fun sections(
        words: List<WordEntry>,
        status: Map<String, WordStatus>,
        filter: WordFilter,
        unitSize: Int = UNIT_SIZE,
    ): List<BookSection> =
        words.withIndex()
            .filter { (_, entry) ->
                matches(status[entry.word.lowercase()] ?: WordStatus.New, filter)
            }
            .groupBy { it.index / unitSize }
            .map { (index, rows) -> BookSection(index + 1, rows.map { it.value }) }

    fun unitCount(total: Int, unitSize: Int = UNIT_SIZE): Int =
        if (total <= 0) 1 else (total + unitSize - 1) / unitSize
}
