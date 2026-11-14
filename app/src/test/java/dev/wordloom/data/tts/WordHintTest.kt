package dev.wordloom.data.tts

import dev.wordloom.data.model.Pronunciation
import dev.wordloom.data.model.Sense
import dev.wordloom.data.model.WordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordHintTest {

    private fun entry(
        ipa: List<Pronunciation> = emptyList(),
        pos: String? = null,
    ) = WordEntry(
        word = "record",
        ipa = ipa,
        senses = listOfNotNull(
            pos?.let { Sense(partOfSpeech = it, definition = "a written account") },
        ),
    )

    @Test
    fun `prefers the US pronunciation and the first sense`() {
        val hint = WordHint.from(
            entry(
                ipa = listOf(
                    Pronunciation("UK", "/ˈrekɔːd/"),
                    Pronunciation("US", "/ˈrekərd/"),
                ),
                pos = "noun",
            ),
        )

        assertEquals("/ˈrekərd/", hint.ipa)
        assertEquals("noun", hint.partOfSpeech)
        assertTrue(hint.instruction.contains("native American English"))
        assertTrue(hint.instruction.contains("/ˈrekərd/"))
        assertTrue(hint.instruction.contains("the noun sense"))
        assertTrue(hint.instruction.contains("Say only the word"))
    }

    @Test
    fun `falls back to the first pronunciation and still gives an accent`() {
        val hint = WordHint.from(
            entry(ipa = listOf(Pronunciation("", "/bɒtʃ/"))),
        )

        assertEquals("/bɒtʃ/", hint.ipa)
        assertEquals(null, hint.partOfSpeech)
        assertTrue(hint.instruction.contains("record"))
        assertTrue(hint.instruction.contains("/bɒtʃ/"))
        assertTrue(hint.instruction.contains("native American English"))
    }

    @Test
    fun `a bare entry still asks for a native dictionary reading`() {
        val hint = WordHint.from(entry())

        assertEquals(null, hint.ipa)
        assertEquals(null, hint.partOfSpeech)
        assertTrue(hint.instruction.contains("\"record\""))
        assertTrue(hint.instruction.contains("only the word"))
    }
}
