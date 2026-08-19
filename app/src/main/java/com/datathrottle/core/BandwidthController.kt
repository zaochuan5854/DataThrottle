package com.datathrottle.core

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log

class BandwidthController(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "BandwidthController"
        private const val INGRESS_RATE_LIMIT_KEY = "ingress_rate_limit_bytes_per_second"
    }

    /**
     * Sets the ingress rate limit in bytes per second.
     * Requires WRITE_SECURE_SETTINGS permission.
     */
    fun setIngressRateLimit(bytesPerSecond: Long): Result<Unit> {
        return try {
            val success = Settings.Global.putLong(
                contentResolver,
                INGRESS_RATE_LIMIT_KEY,
                bytesPerSecond
            )
            if (success) {
                Log.d(TAG, "Successfully set ingress rate limit to $bytesPerSecond bytes/s")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to set ingress rate limit")
                Result.failure(Exception("Failed to update Settings.Global"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: WRITE_SECURE_SETTINGS permission not granted", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ingress rate limit", e)
            Result.failure(e)
        }
    }

    /**
     * Resets the ingress rate limit to the default value (-1).
     * Requires WRITE_SECURE_SETTINGS permission.
     */
    fun resetToDefault(): Result<Unit> {
        return setIngressRateLimit(-1)
    }
}
