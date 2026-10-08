package com.datathrottle.debug

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.datathrottle.core.DebugFeatures

/**
 * Focus policy for the cellular-spoof switch.
 *
 * The spoof must not outlive the user's attention: while the app is not in the
 * user's focus the device should report its real network type again. Turning it
 * off the instant focus is lost, however, made every short background trip
 * (notification shade, quick settings) silently revert the type mid-verification.
 *
 * Policy: the flag survives [graceMs] after focus loss and is dropped
 * afterwards; returning to the app cancels the pending drop. Active only while
 * [DebugFeatures.enabled] is true — with the diagnostics switched off the policy
 * does nothing, so a release install never watches the lifecycle.
 */
class DebugSpoofFocusPolicy(
    private val lifecycleOwner: LifecycleOwner,
    private val graceMs: Long = DEFAULT_GRACE_MS
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingDrop: Job? = null

    // Explicit type: the observer removes itself on ON_DESTROY, which would
    // otherwise be a recursive inference site.
    private val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> scheduleDrop()
            Lifecycle.Event.ON_START -> cancelDrop(reason = "focus regained")
            Lifecycle.Event.ON_DESTROY -> {
                cancelDrop(reason = "owner destroyed")
                lifecycleOwner.lifecycle.removeObserver(this.observer)
            }
            else -> Unit
        }
    }

    fun attach() {
        lifecycleOwner.lifecycle.addObserver(observer)
        // The gate is dynamic: flipping the runtime switch off must stop the
        // policy immediately, not at the next focus change.
        scope.launch {
            DebugFeatures.enabled.collect { enabled ->
                if (enabled) {
                    if (pendingDrop?.isActive == true) return@collect
                    // (re)arm nothing here; ON_STOP schedules on its own.
                } else if (pendingDrop?.isActive == true) {
                    pendingDrop?.cancel()
                    pendingDrop = null
                    Log.d(TAG, "debug features off: pending spoof disable dropped")
                }
            }
        }
        Log.d(TAG, "focus observer registered (grace=${graceMs / 60_000} min)")
    }

    private fun scheduleDrop() {
        if (!DebugFeatures.enabled.value) return
        if (!DebugFlags.forceCellular.value) return
        pendingDrop?.cancel()
        pendingDrop = scope.launch {
            delay(graceMs)
            if (DebugFlags.forceCellular.value) {
                DebugFlags.setForceCellular(false)
                Log.d(TAG, "focus lost for ${graceMs / 60_000} min: cellular spoof disabled")
            }
        }
    }

    private fun cancelDrop(reason: String) {
        if (pendingDrop?.isActive != true) return
        pendingDrop?.cancel()
        pendingDrop = null
        Log.d(TAG, "$reason: pending spoof disable cancelled")
    }

    companion object {
        const val DEFAULT_GRACE_MS = 5 * 60 * 1000L
        private const val TAG = "DebugSpoofFocusPolicy"
    }
}
