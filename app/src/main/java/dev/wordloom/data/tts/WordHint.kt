package dev.wordloom.data.tts

import dev.wordloom.data.model.WordEntry

/**
 * The context a single word needs but a sentence already has.
 *
 * MiMo reads isolated words with no sense, stress or accent information, which
 * is why heteronyms such as record / read / lead came out wrong. The model's
 * user message is a director's note and is never spoken, so we can put the part
 * of speech and the dictionary pronunciation there while the assistant message
 * stays the bare word.
 */
data class WordHint(
    val word: String,
    val ipa: String? = null,
    val partOfSpeech: String? = null,
) {
    val instruction: String
        get() = buildString {
            append("Read this English word in a clear, native American English dictionary voice.")
            append(" The word is \"")
            append(word)
            append("\".")
            partOfSpeech?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(" It is the ")
                append(it)
                append(" sense.")
            }
            ipa?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(" It is pronounced ")
                append(it)
                append(".")
            }
            append(" Stress the correct syllable. Say only the word, with no extra words.")
        }

    companion object {
        fun from(entry: WordEntry): WordHint = WordHint(
            word = entry.word,
            ipa = entry.ipa.firstOrNull { it.label.equals("US", ignoreCase = true) }?.text
                ?: entry.ipa.firstOrNull()?.text,
            partOfSpeech = entry.senses.firstOrNull()?.partOfSpeech,
        )
    }
}
