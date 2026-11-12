package dev.wordloom.ui.books

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfWheelTest {

    private val wheel = BookshelfWheel(6)

    @Test
    fun `delta takes the short way around the ring`() {
        assertEquals(0.2f, wheel.delta(0, 5.8f), 0.0001f)
        assertEquals(-1.2f, wheel.delta(5, 0.2f), 0.0001f)
        assertEquals(1f, wheel.delta(2, 1f), 0.0001f)
    }

    @Test
    fun `nearest folds negative and overflowing positions`() {
        assertEquals(0, wheel.nearest(5.6f))
        assertEquals(0, wheel.nearest(-0.4f))
        assertEquals(5, wheel.nearest(-0.6f))
        assertEquals(2, wheel.nearest(8.2f))
    }

    @Test
    fun `settle target keeps a small flick local`() {
        assertEquals(1, wheel.settleTarget(1.1f, 0f))
        assertEquals(0, wheel.settleTarget(2f, 10f))
        assertEquals(3, wheel.settleTarget(2f, -5f))
    }

    @Test
    fun `stack order is stable around the nearest book`() {
        assertEquals(0, wheel.stackOrder(2, 2))
        assertEquals(1, wheel.stackOrder(3, 2))
        assertEquals(1, wheel.stackOrder(1, 2))
        assertEquals(2, wheel.stackOrder(4, 2))
        assertEquals(2, wheel.stackOrder(0, 2))
    }

    @Test
    fun `one book never wraps or moves`() {
        val single = BookshelfWheel(1)
        assertEquals(0f, single.delta(0, 12.4f), 0.0001f)
        assertEquals(0, single.nearest(12.4f))
        assertEquals(0, single.stackOrder(0, 0))
    }
}
