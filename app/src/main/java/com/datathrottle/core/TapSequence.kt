package com.datathrottle.core

/**
 * Detects the "tap the version row N times" gesture that flips the debug
 * feature switch. Pure and clock-injected so the threshold behaviour is unit
 * tested instead of hand-tapped on a device.
 *
 * A tap only counts towards the same burst while it happens within [windowMs]
 * of the previous one; a longer gap restarts the count. Firing resets the
 * sequence so one gesture cannot toggle twice.
 */
class TapSequence(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var count = 0
    private var lastTapMs = 0L

    /** Register one tap; returns true exactly on the tap that completes a burst. */
    fun register(): Boolean {
        val now = clock()
        count = if (count > 0 && now - lastTapMs > windowMs) 1 else count + 1
        lastTapMs = now
        if (count >= threshold) {
            count = 0
            return true
        }
        return false
    }

    fun reset() {
        count = 0
    }

    companion object {
        /** Version row taps required to flip [DebugFeatures]. */
        const val DEFAULT_THRESHOLD = 7
        const val DEFAULT_WINDOW_MS = 3_000L
    }
}
