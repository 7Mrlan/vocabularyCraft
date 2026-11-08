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

    suspend fun suggest(prefix: String): List<Suggestion>

    suspend fun random(): String?
}
