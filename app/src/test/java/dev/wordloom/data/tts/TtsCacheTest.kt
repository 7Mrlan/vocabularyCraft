package dev.wordloom.data.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TtsCacheTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun file(name: String, bytes: Int, modified: Long): File =
        temporary.newFile(name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
        }

    @Test
    fun `oldest clips are removed until the budget fits`() {
        val old = file("old.wav", 8, 1_000)
        val middle = file("middle.wav", 8, 2_000)
        val recent = file("recent.wav", 8, 3_000)

        TtsCache.prune(temporary.root, maxBytes = 16)

        assertFalse(old.exists())
        assertTrue(middle.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun `the clip being written is never pruned`() {
        val keep = file("keep.wav", 12, 1_000)
        val other = file("other.wav", 20, 2_000)

        TtsCache.prune(temporary.root, maxBytes = 12, keep = keep)

        assertTrue(keep.exists())
        assertFalse(other.exists())
    }

    @Test
    fun `partial files are ignored and left alone`() {
        val partial = file("hash.part", 100, 1_000)
        val clip = file("clip.wav", 8, 2_000)

        TtsCache.prune(temporary.root, maxBytes = 8)

        assertTrue(partial.exists())
        assertTrue(clip.exists())
    }
}
