package dev.wordloom.data.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Word and sentence clips, kept on disk after the first fetch. Playback reads a
 * local file, so the only way a tap can wait is a cold cache — which is why the
 * entry prefetch warms the clip as soon as the entry arrives.
 */
class AudioCache(context: Context) {

    private val context = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun cached(url: String): File? = fileFor(url).takeIf { it.length() > 0 }

    /** Download if missing. Returns the local file, or null on failure. */
    suspend fun fetch(url: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val file = fileFor(url)
        if (file.length() > 0) return@withContext file
        runCatching {
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
            file
        }.getOrNull()
    }

    private fun fileFor(url: String): File {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        // A 32-bit hashCode can collide across a large vocabulary and could mix up
        // an .ogg fallback with an .mp3; keep the extension and use a real digest.
        val ext = url.substringBefore('?').substringAfterLast('.', "mp3")
            .lowercase()
            .takeIf { it in setOf("mp3", "ogg", "oga", "wav", "m4a") }
            ?: "mp3"
        val key = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$key.$ext")
    }
}
