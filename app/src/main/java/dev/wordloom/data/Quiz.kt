package dev.wordloom.data

import dev.wordloom.data.model.WordEntry

/** One four-choice question: a word and the gloss that belongs to it. */
data class QuizQuestion(
    val word: String,
    val answer: String,
    val options: List<String>,
) {
    val answerIndex: Int get() = options.indexOf(answer)
}

/**
 * Builds a round from what is already on the device. No network, no scheduling
 * side effects: this is practice, and the FSRS queue is left untouched.
 */
object QuizBuilder {

    /** Keeps an option to roughly two lines. */
    private fun String.shorten(max: Int = 96): String {
        if (length <= max) return this
        val cut = lastIndexOf(' ', max).let { if (it <= 0) max else it }
        return substring(0, cut).trimEnd().trimEnd(',', ';') + "…"
    }

    fun build(
        entries: List<WordEntry>,
        size: Int = 10,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<QuizQuestion> {
        val pool = entries
            .mapNotNull { entry ->
                // The first sense definition is a gloss; the blurb is a paragraph
                // and would give the answer away (and overflow the option row).
                val gloss = entry.senses.firstOrNull()?.definition?.takeIf { it.isNotBlank() }
                    ?: entry.shortBlurb?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                entry.word to gloss.shorten()
            }
            .distinctBy { it.second }
            .distinctBy { it.first.lowercase() }

        if (pool.size < 4) return emptyList()

        val picked = pool.shuffled(random).take(size)
        return picked.mapNotNull { (word, gloss) ->
            val distractors = pool
                .filter { it.first != word && it.second != gloss }
                .shuffled(random)
                .map { it.second }
                .distinct()
                .take(3)
            if (distractors.size < 3) return@mapNotNull null
            val options = (distractors + gloss).shuffled(random)
            QuizQuestion(word = word, answer = gloss, options = options)
        }
    }
}
