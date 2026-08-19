package com.datathrottle

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Screengrab instrumented test.
 *
 * Captures all key screens for use in the English README / Play Store listing.
 * Run via: bundle exec fastlane screenshots
 *
 * Screenshots saved to: artworks/screenshots/en-US/images/phoneScreenshots/
 *
 * Screen order:
 *   01_permission_setup  — Initial setup screen (no permissions granted)
 *   02_home_off          — Home screen, service disabled
 *   03_bandwidth_picker  — Bandwidth picker scrolled to 2.0 Mbps
 *   04_home_on           — Home screen, service enabled (throttling active)
 *   05_settings          — Settings screen
 *   06_test_screen       — 100 kbps Test screen
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val localeTestRule = LocaleTestRule()
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Grant all three permissions the app requires via UiAutomation shell commands.
     * This mirrors what Shizuku / ADB does in production:
     *   - WRITE_SECURE_SETTINGS  (has the `development` protection flag → grantable by shell)
     *   - POST_NOTIFICATIONS     (runtime permission, auto-granted in tests)
     *   - Battery Optimizations  (added to doze whitelist via dumpsys)
     */
    private fun grantAllPermissions() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand(
            "pm grant com.datathrottle android.permission.WRITE_SECURE_SETTINGS"
        ).close()
        uiAutomation.executeShellCommand(
            "pm grant com.datathrottle android.permission.POST_NOTIFICATIONS"
        ).close()
        // Add app to doze / battery optimization whitelist so
        // PowerManager.isIgnoringBatteryOptimizations() returns true
        uiAutomation.executeShellCommand(
            "dumpsys deviceidle whitelist +com.datathrottle"
        ).close()
        Thread.sleep(300)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Screens
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 01 — Permission Setup Screen
     * Captured before granting any permissions so the PermissionSetupScreen is shown.
     * On a fresh CI emulator this is the first thing the user sees.
     */
    @Test
    fun screenshot_01_permissionSetup() {
        composeTestRule.waitForIdle()
        Thread.sleep(700)
        Screengrab.screenshot("01_permission_setup")
    }

    /**
     * 02 — Home Screen (service OFF / Disabled)
     * Grant permissions first, then recreate the Activity so the ViewModel
     * re-evaluates hasAllPermissions and transitions to HomeScreen.
     */
    @Test
    fun screenshot_02_homeOff() {
        grantAllPermissions()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(700)
        Screengrab.screenshot("02_home_off")
    }

    /**
     * 03 — Bandwidth Picker (drumroll) at 2.0 Mbps
     * Scrolls the LazyColumn picker to index 11 (2.0 Mbps) to make the
     * scrollable nature of the picker obvious.
     *
     * Index mapping (see DrumrollBandwidthPicker.kt):
     *   0..9   = 0.1..1.0 Mbps (0.1 step)
     *   10     = 1.5 Mbps
     *   11     = 2.0 Mbps
     */
    @Test
    fun screenshot_03_bandwidthPicker() {
        grantAllPermissions()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        composeTestRule
            .onNodeWithTag("bandwidth_picker_list")
            .performScrollToIndex(11) // 2.0 Mbps
        composeTestRule.waitForIdle()
        Thread.sleep(600)

        Screengrab.screenshot("03_bandwidth_picker")
    }

    /**
     * 04 — Home Screen (service ON / throttling active)
     * Taps the GiantToggleSwitch to start the BandwidthControlService.
     */
    @Test
    fun screenshot_04_homeOn() {
        grantAllPermissions()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(700)

        composeTestRule.onNodeWithTag("service_toggle").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(900)

        Screengrab.screenshot("04_home_on")
    }

    /**
     * 05 — Settings Screen
     * Navigates via the hamburger Menu icon in the TopAppBar.
     */
    @Test
    fun screenshot_05_settings() {
        grantAllPermissions()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(700)

        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(600)

        Screengrab.screenshot("05_settings")
    }

    /**
     * 06 — 100 kbps Test Screen
     * Navigates Home → Settings → Test.
     */
    @Test
    fun screenshot_06_testScreen() {
        grantAllPermissions()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        Thread.sleep(700)

        // Home → Settings
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Settings → Test
        composeTestRule.onNodeWithText("100 kbps Test").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(600)

        Screengrab.screenshot("06_test_screen")
    }
}
