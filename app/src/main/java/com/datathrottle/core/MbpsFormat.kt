package com.datathrottle.core

import java.util.Locale

/**
 * Single formatting point for Mbps values (S3-03). The locale is always
 * explicit — never the implicit default of the calling code path — so the
 * tile, the notification, the status line and the picker always agree, and
 * CI can pin a locale in tests.
 */
fun formatMbps(limitMbps: Float, locale: Locale = Locale.getDefault()): String =
    formatMbpsValue(limitMbps, locale) + " Mbps"

/** Numeric part only (picker renders "1" and "Mbps" as separate elements). */
fun formatMbpsValue(limitMbps: Float, locale: Locale = Locale.getDefault()): String =
    if (limitMbps >= 1.0f && limitMbps % 1.0f == 0f) {
        String.format(locale, "%.0f", limitMbps)
    } else {
        String.format(locale, "%.1f", limitMbps)
    }
