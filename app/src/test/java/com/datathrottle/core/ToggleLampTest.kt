package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the home switch's asymmetric lamp timing: a slow light-up and a faster
 * dim-down. A single shared duration would silently flatten this, so the
 * contract is explicit and tested.
 */
class ToggleLampTest {

    @Test
    fun lightUpIsSlow() {
        assertEquals(900, ToggleLamp.durationMs(lit = true))
    }

    @Test
    fun dimDownIsFasterThanLightUp() {
        assertTrue(ToggleLamp.durationMs(lit = false) < ToggleLamp.durationMs(lit = true))
        assertEquals(380, ToggleLamp.durationMs(lit = false))
    }

    @Test
    fun bothRampsAreEasedButNotInstant() {
        for (lit in listOf(true, false)) {
            val ms = ToggleLamp.durationMs(lit)
            assertTrue("ramp for lit=$lit must animate", ms > 100)
        }
    }

    @Test
    fun knobProgressIsClamped() {
        assertEquals(0f, ToggleLamp.knobFraction(-0.5f), 0.001f)
        assertEquals(0.5f, ToggleLamp.knobFraction(0.5f), 0.001f)
        assertEquals(1f, ToggleLamp.knobFraction(1.4f), 0.001f)
    }
}
