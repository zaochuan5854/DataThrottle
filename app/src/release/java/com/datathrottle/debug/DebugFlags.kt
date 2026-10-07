package com.datathrottle.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Release variant of [DebugFlags] (DebugInjector pattern: debug and release
 * source sets each provide this class; the debug variant carries the real,
 * persisted implementation). Release is inert: the flag is hard-wired false.
 */
object DebugFlags {
    val forceCellular: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    fun init(context: Context) {
        // no-op in release builds
    }

    fun setForceCellular(enabled: Boolean) {
        // no-op in release builds
    }
}
