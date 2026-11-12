package dev.wordloom.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {

    @Test
    fun `retrievability starts at one`() {
        assertEquals(1.0, Fsrs.retrievability(0.0, 5.0), 1e-9)
    }

    @Test
    fun `interval equals stability at ninety percent retention`() {
        assertEquals(10, Fsrs.intervalDays(10.0))
        assertEquals(1, Fsrs.intervalDays(0.0))
    }

    @Test
    fun `first good review is three days out`() {
        val now = 1_700_000_000_000L
        val outcome = Scheduler.review(CardProgress(), Rating.Good, now)

        assertEquals(3, outcome.intervalDays)
        assertEquals(3.173, outcome.progress.stability, 1e-9)
        assertEquals(5.282, outcome.progress.difficulty, 1e-3)
        assertEquals(1, outcome.progress.reps)
        assertEquals(0, outcome.progress.lapses)
        assertEquals(now, outcome.progress.lastReviewAt)
        assertEquals(now + 3 * DAY_MS, outcome.progress.dueAt)
    }

    @Test
    fun `first again review comes back tomorrow without a lapse`() {
        val outcome = Scheduler.review(CardProgress(), Rating.Again, 0L)

        assertEquals(1, outcome.intervalDays)
        assertEquals(1, outcome.progress.reps)
        assertEquals(0, outcome.progress.lapses)
        assertEquals(DAY_MS, outcome.progress.dueAt)
    }

    @Test
    fun `a successful review grows stability`() {
        val now = 10 * DAY_MS
        val current = CardProgress(
            stability = 3.173,
            difficulty = 5.282,
            reps = 1,
            lapses = 0,
            lastReviewAt = now - 3 * DAY_MS,
            dueAt = now,
        )

        val outcome = Scheduler.review(current, Rating.Good, now)

        assertTrue(outcome.progress.stability > current.stability)
        assertEquals(2, outcome.progress.reps)
        assertTrue(outcome.intervalDays >= 3)
    }
}
