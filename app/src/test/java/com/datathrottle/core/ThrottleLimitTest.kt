package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the kernel-cap precedence as executable contract (S2-16/S2-13):
 * a diagnostic test always owns the cap, the unlimit window only lifts the
 * user's configured cap, and Wi-Fi is never shaped.
 */
class ThrottleLimitTest {

    private fun resolve(
        diagnostic: Long? = null,
        paused: Boolean = false,
        type: NetworkType = NetworkType.CELLULAR,
        mbps: Float = 1.0f
    ) = ThrottleLimit.resolve(diagnostic, paused, type, mbps)

    @Test
    fun cellularAppliesTheConfiguredCap() {
        assertEquals(112_500L, resolve(mbps = 0.9f))
        assertEquals(125_000L, resolve(mbps = 1.0f))
    }

    @Test
    fun wifiAndOtherAreNeverShaped() {
        assertEquals(ThrottleLimit.UNLIMITED, resolve(type = NetworkType.WIFI, mbps = 0.9f))
        assertEquals(ThrottleLimit.UNLIMITED, resolve(type = NetworkType.OTHER, mbps = 0.9f))
        assertEquals(ThrottleLimit.UNLIMITED, resolve(type = NetworkType.NONE, mbps = 0.9f))
    }

    @Test
    fun pauseWindowLiftsTheConfiguredCap() {
        assertEquals(ThrottleLimit.UNLIMITED, resolve(paused = true, mbps = 0.9f))
    }

    /** S2-16: the pause window must never let a test measure an unthrottled download. */
    @Test
    fun diagnosticOutranksThePauseWindow() {
        assertEquals(12_500L, resolve(diagnostic = 12_500L, paused = true, mbps = 0.9f))
    }

    @Test
    fun diagnosticOutranksCellularShaping() {
        assertEquals(12_500L, resolve(diagnostic = 12_500L, mbps = 0.9f))
    }

    /** S2-13: the notice must show the cap that was actually written. */
    @Test
    fun appliedMbpsFollowsTheWrittenBytes() {
        assertEquals(0.9f, ThrottleLimit.appliedMbps(112_500L, enforced = true, configuredMbps = 5.0f), 0.001f)
        assertEquals(0.1f, ThrottleLimit.appliedMbps(12_500L, enforced = true, configuredMbps = 5.0f), 0.001f)
    }

    @Test
    fun rejectedWriteFallsBackToTheConfiguredValue() {
        assertEquals(5.0f, ThrottleLimit.appliedMbps(112_500L, enforced = false, configuredMbps = 5.0f), 0.001f)
    }

    @Test
    fun unlimitedReportsTheConfiguredValue() {
        assertEquals(5.0f, ThrottleLimit.appliedMbps(ThrottleLimit.UNLIMITED, enforced = true, configuredMbps = 5.0f), 0.001f)
    }
}
