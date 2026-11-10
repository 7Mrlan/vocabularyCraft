package dev.wordloom.data.remote

import dev.wordloom.data.model.Suggestion
import dev.wordloom.data.model.WordEntry

/**
 * A pluggable content source. The app never ships dictionary text itself;
 * every definition is fetched at runtime from a source the user can pick.
 *
 * Implement this to add WordNet (offline, public domain), a local gateway,
 * or any other provider.
 */
interface DictionarySource {
    val id: String
    val displayName: String

    suspend fun lookup(term: String): WordEntry?

    /**
     * Returns an empty list for a legitimate "nothing matched". Throws when the
     * lookup itself failed, so the UI can tell "no match" apart from "no answer"
     * and offer a retry.
     */
    suspend fun suggest(prefix: String): List<Suggestion>

    suspend fun random(): String?
}
