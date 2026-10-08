package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the unlimit-window contract (S1-16): the countdown reports whole
 * remaining seconds, and a stopped window invalidates its token so the
 * countdown tail can no longer restore a cap on a torn-down service.
 */
class PauseWindowTest {

    private class Clock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun freshWindowReportsItsFullDuration() {
        val clock = Clock()
        val window = PauseWindow(durationMs = 60_000L, clock = clock)
        val token = window.start()

        assertTrue(window.isActive)
        assertTrue(window.isCurrent(token))
        assertEquals(60, window.durationSeconds)
        assertEquals(60, window.remainingSeconds())
        assertFalse(window.isElapsed())
    }

    @Test
    fun remainingSecondsCountDownAndRoundUp() {
        val clock = Clock()
        val window = PauseWindow(durationMs = 60_000L, clock = clock)
        window.start()

        clock.now = 1_000L
        assertEquals(59, window.remainingSeconds())
        // 500 ms into the last second still reads as one second left.
        clock.now = 59_500L
        assertEquals(1, window.remainingSeconds())
        clock.now = 60_000L
        assertEquals(0, window.remainingSeconds())
        assertTrue(window.isElapsed())
    }

    @Test
    fun elapsedWindowStillBelongsToItsOwner() {
        val clock = Clock()
        val window = PauseWindow(durationMs = 60_000L, clock = clock)
        val token = window.start()
        clock.now = 60_500L

        // Elapsed, but not superseded: the countdown may still restore the cap.
        assertTrue(window.isElapsed())
        assertTrue(window.isCurrent(token))
    }

    /** S1-16: teardown stops the window -> the tail must not touch the cap. */
    @Test
    fun stopInvalidatesTheToken() {
        val clock = Clock()
        val window = PauseWindow(durationMs = 60_000L, clock = clock)
        val token = window.start()

        window.stop()

        assertFalse(window.isActive)
        assertFalse(window.isCurrent(token))
        assertEquals(0, window.remainingSeconds())
        assertTrue(window.isElapsed())
    }

    @Test
    fun restartSupersedesThePreviousToken() {
        val clock = Clock()
        val window = PauseWindow(durationMs = 60_000L, clock = clock)
        val first = window.start()
        clock.now = 5_000L
        val second = window.start()

        assertFalse(window.isCurrent(first))
        assertTrue(window.isCurrent(second))
        assertEquals(60, window.remainingSeconds())
    }
}
