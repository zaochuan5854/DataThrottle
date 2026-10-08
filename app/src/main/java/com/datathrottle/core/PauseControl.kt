package com.datathrottle.core

/** What a tap on the home switch should do. */
enum class HomeToggleAction {
    /** End the unlimit window: throttle resumes, countdown discarded. */
    RESUME_THROTTLE,

    /** Start the throttling service. */
    START_SERVICE,

    /** Stop the throttling service (teardown resets the kernel cap). */
    STOP_SERVICE
}

/**
 * Home-switch policy (S2-19).
 *
 * The switch shows the *service* state, so it stays ON while the 60 s unlimit
 * window is open — the pause is reported by the notification, not by flipping a
 * control the user still needs for "turn throttling off". Therefore a tap during
 * the window means "resume now": the countdown is dropped and the configured cap
 * is re-applied, instead of stopping a service that is merely idling.
 */
object PauseControl {

    fun actionFor(paused: Boolean, enableRequested: Boolean): HomeToggleAction = when {
        paused -> HomeToggleAction.RESUME_THROTTLE
        enableRequested -> HomeToggleAction.START_SERVICE
        else -> HomeToggleAction.STOP_SERVICE
    }
}
