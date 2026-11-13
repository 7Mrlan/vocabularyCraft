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

        val noVoice = draft.copy(voice = "")
        assertEquals("请选择音色", noVoice.validate(hasSavedKey = true))
    }

    @Test
    fun `each template maps to exactly one model`() {
        assertEquals(TtsDefaults.PRESET_MODEL, TtsTemplate.Preset.model)
        assertEquals(TtsConfig.VOICE_DESIGN_MODEL, TtsTemplate.Design.model)
    }

    @Test
    fun `only providers that read the description put it in the cache key`() {
        assertEquals("", preset().effectiveInstructions)
        assertEquals("a described voice", design().effectiveInstructions)
        val openAi = preset().copy(protocol = TtsProtocol.OPENAI, instructions = "dramatic")
        assertEquals("dramatic", openAi.effectiveInstructions)
    }

    @Test
    fun `a decorated pasted key is cleaned for the wire`() {
        assertEquals("sk-abc", " Bearer sk-abc ".normalizeApiKey())
        assertEquals("abc", "\"abc\"".normalizeApiKey())
        assertEquals("abc", "abc\n".normalizeApiKey())
        assertEquals("abc", preset().copy(key = "Bearer abc").requestKey)
        assertTrue(preset().copy(key = "Bearer abc").enabled)
        assertFalse(preset().copy(key = "Bearer ").enabled)
    }

    @Test
    fun `a custom setup survives untouched but a template pick replaces it`() {
        val base = TtsDraft(
            endpoint = "https://example.com/v1",
            protocol = TtsProtocol.OPENAI,
            model = "tts-1",
            voice = "alloy",
            instructions = "dramatic",
            key = "k",
        )

        val kept = base.forTemplate(
            template = TtsTemplate.Preset,
            keepCustom = true,
            customProtocol = TtsProtocol.OPENAI,
            customModel = "tts-1",
            customVoice = "alloy",
            presetVoice = "Chloe",
        )
        assertEquals(TtsProtocol.OPENAI, kept.protocol)
        assertEquals("tts-1", kept.model)
        assertEquals("alloy", kept.voice)
        assertEquals("dramatic", kept.instructions)

        val preset = base.forTemplate(
            template = TtsTemplate.Preset,
            keepCustom = false,
            customProtocol = TtsProtocol.OPENAI,
            customModel = "tts-1",
            customVoice = "alloy",
            presetVoice = "Mia",
        )
        assertEquals(TtsProtocol.MIMO, preset.protocol)
        assertEquals(TtsDefaults.PRESET_MODEL, preset.model)
        assertEquals("Mia", preset.voice)

        val designed = base.forTemplate(
            template = TtsTemplate.Design,
            keepCustom = false,
            customProtocol = TtsProtocol.OPENAI,
            customModel = "tts-1",
            customVoice = "alloy",
            presetVoice = "Mia",
        )
        assertEquals(TtsConfig.VOICE_DESIGN_MODEL, designed.model)
        assertEquals("", designed.voice)
    }
}
