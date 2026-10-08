package com.datathrottle.core

/**
 * Guard and teardown rules for the 100 kbps scanline test. Both decisions used
 * to live inline in the ViewModel, where they were untestable and, when wrong,
 * left the phone shaped (or transferring) without the user asking.
 */
object TestLaunchPolicy {

    /** Re-entrant start is ignored while a run (or its cap-confirm poll) is in flight (S2-17). */
    fun ignoreStart(diagnosticRunning: Boolean): Boolean = diagnosticRunning

    enum class Teardown {
        /** Service was already running: drop the diagnostic cap, keep throttling. */
        RESET_DIAGNOSTIC_CAP,

        /** The test created the service: stop it instead of leaving it foregrounded (S2-18). */
        STOP_SERVICE
    }

    fun teardownFor(serviceWasRunning: Boolean): Teardown =
        if (serviceWasRunning) Teardown.RESET_DIAGNOSTIC_CAP else Teardown.STOP_SERVICE
}
