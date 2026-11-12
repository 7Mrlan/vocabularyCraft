package dev.wordloom.data.audio

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Word and sentence clips, kept on disk after the first fetch.
 *
 * One instance is shared by the whole app: [shared] backs the study cards, the
 * word page and the prefetch queue, so they all reuse a single connection pool
 * and a single in-flight table. A per-composable client used to mean every
 * speaker button on a page built its own OkHttpClient.
 */
class AudioCache(context: Context) {

    private val context = context.applicationContext

    /** One download per URL: the prefetch and a fast tap would otherwise write
     *  the same `.part` file at the same time and corrupt the clip. */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File?>>()

    fun cached(url: String): File? = fileFor(url).takeIf { it.length() > 0 }

    /** Download if missing. Returns the local file, or null on failure. */
    suspend fun fetch(url: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val file = fileFor(url)
        if (file.length() > 0) return@withContext file

        val gate = CompletableDeferred<File?>()
        val running = inFlight.putIfAbsent(url, gate)
        if (running != null) return@withContext running.await()

        try {
            val result = download(url, file)
            gate.complete(result)
            result
        } catch (cancelled: CancellationException) {
            // Waiters get "no clip" instead of being cancelled with us.
            gate.complete(null)
            throw cancelled
        } catch (_: Throwable) {
            gate.complete(null)
            null
        } finally {
            inFlight.remove(url, gate)
        }
    }

    private fun download(url: String, file: File): File {
        val temp = File(file.parentFile, file.name + ".part")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("audio ${response.code}")
            val body = response.body ?: error("empty audio body")
            temp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        return file
    }

    private fun fileFor(url: String): File {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        // A 32-bit hashCode can collide across a large vocabulary and could mix up
        // an .ogg fallback with an .mp3; keep the extension and use a real digest.
        val ext = url.substringBefore('?').substringAfterLast('.', "mp3")
            .lowercase()
            .takeIf { it in AUDIO_EXTENSIONS }
            ?: "mp3"
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$key.$ext")
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "oga", "wav", "m4a")

        /** Shared across the app: one client, one connection pool, one queue. */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        @Volatile
        private var instance: AudioCache? = null

        fun shared(context: Context): AudioCache =
            instance ?: synchronized(this) {
                instance ?: AudioCache(context.applicationContext).also { instance = it }
            }
    }
}
