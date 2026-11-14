package dev.wordloom.data.tts

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsResult(
    val elapsedMillis: Long,
    val bytes: Long,
    val source: String = "AI",
)

/**
 * One short synthesis, no retries: a word or a single example sentence is on
 * the interactive path, so a slow provider must fail fast and let the caller
 * fall back instead of stacking 300 ms retries in front of the reader.
 */
class TtsClient(private val client: OkHttpClient = defaultClient()) {

    suspend fun synthesize(
        config: TtsConfig,
        text: String,
        target: File,
        hint: WordHint? = null,
    ): TtsResult {
        require(config.enabled) { "AI 语音未配置" }
        require(text.isNotBlank()) { "没有可朗读的文本" }

        val request = Request.Builder()
            .url(config.url)
            .header("Content-Type", "application/json")
            .apply {
                if (config.protocol == TtsProtocol.MIMO) {
                    header("api-key", config.requestKey)
                } else {
                    header("Authorization", "Bearer ${config.requestKey}")
                }
            }
            .post(config.request(text, hint).toRequestBody(JSON))
            .build()

        val started = System.nanoTime()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
                target.delete()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    target.delete()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException(
                                if (error is SocketTimeoutException) "AI 语音请求超时"
                                else "无法连接 AI 语音服务，请检查网络与接口地址",
                                error,
                            ),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!response.isSuccessful) {
                                throw IOException(failureMessage(response))
                            }
                            val body = response.body ?: throw IOException("AI 语音没有返回音频")
                            val limit = if (config.protocol == TtsProtocol.MIMO) MAX_JSON else MAX_BYTES
                            if (body.contentLength() > limit) {
                                throw IOException("AI 语音响应过大")
                            }
                            if (config.protocol == TtsProtocol.MIMO) {
                                // MiMo's chat/completions endpoint returns JSON with
                                // base64 audio in choices[0].message.audio.data,
                                // not a raw WAV body.
                                writeMiMo(body.bytes(), target)
                            } else {
                                body.byteStream().use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            requireAudio(target)
                        }
                        if (!continuation.isActive) {
                            target.delete()
                            return
                        }
                        continuation.resume(
                            TtsResult(
                                elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                                bytes = target.length(),
                            ),
                        )
                    } catch (error: Exception) {
                        target.delete()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    /** A 401 is almost always the pasted key, not the model; say so. */
    private fun failureMessage(response: Response): String {
        val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val detail = raw
            .replace(Regex("(?i)sk-[A-Za-z0-9_-]+"), "sk-***")
            .replace(Regex("(?i)(api[-_]?key|authorization|bearer)\\s*[:=]?\\s*\\S+"), "$1 ***")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(160)
        val prefix = when (response.code) {
            401, 403 -> "API 密钥无效或没有权限（${response.code}），请清除后重新粘贴完整密钥"
            404 -> "AI 语音接口或模型不存在（404）"
            else -> "AI 语音服务返回 ${response.code}"
        }
        return if (detail.isBlank()) prefix else "$prefix：$detail"
    }

    private fun writeMiMo(bytes: ByteArray, target: File) {
        if (bytes.isEmpty()) throw IOException("AI 语音没有返回音频")
        if (bytes.size > MAX_JSON) throw IOException("AI 语音响应过大")
        val root = try {
            JSONObject(bytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            throw IOException("AI 语音响应格式无效，请检查协议与地址", error)
        }
        if (root.has("error") && !root.isNull("error")) {
            throw IOException("AI 语音服务返回错误，请检查模型、权限与额度")
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
            ?: throw IOException("AI 语音响应格式无效，请检查协议与模型")
        val finish = choice.optString("finish_reason", "")
        if (finish.isNotBlank() && finish != "stop") {
            throw IOException(
                if (finish == "content_filter") "AI 语音拒绝合成这段文字"
                else "AI 语音没有正常完成，请检查模型与文本",
            )
        }
        val message = choice.optJSONObject("message")
            ?: throw IOException("AI 语音响应格式无效，请检查协议与模型")
        if (!message.isNull("refusal") && message.optString("refusal", "").isNotBlank()) {
            throw IOException("AI 语音拒绝合成这段文字")
        }
        val audio = message.optJSONObject("audio")
            ?: throw IOException("AI 语音没有返回音频")
        val encoded = audio.optString("data", "")
        if (encoded.isBlank()) throw IOException("AI 语音没有返回音频")
        if (encoded.length.toLong() > (MAX_BYTES + 2) / 3 * 4) {
            throw IOException("AI 语音响应过大")
        }
        val decoded = try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw IOException("AI 语音音频编码无效", error)
        }
        target.writeBytes(decoded)
    }

    private fun requireAudio(target: File) {
        if (!target.isFile || target.length() < 12) error("AI 语音没有返回有效音频")
        val header = ByteArray(12)
        val count = target.inputStream().use { it.read(header) }
        val wav = count == 12 &&
            header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())
        val mp3 = count >= 3 && (
            header.copyOfRange(0, 3).contentEquals("ID3".toByteArray()) ||
                ((header[0].toInt() and 0xff) == 0xff && (header[1].toInt() and 0xe0) == 0xe0)
            )
        require(wav || mp3) { "AI 语音返回的不是 WAV/MP3" }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BYTES = 16L * 1024 * 1024
        private const val MAX_JSON = 24L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
