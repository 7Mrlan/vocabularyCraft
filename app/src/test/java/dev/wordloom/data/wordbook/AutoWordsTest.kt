package dev.wordloom.data.wordbook

import dev.wordloom.data.local.CardEntity
import dev.wordloom.data.model.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoWordsTest {

    private fun card(
        word: String,
        lapses: Int = 0,
        addedAt: Long = 0L,
        lastReviewAt: Long = 0L,
        savedAt: Long = addedAt,
    ) = CardEntity(
        word = word,
        stability = 1.0,
        difficulty = 5.0,
        reps = 1,
        lapses = lapses,
        lastReviewAt = lastReviewAt,
        dueAt = 0L,
        addedAt = addedAt,
        preview = "",
        savedAt = savedAt,
    )

    @Test
    fun `looked-up words keep their order and drop repeats`() {
        val opened = listOf(WordEntry("Go"), WordEntry("say"), WordEntry("go"))
        assertEquals(listOf("go", "say"), AutoWords.looked(opened))
    }

    @Test
    fun `wrong words only include words that were forgotten`() {
        val cards = listOf(
            card("a", lapses = 0),
            card("b", lapses = 1),
            card("c", lapses = 3),
        )
        assertEquals(listOf("c", "b"), AutoWords.wrong(cards))
    }

    @Test
    fun `wrong words break ties by the latest review`() {
        val cards = listOf(
            card("old", lapses = 2, lastReviewAt = 100),
            card("new", lapses = 2, lastReviewAt = 900),
        )
        assertEquals(listOf("new", "old"), AutoWords.wrong(cards))
    }

    @Test
    fun `saved words are newest first`() {
        val cards = listOf(
            card("early", addedAt = 10),
            card("late", addedAt = 50),
        )
        assertEquals(listOf("late", "early"), AutoWords.saved(cards))
    }

    @Test
    fun `study-only cards stay out of the saved book`() {
        val cards = listOf(
            card("bookmarked", addedAt = 10, savedAt = 99),
            card("studied", addedAt = 50, savedAt = 0),
        )
        assertEquals(listOf("bookmarked"), AutoWords.saved(cards))
    }

    @Test
    fun `saved words follow the bookmark time, not the deck time`() {
        val cards = listOf(
            card("decked-late", addedAt = 900, savedAt = 10),
            card("bookmarked-late", addedAt = 5, savedAt = 900),
        )
        assertEquals(listOf("bookmarked-late", "decked-late"), AutoWords.saved(cards))
    }

    @Test
    fun `each automatic book resolves to its own list`() {
        val opened = listOf(WordEntry("go"))
        val cards = listOf(card("say", lapses = 2, addedAt = 5))
        assertEquals(listOf("go"), AutoWords.of(AutoBook.Looked, opened, cards))
        assertEquals(listOf("say"), AutoWords.of(AutoBook.Wrong, opened, cards))
        assertEquals(listOf("say"), AutoWords.of(AutoBook.Saved, opened, cards))
    }

    @Test
    fun `book ids resolve and unknown ids do not`() {
        assertEquals(AutoBook.Wrong, AutoBook.fromId("wrong"))
        assertEquals(null, AutoBook.fromId("core3000"))
        assertEquals(null, AutoBook.fromId(null))
    }
}
