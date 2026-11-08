package dev.wordloom.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WordEntry(
    val word: String,
    val ipa: List<Pronunciation> = emptyList(),
    val audio: List<AudioClip> = emptyList(),
    val forms: String? = null,
    val shortBlurb: String? = null,
    val longBlurb: String? = null,
    val etymology: String? = null,
    /** Words built from the same root, e.g. great → greater, greatly, greatness. */
    val wordFamily: List<String> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val sourceName: String = "Vocabulary.com",
    val sourceUrl: String = "",
    val license: String = "",
    val fetchedAt: Long = 0L,
) {
    val hasContent: Boolean get() = senses.isNotEmpty() || !shortBlurb.isNullOrBlank()
}

@Serializable
data class Pronunciation(
    val label: String,
    val text: String,
)

@Serializable
data class AudioClip(
    val label: String,
    val url: String,
)

@Serializable
data class SynonymGroup(
    /** The sense qualifier in parentheses, e.g. "moving with speed". */
    val sense: String = "",
    val words: List<String> = emptyList(),
)

/** One row in the search-suggestion list. */
@Serializable
data class Suggestion(
    val word: String,
    /** Short gloss shown under the word, as on the reference site. */
    val definition: String = "",
)

@Serializable
data class Sense(
    val partOfSpeech: String,
    val definition: String,
    val examples: List<String> = emptyList(),
    val synonyms: List<SynonymGroup> = emptyList(),
    val synonymsList: List<String> = emptyList(),
)
