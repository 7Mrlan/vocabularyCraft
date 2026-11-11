package dev.wordloom.data.wordbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListsTest {

    @Test
    fun `parse trims, lowercases and drops repeats`() {
        val words = WordLists.parse("  The \nthe\n\nbe\nBe \n")
        assertEquals(listOf("the", "be"), words)
    }

    @Test
    fun `parse keeps a trailing line without a newline`() {
        assertEquals(listOf("one", "two"), WordLists.parse("one\ntwo"))
    }

    @Test
    fun `units are 100 words and the last one is short`() {
        val words = (0 until 250).map { "w$it" }
        assertEquals(100, WordLists.unit(words, 0).size)
        assertEquals("w100", WordLists.unit(words, 1).first())
        assertEquals(50, WordLists.unit(words, 2).size)
        assertEquals("w249", WordLists.unit(words, 2).last())
    }

    @Test
    fun `an index past the book is empty, not a crash`() {
        val words = (0 until 100).map { "w$it" }
        assertTrue(WordLists.unit(words, 1).isEmpty())
        assertTrue(WordLists.unit(words, -1).isEmpty())
        assertTrue(WordLists.unit(emptyList(), 0).isEmpty())
    }

    @Test
    fun `unit count rounds up`() {
        assertEquals(0, WordLists.unitCount(emptyList()))
        assertEquals(1, WordLists.unitCount(List(100) { "w$it" }))
        assertEquals(2, WordLists.unitCount(List(101) { "w$it" }))
        assertEquals(30, WordLists.unitCount(List(3000) { "w$it" }))
    }

    @Test
    fun `book lookup falls back to the first book`() {
        assertEquals(WordBook.Core, WordBook.fromId(null))
        assertEquals(WordBook.Core, WordBook.fromId("nope"))
        assertEquals(WordBook.Kaoyan, WordBook.fromId("Kaoyan"))
    }
}
