package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the scanline test's guard and teardown rules (S2-17/S2-18): one run at a
 * time, and a test that created the service must not leave it foregrounded.
 */
class TestLaunchPolicyTest {

    @Test
    fun reEntrantStartIsIgnored() {
        assertTrue(TestLaunchPolicy.ignoreStart(diagnosticRunning = true))
        assertFalse(TestLaunchPolicy.ignoreStart(diagnosticRunning = false))
    }

    @Test
    fun runningServiceGetsTheCapReset() {
        assertEquals(
            TestLaunchPolicy.Teardown.RESET_DIAGNOSTIC_CAP,
            TestLaunchPolicy.teardownFor(serviceWasRunning = true)
        )
    }

    /** S2-18: with no service of the user's own, teardown must stop the test's. */
    @Test
    fun testCreatedServiceIsStopped() {
        assertEquals(
            TestLaunchPolicy.Teardown.STOP_SERVICE,
            TestLaunchPolicy.teardownFor(serviceWasRunning = false)
        )
    }
}
