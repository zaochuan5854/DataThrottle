package com.datathrottle.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Pins the S3-03 contract: the numeric form is locale-explicit and stable. */
class MbpmFormatTest {
    @Test
    fun wholeNumbersHaveNoDecimals() {
        assertEquals("1 Mbps", formatMbps(1.0f, Locale.US))
        assertEquals("5 Mbps", formatMbps(5.0f, Locale.US))
    }

    @Test
    fun subOneAndFractionalKeepOneDecimal() {
        assertEquals("0.1 Mbps", formatMbps(0.1f, Locale.US))
        assertEquals("1.2 Mbps", formatMbps(1.2f, Locale.US))
    }

    @Test
    fun decimalSeparatorFollowsThePassedLocale() {
        // The old code used the implicit default locale; now the caller (and
        // tests) decide. de-DE shows a comma, en-US a dot.
        assertEquals("1,2 Mbps", formatMbps(1.2f, Locale.GERMANY))
        assertEquals("1.2", formatMbpsValue(1.2f, Locale.US))
    }
}
