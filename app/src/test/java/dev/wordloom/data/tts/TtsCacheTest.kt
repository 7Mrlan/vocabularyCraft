package dev.wordloom.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private fun key(
        model: String = TtsDefaults.PRESET_MODEL,
        voice: String = "Chloe",
        instructions: String = "",
        text: String = "record",
        hint: String = "",
    ) = TtsCache.key(
        protocol = TtsProtocol.MIMO,
        endpoint = TtsDefaults.ENDPOINT,
        model = model,
        voice = voice,
        instructions = instructions,
        text = text,
        hint = hint,
    )

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

    @Test
    fun `the same request is the same clip`() {
        assertEquals(key(), key())
    }

    @Test
    fun `model voice and text each change the identity`() {
        assertNotEquals(key(), key(model = TtsConfig.VOICE_DESIGN_MODEL))
        assertNotEquals(key(), key(voice = "Mia"))
        assertNotEquals(key(), key(text = "record."))
    }

    @Test
    fun `a word hint changes the identity and an empty hint does not`() {
        assertEquals(key(), key(hint = ""))
        assertNotEquals(key(), key(hint = "read as a noun"))
    }

    @Test
    fun `a new description cannot replay an old design clip`() {
        assertNotEquals(
            key(model = TtsConfig.VOICE_DESIGN_MODEL, instructions = "warm"),
            key(model = TtsConfig.VOICE_DESIGN_MODEL, instructions = "bright"),
        )
    }

    @Test
    fun `the key stays filesystem-safe`() {
        val value = key(text = "../../etc/passwd")
        assertEquals(40, value.length)
        assertTrue(value.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
