package com.datathrottle.debug

import android.content.Context
import com.datathrottle.core.DebugFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Persisted debug switches, backed by their own SharedPreferences file so they
 * never mix with production DataStore settings.
 *
 * The spoof switch is *gated* by [DebugFeatures.enabled]: even if a previous
 * session left it on, turning the runtime feature switch off makes the spoof
 * inert, which is what "the debug/release difference is switchable" means.
 */
object DebugFlags {

    private const val PREFS = "debug_flags"
    private const val KEY_FORCE_CELLULAR = "force_cellular"

    private var appContext: Context? = null

    private val _forceCellular = MutableStateFlow(false)
    private val _featuresAllowed = DebugFeatures.enabled

    // Object-scoped: the flag lives as long as the process, like the object.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Effective value: the stored switch AND the runtime feature gate. */
    val forceCellular: StateFlow<Boolean> =
        combine(_forceCellular, _featuresAllowed) { forced, allowed -> forced && allowed }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** Loads persisted flags; call once from Application/Activity on every build. */
    fun init(context: Context) {
        appContext = context.applicationContext
        _forceCellular.value = prefs().getBoolean(KEY_FORCE_CELLULAR, false)
        android.util.Log.d(TAG, "init force_cellular=${_forceCellular.value}")
    }

    fun setForceCellular(enabled: Boolean) {
        android.util.Log.d(TAG, "setForceCellular($enabled)")
        _forceCellular.value = enabled
        prefs().edit().putBoolean(KEY_FORCE_CELLULAR, enabled).apply()
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val TAG = "DebugFlags"
}
