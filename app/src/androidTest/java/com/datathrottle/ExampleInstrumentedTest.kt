package com.datathrottle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datathrottle.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test (S2-04): the previous template asserted the
 * scaffold package `com.example.datathrottle`, which never matched the real
 * applicationId, so the instrumented suite failed silently in CI (only
 * `./gradlew test` ran). Assert against BuildConfig.APPLICATION_ID so the
 * test tracks the real id.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }
}
