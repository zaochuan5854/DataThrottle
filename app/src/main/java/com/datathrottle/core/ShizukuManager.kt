package com.datathrottle.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    UNAUTHORIZED,
    RUNNING
}

class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        const val REQUEST_CODE_SHIZUKU = 1001
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }

    private val _status = MutableStateFlow(checkStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        updateStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        updateStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            Log.d(TAG, "Shizuku permission result: $grantResult")
            updateStatus()
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun updateStatus() {
        _status.value = checkStatus()
    }

    private fun checkStatus(): ShizukuStatus {
        return try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.isPreV11()) {
                    ShizukuStatus.RUNNING
                } else {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        ShizukuStatus.RUNNING
                    } else {
                        ShizukuStatus.UNAUTHORIZED
                    }
                }
            } else {
                if (isShizukuInstalled()) {
                    ShizukuStatus.NOT_RUNNING
                } else {
                    ShizukuStatus.NOT_INSTALLED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku status", e)
            if (isShizukuInstalled()) {
                ShizukuStatus.NOT_RUNNING
            } else {
                ShizukuStatus.NOT_INSTALLED
            }
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestPermission() {
        if (Shizuku.isPreV11()) {
            Log.w(TAG, "Shizuku pre-v11 doesn't support requestPermission")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                updateStatus()
                return
            }
            Log.d(TAG, "Requesting Shizuku permission")
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    /**
     * Runs a command through Shizuku (shell uid) and returns the live
     * [Process] for stream access, or null when Shizuku is unavailable.
     * The diagnostic uses this to measure throughput on a uid that the
     * system shaping governs: shell uid is shaped (verified on device),
     * DownloadProvider (uid 10099) and the app's own uid are not.
     */
    fun runCommand(args: List<String>): Process? {
        if (checkStatus() != ShizukuStatus.RUNNING) {
            return null
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            newProcessMethod.invoke(null, args.toTypedArray(), null, null) as Process
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku execution failed", e)
            null
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS through Shizuku (S1-08): runs off the
     * main thread, drains stdout/stderr concurrently (never after
     * `waitFor`, which is the classic pipe-buffer deadlock order), bounds
     * the wait, and destroys the process when it overruns.
     */
    suspend fun grantWriteSecureSettings(): Boolean = withContext(Dispatchers.IO) {
        if (checkStatus() != ShizukuStatus.RUNNING) {
            Log.e(TAG, "Shizuku is not running or not authorized")
            return@withContext false
        }

        val packageName = context.packageName
        val command = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        Log.d(TAG, "Executing via Shizuku: $command")

        val process = runCommand(listOf("sh", "-c", command)) ?: return@withContext false
        try {
            val output = StringBuilder()
            val error = StringBuilder()
            val readers = listOf(
                thread(start = true) {
                    runCatching { process.inputStream.bufferedReader().use { output.append(it.readText()) } }
                },
                thread(start = true) {
                    runCatching { process.errorStream.bufferedReader().use { error.append(it.readText()) } }
                }
            )
            val exitCode = if (process.waitFor(10, TimeUnit.SECONDS)) {
                readers.forEach { it.join(2000) }
                process.exitValue()
            } else {
                Log.e(TAG, "Shizuku command timed out; destroying process")
                process.destroyForcibly()
                -1
            }

            if (output.isNotEmpty()) Log.d(TAG, "Shizuku Output: $output")
            if (error.isNotEmpty()) Log.e(TAG, "Shizuku Error: $error")

            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku execution failed", e)
            runCatching { process.destroy() }
            false
        }
    }

    fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
