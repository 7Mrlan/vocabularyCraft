package dev.wordloom.data.tts

import java.io.File
import java.security.MessageDigest

/**
 * The TTS cache is a convenience, not a second database. Keep it bounded so a
 * reader who studies for months never loses storage to old clips; the file
 * being written right now is always kept.
 */
object TtsCache {

    const val MAX_BYTES: Long = 128L * 1024 * 1024

    /**
     * The identity of one clip. Every input that can change the audio is in the
     * key, so switching template, voice or description can never replay an old
     * file. Preset voices ignore instructions, so their key must too — otherwise
     * editing a description would silently duplicate cache entries.
     */
    fun key(
        protocol: TtsProtocol,
        endpoint: String,
        model: String,
        voice: String,
        instructions: String,
        text: String,
    ): String {
        val identity = listOf(
            // Bump when the request shape changes; old clips then stop matching.
            "tts-v2",
            protocol.name,
            endpoint,
            model,
            voice,
            instructions,
            text,
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray())
            .take(20)
            .joinToString("") { "%02x".format(it) }
    }

    fun prune(directory: File, maxBytes: Long = MAX_BYTES, keep: File? = null) {
        val files = directory.listFiles()
            ?.filter {
                it.isFile && it != keep &&
                    !it.name.endsWith(".part") &&
                    !it.name.startsWith("preview-") &&
                    !it.name.startsWith("test-")
            }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return@forEach
            val length = file.length()
            if (file.delete()) total -= length
        }
    }
}
