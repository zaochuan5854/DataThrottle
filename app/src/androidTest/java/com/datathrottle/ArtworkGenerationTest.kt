package com.datathrottle

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ArtworkGenerationTest {

    private lateinit var device: UiDevice
    private val packageName = "com.datathrottle"

    companion object {
        @JvmStatic
        @BeforeClass
        fun grantAllPermissions() {
            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val pkg = "com.datathrottle"
            uiAutomation.executeShellCommand("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS").close()
            uiAutomation.executeShellCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS").close()
            uiAutomation.executeShellCommand("dumpsys deviceidle whitelist +$pkg").close()
            Thread.sleep(1000)
        }
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun launchApp() {
        device.pressHome()
        Thread.sleep(500)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName)), 8000)
        Thread.sleep(1500)
    }

    private fun ensureServiceOff() {
        val toggle = device.wait(Until.findObject(By.desc("Service Toggle")), 3000)
        if (toggle != null) {
            val isUnlimited = device.hasObject(By.textContains("Unlimited"))
            if (isUnlimited) {
                toggle.click()
                Thread.sleep(1500)
            }
        }
    }

    private fun openMenu() {
        val menuIcon = device.wait(Until.findObject(By.desc("Menu")), 5000)
            ?: device.wait(Until.findObject(By.descContains("Menu")), 3000)
        checkNotNull(menuIcon) { "Menu icon not found" }
        menuIcon.click()
        Thread.sleep(1000)
    }

    private fun takeScreenshot(name: String) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("screencap -p /sdcard/$name.png").close()
        Thread.sleep(500)
    }

    // ─── 1. Screenshots ──────────────────────────────────────────

    @Test
    fun test01_homeOffScreenshot() {
        launchApp()
        ensureServiceOff()
        Thread.sleep(1000)
        takeScreenshot("02_home_off")
    }

    @Test
    fun test02_bandwidthPickerScreenshot() {
        launchApp()
        ensureServiceOff()
        Thread.sleep(1000)

        val picker = device.wait(Until.findObject(By.desc("Bandwidth Picker")), 3000)
        val bounds = picker?.visibleBounds ?: Rect(device.displayWidth / 4, (device.displayHeight * 0.35).toInt(), (device.displayWidth * 0.75).toInt(), (device.displayHeight * 0.45).toInt())
        val startY = bounds.centerY() + (bounds.height() * 0.8).toInt()
        val endY = bounds.centerY() - (bounds.height() * 0.8).toInt()
        device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, 20)
        Thread.sleep(1000)

        takeScreenshot("03_bandwidth_picker")
    }

    @Test
    fun test03_homeOnScreenshot() {
        launchApp()
        ensureServiceOff()
        Thread.sleep(1000)

        val toggle = device.wait(Until.findObject(By.desc("Service Toggle")), 5000)
        checkNotNull(toggle) { "Service Toggle not found" }
        toggle.click()
        Thread.sleep(1500)

        takeScreenshot("04_home_on")

        // Reset back to OFF
        toggle.click()
        Thread.sleep(1000)
    }

    @Test
    fun test04_settingsScreenshot() {
        launchApp()
        Thread.sleep(1000)

        openMenu()
        takeScreenshot("05_settings")
    }

    @Test
    fun test05_testScreenScreenshot() {
        launchApp()
        Thread.sleep(1000)

        openMenu()
        val testItem = device.wait(Until.findObject(By.textContains("100 kbps")), 5000)
        checkNotNull(testItem) { "100 kbps test item not found in Settings" }
        testItem.click()
        Thread.sleep(1200)

        takeScreenshot("06_test_screen")
    }

    @Test
    fun test06_permissionStatusScreenshot() {
        launchApp()
        Thread.sleep(1000)

        openMenu()
        takeScreenshot("01_permission_setup")
    }

    // ─── 2. Video Demos ──────────────────────────────────────────

    @Test
    fun test11_recordToggleSwitch() {
        launchApp()
        ensureServiceOff()
        Thread.sleep(1000)

        val toggle = device.wait(Until.findObject(By.desc("Service Toggle")), 5000)
        checkNotNull(toggle) { "Service Toggle not found" }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("screenrecord --size 720x1616 --bit-rate 4000000 --time-limit 4 /sdcard/01_toggle_switch.mp4")

        Thread.sleep(500)
        toggle.click()
        Thread.sleep(2500)
    }

    @Test
    fun test12_recordDrumrollPicker() {
        launchApp()
        ensureServiceOff()
        Thread.sleep(1000)

        val picker = device.wait(Until.findObject(By.desc("Bandwidth Picker")), 5000)
        val bounds = picker?.visibleBounds ?: Rect(device.displayWidth / 4, (device.displayHeight * 0.35).toInt(), (device.displayWidth * 0.75).toInt(), (device.displayHeight * 0.45).toInt())

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("screenrecord --size 720x1616 --bit-rate 4000000 --time-limit 7 /sdcard/02_drumroll_picker.mp4")

        Thread.sleep(500)

        val startY = bounds.centerY() + bounds.height() / 2
        val endY = bounds.centerY() - bounds.height() / 2
        device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, 20)
        Thread.sleep(1500)

        device.swipe(bounds.centerX(), endY, bounds.centerX(), startY, 20)
        Thread.sleep(2000)
    }

    @Test
    fun test13_record100kTest() {
        launchApp()
        Thread.sleep(1000)

        openMenu()
        val testItem = device.wait(Until.findObject(By.textContains("100 kbps")), 5000)
        checkNotNull(testItem) { "100 kbps test item not found" }
        testItem.click()
        Thread.sleep(1500)

        val startBtn = device.wait(Until.findObject(By.desc("Start Test Button")), 5000)
            ?: device.findObject(By.textContains("Start"))
            ?: device.findObject(By.textContains("テスト"))
        checkNotNull(startBtn) { "Start Test Button not found" }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("screenrecord --size 720x1616 --bit-rate 4000000 --time-limit 16 /sdcard/03_100k_test.mp4")

        Thread.sleep(500)
        startBtn.click()
        Thread.sleep(14000)
    }
}
