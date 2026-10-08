package com.datathrottle.debug

import androidx.lifecycle.LifecycleOwner

/**
 * Release variant of [DebugSpoofFocusPolicy]: no debug flags exist, so the
 * policy is inert. The debug source set provides the real implementation
 * (DebugInjector pattern).
 */
class DebugSpoofFocusPolicy(
    @Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner,
    @Suppress("UNUSED_PARAMETER") graceMs: Long = DEFAULT_GRACE_MS
) {
    fun attach() {
        // Intentionally empty in release builds.
    }

    companion object {
        const val DEFAULT_GRACE_MS = 5 * 60 * 1000L
    }
}
