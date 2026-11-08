package dev.wordloom.srs

const val DAY_MS = 24L * 60L * 60L * 1000L

enum class Rating(val value: Int, val label: String) {
    Again(1, "重来"),
    Hard(2, "困难"),
    Good(3, "良好"),
    Easy(4, "容易"),
}

data class CardProgress(
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewAt: Long = 0L,
    val dueAt: Long = 0L,
)

data class ReviewOutcome(
    val progress: CardProgress,
    val intervalDays: Int,
)

object Scheduler {

    fun review(current: CardProgress, rating: Rating, now: Long = System.currentTimeMillis()): ReviewOutcome {
        val isFirst = current.reps == 0
        val stability: Double
        val difficulty: Double
        var lapses = current.lapses

        if (isFirst) {
            stability = Fsrs.initStability(rating.value)
            difficulty = Fsrs.initDifficulty(rating.value)
        } else {
            val elapsedDays = ((now - current.lastReviewAt).coerceAtLeast(0L)).toDouble() / DAY_MS
            val r = Fsrs.retrievability(elapsedDays, current.stability)
            difficulty = Fsrs.nextDifficulty(current.difficulty, rating.value)
            stability = if (rating == Rating.Again) {
                lapses += 1
                Fsrs.stabilityAfterForget(difficulty, current.stability, r)
            } else {
                Fsrs.stabilityAfterRecall(difficulty, current.stability, r)
            }
        }

        val interval = Fsrs.intervalDays(stability)
        val next = CardProgress(
            stability = stability,
            difficulty = difficulty,
            reps = current.reps + 1,
            lapses = lapses,
            lastReviewAt = now,
            dueAt = now + interval * DAY_MS,
        )
        return ReviewOutcome(next, interval)
    }
}
