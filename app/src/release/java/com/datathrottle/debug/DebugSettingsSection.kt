package com.datathrottle.debug

import androidx.compose.runtime.Composable

/**
 * Release variant of [DebugSettingsSection]: renders nothing. The debug
 * source set provides the real section (DebugInjector pattern).
 */
@Composable
fun DebugSettingsSection() {
    // Intentionally empty in release builds.
}
