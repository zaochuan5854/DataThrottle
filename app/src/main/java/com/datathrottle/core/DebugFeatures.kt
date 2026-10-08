package com.datathrottle.core

import android.content.Context
import com.datathrottle.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime switch for the debug-only surfaces (cellular spoof switch, its focus
 * policy, the debug section of the settings screen).
 *
 * Why runtime and not `BuildConfig.DEBUG`: the debug/release *feature*
 * difference should be switchable on one installed build. Tapping the version
 * row [TapSequence.DEFAULT_THRESHOLD] times inverts it (see SettingsScreen), so
 * a release APK can be examined with the diagnostics exposed, and a debug build
 * can be checked with release-like behaviour -- without reinstalling.
 *
 * The value persists; when unset it falls back to the build default:
 * release -> false, every other build (debug) -> true.
 */
object DebugFeatures {

    private const val PREFS = "debug_features"

    /**
     * The stored value is keyed per build default, so a debug install that the
     * user switched on never hands "enabled" to a release install on the same
     * device: each variant starts from its own default and remembers only its
     * own choice.
     */
    private val KEY_ENABLED =
        "debug_features_enabled_" + if (BuildConfig.DEBUG) "debug" else "release"

    /** Build default: diagnostics off in release, on in debug. */
    val DEFAULT: Boolean = BuildConfig.DEBUG

    private val _enabled = MutableStateFlow(DEFAULT)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    /** Call once from the Activity/Application before the UI reads [enabled]. */
    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sp
        val stored = sp.getBoolean(KEY_ENABLED, DEFAULT)
        if (_enabled.value != stored) _enabled.value = stored
        android.util.Log.d(TAG, "init enabled=$stored (buildDefault=$DEFAULT)")
    }

    /** Invert the switch; returns the new value. */
    fun toggle(): Boolean {
        val next = !_enabled.value
        _enabled.value = next
        prefs?.edit()?.putBoolean(KEY_ENABLED, next)?.apply()
        android.util.Log.d(TAG, "toggle enabled=$next")
        return next
    }

    /** True when the current value equals the build default. */
    fun isAtBuildDefault(): Boolean = _enabled.value == DEFAULT

    private const val TAG = "DebugFeatures"
}
