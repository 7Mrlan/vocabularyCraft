package dev.wordloom.ui.study

import dev.wordloom.data.local.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyQueueTest {

    private fun card(word: String) = CardEntity(
        word = word,
        stability = 1.0,
        difficulty = 5.0,
        reps = 1,
        lapses = 0,
        lastReviewAt = 0L,
        dueAt = 0L,
        addedAt = 0L,
        preview = "",
    )

    private val unit = (1..30).map { "word$it" }

    @Test
    fun `a fresh unit gives its first ten words`() {
        assertEquals(unit.take(10), StudyQueue.newWords(unit, emptyList()))
    }

    @Test
    fun `words with a card are not offered again, due or not`() {
        val scheduled = unit.take(10).map(::card)
        assertEquals(unit.drop(10).take(10), StudyQueue.newWords(unit, scheduled))
    }

    @Test
    fun `scheduled words match case insensitively`() {
        val scheduled = listOf(card("WORD1"), card("word2"))
        assertEquals("word3", StudyQueue.newWords(unit, scheduled).first())
    }

    @Test
    fun `the tail of a unit can be shorter than a session`() {
        val scheduled = unit.dropLast(4).map(::card)
        assertEquals(unit.takeLast(4), StudyQueue.newWords(unit, scheduled))
    }

    @Test
    fun `a finished unit yields nothing`() {
        assertEquals(emptyList<String>(), StudyQueue.newWords(unit, unit.map(::card)))
    }
}
