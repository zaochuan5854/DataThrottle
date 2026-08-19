package com.datathrottle.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTheme(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String?): AppTheme {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val BANDWIDTH_LIMIT_MBPS = floatPreferencesKey("bandwidth_limit_mbps")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private const val DEFAULT_LIMIT_MBPS = 0.8f // ~100 KB/s
    }

    val bandwidthLimitMbps: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[BANDWIDTH_LIMIT_MBPS] ?: DEFAULT_LIMIT_MBPS
        }

    val appTheme: Flow<AppTheme> = context.dataStore.data
        .map { preferences ->
            AppTheme.fromValue(preferences[APP_THEME])
        }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SERVICE_ENABLED] ?: false
        }

    suspend fun setBandwidthLimitMbps(limit: Float) {
        context.dataStore.edit { preferences ->
            preferences[BANDWIDTH_LIMIT_MBPS] = limit
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme.value
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_ENABLED] = enabled
        }
    }
}
