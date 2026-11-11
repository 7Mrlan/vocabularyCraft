package dev.wordloom.ui.books

import dev.wordloom.data.local.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookProgressTest {

    private fun card(word: String, stability: Double) = CardEntity(
        word = word,
        stability = stability,
        difficulty = 5.0,
        reps = 1,
        lapses = 0,
        lastReviewAt = 0L,
        dueAt = 0L,
        addedAt = 0L,
        preview = "",
    )

    private fun words(count: Int) = List(count) { "word$it" }

    @Test
    fun `percent keeps a decimal until ten percent`() {
        assertEquals("<0.1%", BookProgress.percentText(1, 3000))
        assertEquals("0.3%", BookProgress.percentText(10, 3000))
        assertEquals("3.3%", BookProgress.percentText(100, 3000))
        assertEquals("10%", BookProgress.percentText(300, 3000))
        assertEquals("41%", BookProgress.percentText(1234, 3000))
        assertEquals("100%", BookProgress.percentText(3000, 3000))
    }

    @Test
    fun `nothing learned reads as zero`() {
        assertEquals("0%", BookProgress.percentText(0, 3000))
        assertEquals("0%", BookProgress.percentText(5, 0))
        assertEquals(0f, BookProgress.ratio(5, 0), 0.0001f)
    }

    @Test
    fun `ratio never leaves zero to one`() {
        assertEquals(1f, BookProgress.ratio(4000, 3000), 0.0001f)
        assertEquals(0f, BookProgress.ratio(-2, 3000), 0.0001f)
    }

    @Test
    fun `status follows the mastery threshold`() {
        assertEquals(WordStatus.New, BookProgress.statusOf(null))
        assertEquals(WordStatus.Learning, BookProgress.statusOf(20.9))
        assertEquals(WordStatus.Mastered, BookProgress.statusOf(21.0))
        assertEquals(WordStatus.Mastered, BookProgress.statusOf(90.0))
    }

    @Test
    fun `status map matches words case insensitively`() {
        val map = BookProgress.statusMap(listOf(card("Great", 30.0)))
        assertEquals(WordStatus.Mastered, map["great"])
    }

    @Test
    fun `units are chunks of the book order`() {
        val sections = BookProgress.sections(words(250), emptyMap(), WordFilter.All)
        assertEquals(listOf(1, 2, 3), sections.map { it.unit })
        assertEquals(listOf(100, 100, 50), sections.map { it.words.size })
        assertEquals("word0", sections.first().words.first())
        assertEquals("word249", sections.last().words.last())
    }

    @Test
    fun `filtering keeps original unit numbers`() {
        val words = words(250)
        val status = mapOf(
            "word180" to WordStatus.Mastered,
            "word10" to WordStatus.Learning,
        )
        val mastered = BookProgress.sections(words, status, WordFilter.Mastered)
        assertEquals(1, mastered.size)
        assertEquals(2, mastered.first().unit)
        assertEquals("word180", mastered.first().words.single())

        val learning = BookProgress.sections(words, status, WordFilter.Learning)
        assertEquals(1, learning.first().unit)
        assertEquals("word10", learning.first().words.single())
    }

    @Test
    fun `unlearned words are exactly the ones without a card`() {
        val words = words(3)
        val status = mapOf("word1" to WordStatus.Learning, "word2" to WordStatus.Mastered)
        val fresh = BookProgress.sections(words, status, WordFilter.New).single().words
        assertEquals(listOf("word0"), fresh)
    }

    @Test
    fun `empty filter result produces no sections`() {
        assertTrue(BookProgress.sections(words(10), emptyMap(), WordFilter.Mastered).isEmpty())
        assertTrue(BookProgress.sections(emptyList(), emptyMap(), WordFilter.All).isEmpty())
    }

    @Test
    fun `words without a card are never counted as learned`() {
        val words = listOf("the", "be", "and")
        assertEquals(0, BookProgress.learnedCount(words, emptyMap()))
        assertEquals(
            1,
            BookProgress.learnedCount(words, mapOf("be" to WordStatus.Learning)),
        )
        assertEquals(
            2,
            BookProgress.learnedCount(
                words,
                mapOf("be" to WordStatus.Learning, "and" to WordStatus.Mastered),
            ),
        )
    }

    @Test
    fun `current unit is the first one with an unlearned word`() {
        val words = words(250)
        assertEquals(0, BookProgress.currentUnitIndex(words, emptyMap()))

        val firstUnitDone = (0 until 100).associate { "word$it" to WordStatus.Learning }
        assertEquals(1, BookProgress.currentUnitIndex(words, firstUnitDone))

        val allDone = words.associateWith { WordStatus.Mastered }
        assertEquals(2, BookProgress.currentUnitIndex(words, allDone))

        assertEquals(0, BookProgress.currentUnitIndex(emptyList(), emptyMap()))
    }

    @Test
    fun `unit count rounds up a partial last unit`() {
        assertEquals(1, BookProgress.unitCount(0))
        assertEquals(1, BookProgress.unitCount(1))
        assertEquals(1, BookProgress.unitCount(100))
        assertEquals(2, BookProgress.unitCount(101))
        assertEquals(30, BookProgress.unitCount(3000))
    }
}
