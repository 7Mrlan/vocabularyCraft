package dev.wordloom.data.tts

import dev.wordloom.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class TtsProtocol { MIMO, OPENAI }

/**
 * A local AI-TTS endpoint, mirroring the config the ebook project already uses.
 *
 * The key is only present in a debug build: [BuildConfig] gets empty strings in
 * release builds, so a key typed into local.properties can never ship.
 */
data class TtsConfig(
    val endpoint: String,
    val protocol: TtsProtocol,
    val model: String,
    val voice: String,
    val instructions: String,
    val key: String,
) {
    /** MiMo's voicedesign model derives the timbre from instructions; voice is ignored. */
    val voiceDesign: Boolean
        get() = protocol == TtsProtocol.MIMO && model == VOICE_DESIGN_MODEL

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
            if (instructions.isNotBlank()) {
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

    internal fun toJson(): JSONObject = JSONObject()
        .put("endpoint", endpoint)
        .put("protocol", protocol.name)
        .put("model", model)
        .put("voice", voice)
        .put("instructions", instructions)
        .put("key", key)

    companion object {
        const val VOICE_DESIGN_MODEL = "mimo-v2.5-tts-voicedesign"

        internal fun fromJson(json: JSONObject): TtsConfig {
            val protocol = runCatching {
                TtsProtocol.valueOf(json.optString("protocol", "MIMO").uppercase())
            }.getOrDefault(TtsProtocol.MIMO)
            return TtsConfig(
                endpoint = json.optString("endpoint", ""),
                protocol = protocol,
                model = json.optString("model", ""),
                voice = json.optString("voice", ""),
                instructions = json.optString("instructions", ""),
                key = json.optString("key", ""),
            )
        }

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

/** The one recommended setup: the voice the reader chose during design work. */
object TtsDefaults {
    const val ENDPOINT = "https://api.xiaomimimo.com/v1"
    const val MODEL = TtsConfig.VOICE_DESIGN_MODEL
    const val INSTRUCTIONS =
        "温润知性的成熟女声，中低音，音色干净，吐字清晰，语速从容，句末自然下落，不过分甜。"
    const val PREVIEW = "Never make your students feel ignorant."

    fun recommended(key: String = "") = TtsConfig(
        endpoint = ENDPOINT,
        protocol = TtsProtocol.MIMO,
        model = MODEL,
        voice = "",
        instructions = INSTRUCTIONS,
        key = key,
    )
}
