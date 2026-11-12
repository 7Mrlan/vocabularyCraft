package dev.wordloom.data.tts

import dev.wordloom.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class TtsProtocol { MIMO, OPENAI }

/** The two ways a MiMo voice can be chosen. */
enum class TtsTemplate { Preset, Design }

/**
 * A local AI-TTS endpoint, mirroring the config the ebook project already uses.
 *
 * The key is only present in a debug build: [BuildConfig] gets empty strings in
 * release builds, so a key typed into local.properties can never ship.
 */
@Serializable
data class TtsConfig(
    val endpoint: String,
    val protocol: TtsProtocol,
    val model: String,
    val voice: String,
    val instructions: String,
    val key: String,
    /** When synthesis fails, fall back to the recorded dictionary clip. */
    val fallback: Boolean = true,
) {
    /** MiMo's voicedesign model derives the timbre from instructions; voice is ignored. */
    val voiceDesign: Boolean
        get() = protocol == TtsProtocol.MIMO && model == VOICE_DESIGN_MODEL

    val template: TtsTemplate
        get() = if (voiceDesign) TtsTemplate.Design else TtsTemplate.Preset

    /**
     * The text the service actually receives (and the cache is keyed on).
     *
     * A generated design voice has no sentence context for a bare headword, so
     * single words get a full stop: "record" is sent as "record.". Sentences
     * and multi-word phrases are untouched, and preset voices take the text
     * exactly as written.
     */
    fun requestText(text: String): String {
        val trimmed = text.trim()
        if (!voiceDesign) return trimmed
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.any { it.isWhitespace() }) return trimmed
        if (trimmed.last() in ".!?,;:\"'") return trimmed
        return "$trimmed."
    }

    val enabled: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && key.isNotBlank() &&
            (protocol == TtsProtocol.MIMO || voice.isNotBlank())

    val extension: String
        get() = if (protocol == TtsProtocol.MIMO) "wav" else "mp3"

    /** Full endpoint: accepts a base URL, a /v1 root, or the complete API path. */
    val url: String
        get() {
            val parsed = endpoint.toHttpUrlOrNull() ?: return endpoint
            val path = parsed.encodedPath.trimEnd('/')
            val suffix = if (protocol == TtsProtocol.MIMO) "/chat/completions" else "/audio/speech"
            return when {
                path.endsWith(suffix) -> parsed.toString().trimEnd('/')
                path.isEmpty() -> parsed.newBuilder().encodedPath("/v1$suffix").build().toString()
                path.endsWith("/v1") -> parsed.newBuilder().encodedPath(path + suffix).build().toString()
                else -> parsed.toString()
            }
        }

    fun request(text: String): ByteArray {
        val root = JSONObject().put("model", model)
        if (protocol == TtsProtocol.MIMO) {
            val messages = JSONArray()
            // Preset voices reject the design description; only the design
            // template carries it. Keeping it stored lets a switch back to
            // Design restore the reader's own words.
            if (voiceDesign && instructions.isNotBlank()) {
                messages.put(JSONObject().put("role", "user").put("content", instructions.trim()))
            }
            messages.put(JSONObject().put("role", "assistant").put("content", text))
            val audio = JSONObject().put("format", "wav")
            // The preview flag is voicedesign-only; preset voices reject it.
            if (voiceDesign) audio.put("optimize_text_preview", false)
            if (!voiceDesign && voice.isNotBlank()) audio.put("voice", voice)
            root.put("messages", messages).put("audio", audio).put("stream", false)
        } else {
            root.put("voice", voice).put("input", text).put("response_format", "mp3")
            if (instructions.isNotBlank()) root.put("instructions", instructions.trim())
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    override fun toString(): String =
        "TtsConfig(endpoint=$endpoint, protocol=$protocol, model=$model, voice=$voice, key=<redacted>)"

    companion object {
        const val VOICE_DESIGN_MODEL = "mimo-v2.5-tts-voicedesign"

        /** The encrypted file is JSON; kotlinx keeps it typed and testable. */
        private val storage = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        internal fun encode(config: TtsConfig): String =
            storage.encodeToString(TtsConfig.serializer(), config)

        internal fun decode(text: String): TtsConfig? =
            runCatching { storage.decodeFromString(TtsConfig.serializer(), text) }.getOrNull()

        fun fromBuildConfig(): TtsConfig {
            val protocol = runCatching {
                TtsProtocol.valueOf(BuildConfig.TTS_PROTOCOL.trim().uppercase())
            }.getOrDefault(TtsProtocol.MIMO)
            return TtsConfig(
                endpoint = BuildConfig.TTS_ENDPOINT.trim(),
                protocol = protocol,
                model = BuildConfig.TTS_MODEL.trim(),
                voice = BuildConfig.TTS_VOICE.trim(),
                instructions = BuildConfig.TTS_INSTRUCTIONS.trim(),
                key = BuildConfig.TTS_KEY.trim(),
            )
        }
    }
}

/** The recommended setup: a preset English voice, with the design voice one tap away. */
object TtsDefaults {
    const val ENDPOINT = "https://api.xiaomimimo.com/v1"
    const val PRESET_MODEL = "mimo-v2.5-tts"
    const val DESIGN_MODEL = TtsConfig.VOICE_DESIGN_MODEL
    const val MODEL = PRESET_MODEL
    const val VOICE = "Chloe"
    val VOICES = listOf(
        "Chloe" to "女声 · 官方英文示例",
        "Mia" to "女声 · 明亮",
        "Milo" to "男声 · 沉稳",
        "Dean" to "男声 · 低沉",
    )
    const val INSTRUCTIONS =
        "温润知性的成熟女声，中低音，音色干净，吐字清晰，语速从容，句末自然下落，不过分甜。"
    const val PREVIEW = "Never make your students feel ignorant."
    val PREVIEW_WORDS = listOf("record", "read", "lead", "close", "use", "live")

    fun recommended(key: String = "") = TtsConfig(
        endpoint = ENDPOINT,
        protocol = TtsProtocol.MIMO,
        model = MODEL,
        voice = VOICE,
        instructions = INSTRUCTIONS,
        key = key,
    )
}
