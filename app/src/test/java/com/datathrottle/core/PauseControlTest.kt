package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the home-switch policy (S2-19): the switch keeps showing the service
 * state, so a tap during the unlimit window must resume throttling (and drop
 * the countdown) rather than stop a service that is merely idling.
 */
class PauseControlTest {

    @Test
    fun tapDuringTheWindowResumesInsteadOfStopping() {
        assertEquals(
            HomeToggleAction.RESUME_THROTTLE,
            PauseControl.actionFor(paused = true, enableRequested = false)
        )
    }

    @Test
    fun tapDuringTheWindowResumesRegardlessOfRequestedState() {
        // The switch reads ON, so only the OFF transition is reachable, but the
        // policy must not depend on it.
        assertEquals(
            HomeToggleAction.RESUME_THROTTLE,
            PauseControl.actionFor(paused = true, enableRequested = true)
        )
    }

    @Test
    fun withoutAWindowTheSwitchStartsAndStopsTheService() {
        assertEquals(
            HomeToggleAction.START_SERVICE,
            PauseControl.actionFor(paused = false, enableRequested = true)
        )
        assertEquals(
            HomeToggleAction.STOP_SERVICE,
            PauseControl.actionFor(paused = false, enableRequested = false)
        )
    }
}
