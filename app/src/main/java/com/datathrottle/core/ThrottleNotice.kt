package com.datathrottle.core

import com.datathrottle.R

/** Label of the notification's throttle toggle for the current state. */
enum class PauseActionLabel { PAUSE_ONE_MINUTE, RESUME_NOW, NONE }

/**
 * Everything the service notification says about the current cap: string
 * resources + format arguments, accent colour and which pause action to offer.
 *
 * Kept separate from the Android builder so the state -> copy matrix is unit
 * tested (the notification itself is a presentation detail).
 */
data class ThrottleNotice(
    val titleRes: Int,
    val titleArgs: List<Any> = emptyList(),
    val bodyRes: Int,
    val bodyArgs: List<Any> = emptyList(),
    val accentColor: Int,
    val pauseActionLabel: PauseActionLabel
)

object ThrottleNoticeFactory {

    const val COLOR_ERROR = 0xFFDC2626.toInt()      // not enforced / limit currently off
    const val COLOR_DIAGNOSTIC = 0xFF00E5FF.toInt() // scanline test running
    const val COLOR_CELLULAR = 0xFF2563EB.toInt()   // cellular cap in force
    const val COLOR_WIFI = 0xFF0288D1.toInt()       // wifi, unlimited
    const val COLOR_IDLE = 0xFF757575.toInt()       // no network

    /**
     * @param type effective network type (debug spoof included)
     * @param isDiagnostic a scanline test currently owns the cap
     * @param paused the 60 s unlimit window is open
     * @param pauseRemainingSec countdown shown while [paused]
     * @param limitLabel the applied cap, already formatted (e.g. "0.1 Mbps")
     * @param notEnforced the kernel write was rejected (missing permission)
     */
    fun build(
        type: NetworkType,
        isDiagnostic: Boolean,
        paused: Boolean,
        pauseRemainingSec: Int,
        limitLabel: String,
        notEnforced: Boolean
    ): ThrottleNotice {
        // The toggle is only meaningful while a cap can actually be lifted:
        // cellular with the user's cap, or an open pause window. A diagnostic
        // test owns the cap (its pause requests are ignored), so it gets none.
        val pauseLabel = when {
            paused -> PauseActionLabel.RESUME_NOW
            type == NetworkType.CELLULAR && !isDiagnostic -> PauseActionLabel.PAUSE_ONE_MINUTE
            else -> PauseActionLabel.NONE
        }

        return when {
            notEnforced -> ThrottleNotice(
                titleRes = R.string.status_not_enforced,
                bodyRes = R.string.status_not_enforced_desc,
                accentColor = COLOR_ERROR,
                pauseActionLabel = PauseActionLabel.NONE
            )
            paused -> ThrottleNotice(
                titleRes = R.string.status_paused,
                bodyRes = R.string.status_desc_paused,
                bodyArgs = listOf(pauseRemainingSec),
                accentColor = COLOR_ERROR,
                pauseActionLabel = PauseActionLabel.RESUME_NOW
            )
            isDiagnostic -> ThrottleNotice(
                titleRes = R.string.test_running,
                bodyRes = R.string.notification_desc_test,
                accentColor = COLOR_DIAGNOSTIC,
                pauseActionLabel = PauseActionLabel.NONE
            )
            type == NetworkType.CELLULAR -> ThrottleNotice(
                titleRes = R.string.status_limited_to,
                titleArgs = listOf(limitLabel),
                bodyRes = R.string.status_desc_cellular,
                bodyArgs = listOf(limitLabel),
                accentColor = COLOR_CELLULAR,
                pauseActionLabel = PauseActionLabel.PAUSE_ONE_MINUTE
            )
            type == NetworkType.WIFI -> ThrottleNotice(
                titleRes = R.string.status_unlimited_wifi,
                bodyRes = R.string.status_desc_wifi,
                accentColor = COLOR_WIFI,
                pauseActionLabel = PauseActionLabel.NONE
            )
            else -> ThrottleNotice(
                titleRes = R.string.status_unlimited,
                bodyRes = R.string.status_desc_disabled,
                accentColor = COLOR_IDLE,
                pauseActionLabel = PauseActionLabel.NONE
            )
        }
    }
}
