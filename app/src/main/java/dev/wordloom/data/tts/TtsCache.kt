package dev.wordloom.data.tts

import java.io.File

/**
 * The TTS cache is a convenience, not a second database. Keep it bounded so a
 * reader who studies for months never loses storage to old clips; the file
 * being written right now is always kept.
 */
object TtsCache {

    const val MAX_BYTES: Long = 128L * 1024 * 1024

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
