package dev.wordloom.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dev.wordloom.data.audio.AudioCache
import dev.wordloom.data.audio.AudioFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

sealed interface SpeechState {
    data object Idle : SpeechState
    data object Loading : SpeechState
    data class Playing(val source: String) : SpeechState
    data class Failed(val message: String) : SpeechState
}

data class TtsStatus(
    val enabled: Boolean,
    val endpoint: String,
    val protocol: String,
    val model: String,
    val voice: String,
    val instructions: String,
    val hasKey: Boolean,
    val fallback: Boolean = true,
)

/**
 * AI first, existing URL audio as fallback. Synthesis is cached on disk and can
 * be warmed before the reader taps; the player only ever opens a local file.
 *
 * The live config comes from [TtsConfigStore] when present; a debug-only
 * bootstrap may seed the first run, but release builds never carry a key.
 */
class SpeechService(
    context: Context,
    bootstrap: TtsConfig,
    private val store: TtsConfigStore,
    private val audioCache: AudioCache,
) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "tts").apply { mkdirs() }
    private val client = TtsClient()
    private val audioFocus = AudioFocus(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = Semaphore(2)
    private val inFlight = ConcurrentHashMap<String, Deferred<File?>>()
    private var job: Job? = null
    private var player: MediaPlayer? = null

    @Volatile
    private var config: TtsConfig = store.load() ?: bootstrap

    /** The last synthesis failure, shown when the recorded fallback is off. */
    @Volatile
    private var lastError: String? = null

    init {
        scope.launch(Dispatchers.IO) { TtsCache.prune(directory) }
    }

    val enabled: Boolean get() = config.enabled

    val status: TtsStatus
        get() = TtsStatus(
            enabled = config.enabled,
            endpoint = config.endpoint,
            protocol = config.protocol.name,
            model = config.model,
            voice = config.voice,
            instructions = config.instructions,
            hasKey = config.key.isNotBlank(),
            fallback = config.fallback,
        )

    /** Persist a complete config; a blank key reuses the saved one. */
    fun saveConfig(draft: TtsConfig): Result<Unit> {
        val resolved = if (draft.key.isBlank() && config.key.isNotBlank()) {
            draft.copy(key = config.key)
        } else {
            draft
        }
        return runCatching {
            require(resolved.enabled) { "AI 朗读配置不完整，请检查接口、模型和密钥" }
            store.save(resolved)
            config = resolved
            stop()
        }
    }

    /** Preview an unsaved draft without changing the active config. */
    fun preview(draft: TtsConfig, text: String, onResult: (Result<Unit>) -> Unit) {
        job?.cancel()
        stopPlayer()
        job = scope.launch {
            val result = try {
                val resolved = if (draft.key.isBlank() && config.key.isNotBlank()) {
                    draft.copy(key = config.key)
                } else {
                    draft
                }
                require(resolved.enabled) { "AI 朗读配置不完整，请检查接口、模型和密钥" }
                val temporary = File(directory, "preview-${System.nanoTime()}.${resolved.extension}")
                try {
                    client.synthesize(resolved, resolved.requestText(text), temporary)
                    var started = false
                    playFile(temporary, "preview") { state ->
                        if (state is SpeechState.Playing) started = true
                    }
                    if (!started) throw IOException("无法播放试听音频")
                } finally {
                    temporary.delete()
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            onResult(result)
        }
    }

    /**
     * One-shot setup test: synthesize with an unsaved draft, play it, and hand
     * back the synthesis time. Used by onboarding and never written to cache.
     */
    fun test(draft: TtsConfig, text: String, onResult: (Result<TtsResult>) -> Unit) {
        job?.cancel()
        stopPlayer()
        job = scope.launch {
            var temporary: File? = null
            val result = try {
                val resolved = if (draft.key.isBlank() && config.key.isNotBlank()) {
                    draft.copy(key = config.key)
                } else {
                    draft
                }
                require(resolved.enabled) { "AI 朗读配置不完整，请检查接口、模型和密钥" }
                val file = File(directory, "test-${System.nanoTime()}.${resolved.extension}")
                temporary = file
                val synthesized = client.synthesize(resolved, resolved.requestText(text), file)
                var started = false
                playFile(file, "test") { state ->
                    if (state is SpeechState.Playing) started = true
                }
                if (!started) throw IOException("无法播放测试音频")
                Result.success(synthesized)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            } finally {
                temporary?.delete()
            }
            onResult(result)
        }
    }

    /** Warm a clip without playing it; safe to call repeatedly. */
    fun prefetch(text: String) {
        if (!enabled || text.isBlank()) return
        scope.launch { prepare(text) }
    }

    /**
     * Play AI audio first. When the reader has turned the recorded-clip fallback
     * off, a failed synthesis is retried once and then reported — the old URL is
     * never played, so the voice stays the one they configured.
     */
    fun speak(text: String, fallbackUrl: String?, onState: (SpeechState) -> Unit = {}) {
        job?.cancel()
        stopPlayer()
        job = scope.launch {
            onState(SpeechState.Loading)
            var synthesized = if (enabled) prepare(text) else null
            if (synthesized == null && enabled) {
                // One retry: transient network failures are the common case.
                synthesized = prepare(text, force = true)
            }
            if (synthesized != null) {
                playFile(synthesized, "AI", onState)
                return@launch
            }
            if (enabled && !config.fallback) {
                val reason = lastError?.takeIf { it.isNotBlank() }
                onState(
                    SpeechState.Failed(
                        if (reason != null) "朗读失败：$reason" else "朗读失败，可重试",
                    ),
                )
                return@launch
            }
            val fallback = fallbackUrl?.takeIf { it.isNotBlank() }?.let { audioCache.fetch(it) }
            if (fallback != null) {
                playFile(fallback, "fallback", onState)
            } else {
                onState(SpeechState.Failed("没有可用发音"))
            }
        }
    }

    /**
     * Forget the stored key and keep the rest of the configuration. A keyless
     * config is still written, so the endpoint, model and voice survive the
     * next launch; synthesis simply stays disabled until a new key is entered.
     */
    fun forgetKey(): Boolean {
        stop()
        val cleared = config.copy(key = "")
        val saved = runCatching { store.save(cleared, allowKeyless = true) }
            .onFailure { android.util.Log.w("Wordloom", "clear TTS key failed", it) }
            .isSuccess
        config = cleared
        return saved
    }

    fun stop() {
        job?.cancel()
        job = null
        stopPlayer()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun prepare(text: String, force: Boolean = false): File? {
        if (!enabled || text.isBlank()) return null
        val request = config.requestText(text)
        val key = cacheKey(request)
        val target = File(directory, "$key.${config.extension}")
        if (!force && target.isFile && target.length() > 0) {
            // Refresh LRU position without changing the clip.
            target.setLastModified(System.currentTimeMillis())
            return target
        }

        val existing = if (!force) inFlight[key] else null
        if (existing != null) return existing.await()

        val work = scope.async(Dispatchers.IO) {
            gate.withPermit {
                if (!force && target.isFile && target.length() > 0) return@withPermit target
                val temporary = File(directory, "$key-${System.nanoTime()}.part")
                try {
                    client.synthesize(config, request, temporary)
                    if (!temporary.isFile || temporary.length() == 0L) return@withPermit null
                    if (!temporary.renameTo(target)) {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                    }
                    TtsCache.prune(directory, keep = target)
                    target
                } catch (cancelled: CancellationException) {
                    temporary.delete()
                    throw cancelled
                } catch (error: Throwable) {
                    lastError = error.message
                    temporary.delete()
                    null
                }
            }
        }
        inFlight[key] = work
        work.invokeOnCompletion { inFlight.remove(key, work) }
        return work.await()
    }

    private suspend fun playFile(file: File, source: String, onState: (SpeechState) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            stopPlayer()
            val media = MediaPlayer()
            player = media
            val finish: (Boolean) -> Unit = { failed ->
                runCatching { media.release() }
                if (player === media) player = null
                audioFocus.abandon()
                if (continuation.isActive) {
                    onState(
                        if (failed) SpeechState.Failed("无法播放音频")
                        else SpeechState.Idle,
                    )
                    continuation.resume(Unit)
                }
            }
            if (!audioFocus.request { finish(false) }) {
                runCatching { media.release() }
                if (player === media) player = null
                onState(SpeechState.Failed("音频被其他应用占用"))
                if (continuation.isActive) continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            try {
                media.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener {
                    it.start()
                    onState(SpeechState.Playing(source))
                }
                media.setOnCompletionListener { finish(false) }
                media.setOnErrorListener { _, _, _ ->
                    finish(true)
                    true
                }
                media.prepareAsync()
            } catch (_: Throwable) {
                finish(true)
            }
            continuation.invokeOnCancellation {
                runCatching { media.release() }
                if (player === media) player = null
                audioFocus.abandon()
            }
        }
    }

    private fun stopPlayer() {
        runCatching { player?.release() }
        player = null
        audioFocus.abandon()
    }

    private fun cacheKey(text: String): String {
        val identity = listOf(
            // Bump when the request shape changes; old clips then stop matching.
            "tts-v2",
            config.protocol.name,
            config.endpoint,
            config.model,
            config.voice,
            config.instructions,
            text,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .take(20)
            .joinToString("") { "%02x".format(it) }
    }
}
