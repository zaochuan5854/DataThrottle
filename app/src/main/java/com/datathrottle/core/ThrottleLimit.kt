package com.datathrottle.core

/**
 * Single decision point for the kernel cap (bytes/s). Pure so the precedence
 * contract can be pinned by unit tests instead of only by device runs.
 *
 * Precedence is a correctness contract, not a preference:
 *  1. a running diagnostic scanline test owns the cap — the measurement must
 *     never see an unthrottled download (S2-16: the 60 s unlimit window used to
 *     outrank the test and produced a false "throttling not applied" verdict);
 *  2. the notification's 60 s unlimit window lifts the configured cap (S1-16);
 *  3. the configured cap applies on cellular only;
 *  4. everything else is unlimited — Wi-Fi is never shaped.
 */
object ThrottleLimit {

    /** Value of Settings.Global ingress_rate_limit_bytes_per_second meaning "no cap". */
    const val UNLIMITED = -1L

    /** Mbps -> bytes/s (the kernel cap is a byte rate). */
    const val MBPS_TO_BYTES_PER_SECOND = 125_000L

    fun resolve(
        diagnosticBytes: Long?,
        paused: Boolean,
        networkType: NetworkType,
        limitMbps: Float
    ): Long = when {
        diagnosticBytes != null -> diagnosticBytes
        paused -> UNLIMITED
        networkType == NetworkType.CELLULAR -> (limitMbps * MBPS_TO_BYTES_PER_SECOND).toLong()
        else -> UNLIMITED
    }

    /**
     * The Mbps value that matches what was actually installed on the kernel
     * (S2-13): a rejected write must not advertise a speed it never applied,
     * and a successful one must not trail the last configuration change.
     */
    fun appliedMbps(limitBytes: Long, enforced: Boolean, configuredMbps: Float): Float =
        if (enforced && limitBytes > 0) limitBytes.toFloat() / MBPS_TO_BYTES_PER_SECOND
        else configuredMbps
}
