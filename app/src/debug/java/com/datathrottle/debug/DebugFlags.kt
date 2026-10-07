package com.datathrottle.debug

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug build implementation of [DebugFlags] (replaces the main-source stub —
 * DebugInjector pattern: identical FQCN, per-variant source set).
 *
 * Backed by a dedicated SharedPreferences file so debug flags never mix with
 * production DataStore data.
 */
object DebugFlags {

    private const val PREFS = "debug_flags"
    private const val KEY_FORCE_CELLULAR = "force_cellular"

    private var appContext: Context? = null

    private val _forceCellular = MutableStateFlow(false)
    val forceCellular: StateFlow<Boolean> = _forceCellular.asStateFlow()

    /** Loads persisted flags; call once from Application.onCreate in debug builds. */
    fun init(context: Context) {
        appContext = context.applicationContext
        _forceCellular.value = prefs().getBoolean(KEY_FORCE_CELLULAR, false)
        android.util.Log.d("DebugFlags", "init force_cellular=${_forceCellular.value}")
    }

    fun setForceCellular(enabled: Boolean) {
        android.util.Log.d("DebugFlags", "setForceCellular($enabled)")
        _forceCellular.value = enabled
        prefs().edit().putBoolean(KEY_FORCE_CELLULAR, enabled).apply()
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
