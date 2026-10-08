package com.datathrottle.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "tap the version row 7 times" gesture that inverts the debug feature
 * switch: fires exactly on the 7th tap, resets afterwards, and forgets taps that
 * are too far apart to be one intent.
 */
class TapSequenceTest {

    private class Clock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun firesOnTheSeventhTapOnly() {
        val clock = Clock()
        val seq = TapSequence(clock = clock)
        for (i in 1..6) {
            assertFalse("tap $i must not fire", seq.register())
            clock.now += 200
        }
        assertTrue(seq.register())
    }

    @Test
    fun oneBurstCannotToggleTwice() {
        val clock = Clock()
        val seq = TapSequence(clock = clock)
        repeat(7) { seq.register(); clock.now += 100 }
        assertFalse("8th tap is a new burst", seq.register())
    }

    @Test
    fun slowTapsNeverAccumulate() {
        val clock = Clock()
        val seq = TapSequence(windowMs = 3_000L, clock = clock)
        repeat(10) {
            assertFalse(seq.register())
            clock.now += 3_100 // outside the burst window
        }
    }

    @Test
    fun aPauseResetsTheCount() {
        val clock = Clock()
        val seq = TapSequence(windowMs = 3_000L, clock = clock)
        repeat(5) { seq.register(); clock.now += 100 }
        clock.now += 5_000 // user walked away
        repeat(6) {
            assertFalse(seq.register())
            clock.now += 100
        }
        assertTrue("7 consecutive taps still fire", seq.register())
    }

    @Test
    fun resetClearsProgress() {
        val clock = Clock()
        val seq = TapSequence(clock = clock)
        repeat(6) { seq.register(); clock.now += 100 }
        seq.reset()
        assertFalse(seq.register())
    }
}
