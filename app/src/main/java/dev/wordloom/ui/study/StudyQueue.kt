package dev.wordloom.ui.study

import dev.wordloom.data.local.CardEntity

/**
 * Choosing the new words for a session. A word counts as "learned" the moment
 * it has a card — due or not — otherwise a session's words come back as new on
 * the next session and the queue never leaves its first group.
 */
object StudyQueue {

    const val NEW_PER_SESSION = 10

    fun newWords(
        unit: List<String>,
        scheduled: List<CardEntity>,
        limit: Int = NEW_PER_SESSION,
    ): List<String> {
        // A unit has 100 words and the deck can hold thousands; a Set keeps this
        // linear instead of 100 × N string comparisons on every card change.
        val known = scheduled.mapTo(HashSet(scheduled.size)) { it.word.lowercase() }
        return unit.asSequence()
            .filter { it.lowercase() !in known }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
    }
}
