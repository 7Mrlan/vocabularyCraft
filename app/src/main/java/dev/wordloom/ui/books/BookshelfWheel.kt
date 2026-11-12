package dev.wordloom.ui.books

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Pure geometry for the endless bookshelf. Keeping wrap math out of Compose
 * makes the ring testable and gives the screen one source of truth for the
 * nearest book, the settle target and the stable stack rank.
 */
class BookshelfWheel(val count: Int) {

    init {
        require(count > 0) { "A bookshelf needs at least one book" }
    }

    /** Position folded into [0, count), safe for negative and large offsets. */
    fun normalize(position: Float): Float {
        val folded = position % count
        return if (folded < 0f) folded + count else folded
    }

    /** Shortest signed distance from [position] to [index], in pages. */
    fun delta(index: Int, position: Float): Float {
        if (count == 1) return 0f
        var distance = index - position
        distance -= count * (distance / count).roundToInt()
        return distance
    }

    /** The page a finger is closest to, independent of how far it has wrapped. */
    fun nearest(position: Float): Int = Math.floorMod(position.roundToInt(), count)

    /**
     * Where a flick should rest. The velocity term is deliberately small: a
     * hard swipe crosses one or two books, not the whole shelf.
     */
    fun settleTarget(position: Float, pagesPerSecond: Float): Int =
        (position - pagesPerSecond * 0.20f).roundToInt()

    /**
     * Stable drawing rank: centre 0, neighbours 1, and so on. It only changes
     * when [nearest] changes, never on every animation frame.
     */
    fun stackOrder(index: Int, nearest: Int): Int =
        delta(index, nearest.toFloat()).absoluteValue.roundToInt()
}
