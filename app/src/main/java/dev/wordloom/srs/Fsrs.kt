package dev.wordloom.srs

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-5 long-term scheduler, implemented from the published algorithm
 * (https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler).
 *
 * Only whole-day intervals are used: a failed card ("Again") comes back
 * tomorrow rather than in 10 minutes, which keeps the queue predictable.
 */
object Fsrs {

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0

    /** FSRS-5 default weights. */
    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621,
    )

    const val DEFAULT_RETENTION = 0.9

    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = elapsedDays.coerceAtLeast(0.0)
        return (1.0 + FACTOR * t / stability).pow(DECAY)
    }

    fun initStability(rating: Int): Double = W[(rating - 1).coerceIn(0, 3)].coerceAtLeast(0.1)

    fun initDifficulty(rating: Int): Double =
        (W[4] - exp(W[5] * (rating - 1)) + 1.0).coerceIn(1.0, 10.0)

    fun nextDifficulty(difficulty: Double, rating: Int): Double {
        val delta = difficulty - W[6] * (rating - 3)
        val reverted = W[7] * initDifficulty(4) + (1.0 - W[7]) * delta
        return reverted.coerceIn(1.0, 10.0)
    }

    fun stabilityAfterRecall(d: Double, s: Double, r: Double): Double {
        val gain = exp(W[8]) * (11.0 - d) * s.pow(-W[9]) * (exp(W[10] * (1.0 - r)) - 1.0)
        return (s * (1.0 + gain)).coerceAtLeast(0.1)
    }

    fun stabilityAfterForget(d: Double, s: Double, r: Double): Double {
        val s2 = W[11] * d.pow(-W[12]) * ((s + 1.0).pow(W[13]) - 1.0) * exp(W[14] * (1.0 - r))
        return s2.coerceIn(0.1, 36500.0)
    }

    /** Days until the card's retrievability drops to [retention]. */
    fun intervalDays(stability: Double, retention: Double = DEFAULT_RETENTION): Int =
        (stability / FACTOR * (retention.pow(1.0 / DECAY) - 1.0))
            .roundToInt()
            .coerceIn(1, 36500)
}
