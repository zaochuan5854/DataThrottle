package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the S2-12 lesson as executable contract: the verdict must measure the
 * sustained post-burst rate, and must refuse to render a verdict when the
 * whole object rode the token-bucket burst.
 */
class BurstWindowTest {
    private fun nanos(ms: Long) = ms * 1_000_000L

    @Test
    fun sustainedRateIgnoresTheBurstPrefix() {
        val window = StreamTestEngine.BurstWindow(graceMs = 3_000L)
        // E7-shaped transfer: ~100 KB burst drains fast, then 12.5 KB/s cap.
        window.sample(elapsedMs = 3_000L, bytes = 131_250L, nowNanos = nanos(3_000L))
        val kbps = window.sustainedKbps(endNanos = nanos(24_250L), endBytes = 396_874L)
        // (396874-131250)*8 / 21.25 s = 100.0 kbps exactly
        assertEquals(100.0f, kbps!!, 0.5f)
    }

    @Test
    fun noWindowWhenTransferEndsInsideTheBurst() {
        val window = StreamTestEngine.BurstWindow(graceMs = 3_000L)
        // 82 KB object done in 0.6 s (E3a case): never sampled at grace.
        window.sample(elapsedMs = 600L, bytes = 82_429L, nowNanos = nanos(600L))
        assertNull(window.sustainedKbps(endNanos = nanos(600L), endBytes = 82_429L))
    }

    @Test
    fun degenerateWindowBelowHalfSecondIsRejected() {
        val window = StreamTestEngine.BurstWindow(graceMs = 3_000L)
        window.sample(elapsedMs = 3_000L, bytes = 300_000L, nowNanos = nanos(3_000L))
        // Ends 200 ms after grace: too short to distinguish cap from burst.
        assertNull(window.sustainedKbps(endNanos = nanos(3_200L), endBytes = 396_874L))
    }
}
