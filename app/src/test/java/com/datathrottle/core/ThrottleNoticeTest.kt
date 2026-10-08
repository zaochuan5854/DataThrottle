package com.datathrottle.core

import com.datathrottle.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the state -> notification-copy matrix. The strings themselves are user
 * facing and must not drift with the state machine, and the pause toggle must
 * only appear where it can actually do something.
 */
class ThrottleNoticeTest {

    private fun build(
        type: NetworkType = NetworkType.CELLULAR,
        isDiagnostic: Boolean = false,
        paused: Boolean = false,
        remaining: Int = 60,
        label: String = "0.1 Mbps",
        notEnforced: Boolean = false
    ) = ThrottleNoticeFactory.build(type, isDiagnostic, paused, remaining, label, notEnforced)

    @Test
    fun cellularShowsTheAppliedCapAndOffersThePause() {
        val notice = build()
        assertEquals(R.string.status_limited_to, notice.titleRes)
        assertEquals(listOf<Any>("0.1 Mbps"), notice.titleArgs)
        assertEquals(R.string.status_desc_cellular, notice.bodyRes)
        assertEquals(ThrottleNoticeFactory.COLOR_CELLULAR, notice.accentColor)
        assertEquals(PauseActionLabel.PAUSE_ONE_MINUTE, notice.pauseActionLabel)
    }

    @Test
    fun pausedShowsTheCountdownAndOffersResume() {
        val notice = build(paused = true, remaining = 57)
        assertEquals(R.string.status_paused, notice.titleRes)
        assertEquals(R.string.status_desc_paused, notice.bodyRes)
        assertEquals(listOf<Any>(57), notice.bodyArgs)
        assertEquals(ThrottleNoticeFactory.COLOR_ERROR, notice.accentColor)
        assertEquals(PauseActionLabel.RESUME_NOW, notice.pauseActionLabel)
    }

    @Test
    fun notEnforcedWinsOverEveryOtherState() {
        for (paused in listOf(false, true)) {
            for (diagnostic in listOf(false, true)) {
                val notice = build(paused = paused, isDiagnostic = diagnostic, notEnforced = true)
                assertEquals(R.string.status_not_enforced, notice.titleRes)
                assertEquals(ThrottleNoticeFactory.COLOR_ERROR, notice.accentColor)
                assertEquals(PauseActionLabel.NONE, notice.pauseActionLabel)
            }
        }
    }

    @Test
    fun diagnosticNeverOffersThePauseToggle() {
        val notice = build(isDiagnostic = true)
        assertEquals(R.string.test_running, notice.titleRes)
        assertEquals(R.string.notification_desc_test, notice.bodyRes)
        assertEquals(ThrottleNoticeFactory.COLOR_DIAGNOSTIC, notice.accentColor)
        assertEquals(PauseActionLabel.NONE, notice.pauseActionLabel)
    }

    @Test
    fun wifiIsUnlimitedWithoutAToggle() {
        val notice = build(type = NetworkType.WIFI)
        assertEquals(R.string.status_unlimited_wifi, notice.titleRes)
        assertEquals(ThrottleNoticeFactory.COLOR_WIFI, notice.accentColor)
        assertEquals(PauseActionLabel.NONE, notice.pauseActionLabel)
    }

    @Test
    fun noNetworkFallsBackToTheIdleCopy() {
        val notice = build(type = NetworkType.NONE)
        assertEquals(R.string.status_unlimited, notice.titleRes)
        assertEquals(R.string.status_desc_disabled, notice.bodyRes)
        assertEquals(ThrottleNoticeFactory.COLOR_IDLE, notice.accentColor)
    }
}
