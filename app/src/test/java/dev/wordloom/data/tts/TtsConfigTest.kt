package dev.wordloom.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsConfigTest {

    private fun design() = TtsConfig(
        endpoint = "https://example.com/v1",
        protocol = TtsProtocol.MIMO,
        model = TtsConfig.VOICE_DESIGN_MODEL,
        voice = "",
        instructions = "a described voice",
        key = "k",
    )

    private fun preset() = TtsConfig(
        endpoint = "https://example.com/v1",
        protocol = TtsProtocol.MIMO,
        model = TtsDefaults.PRESET_MODEL,
        voice = TtsDefaults.VOICE,
        instructions = "",
        key = "k",
    )

    @Test
    fun `design voice sends a single word with a full stop`() {
        assertEquals("record.", design().requestText("record"))
        assertEquals("Record.", design().requestText("  Record "))
    }

    @Test
    fun `design voice leaves sentences and phrases alone`() {
        assertEquals(
            "Never make your students feel ignorant.",
            design().requestText("Never make your students feel ignorant."),
        )
        assertEquals("give up", design().requestText("give up"))
        assertEquals("well-known.", design().requestText("well-known."))
    }

    @Test
    fun `a long single token still gets its full stop`() {
        val word = "pneumonoultramicroscopicsilicovolcanoconiosis"
        assertEquals("$word.", design().requestText(word))
    }

    @Test
    fun `preset voices never rewrite the text`() {
        assertEquals("record", preset().requestText("record"))
        assertEquals("Never make your students feel ignorant.", preset().requestText("Never make your students feel ignorant."))
    }

    @Test
    fun `fallback defaults to on and survives the encrypted store shape`() {
        assertTrue(preset().fallback)
        val off = preset().copy(fallback = false)
        val text = TtsConfig.encode(off)
        assertTrue(text.contains("\"fallback\":false"))
        assertFalse(TtsConfig.decode(text)!!.fallback)
    }

    @Test
    fun `draft validation matches what the service will accept`() {
        val draft = TtsDraft(
            endpoint = "https://example.com/v1",
            protocol = TtsProtocol.MIMO,
            model = TtsDefaults.PRESET_MODEL,
            voice = TtsDefaults.VOICE,
            instructions = "",
            key = "",
        )
        assertEquals("请填写 API 密钥", draft.validate(hasSavedKey = false))
        assertEquals(null, draft.validate(hasSavedKey = true))

        val design = draft.copy(model = TtsConfig.VOICE_DESIGN_MODEL, instructions = "")
        assertEquals("请填写音色描述", design.validate(hasSavedKey = true))

        val openAi = draft.copy(protocol = TtsProtocol.OPENAI, voice = "")
        assertEquals("请填写声音名称", openAi.validate(hasSavedKey = true))
    }
}
