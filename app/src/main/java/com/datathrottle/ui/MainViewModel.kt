package com.datathrottle.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datathrottle.R
import com.datathrottle.core.BandwidthController
import com.datathrottle.core.TestState
import com.datathrottle.core.TestStatus
import com.datathrottle.core.NetworkMonitor
import com.datathrottle.core.NetworkType
import com.datathrottle.core.ShizukuManager
import com.datathrottle.core.ShizukuStatus
import com.datathrottle.core.StreamTestEngine
import com.datathrottle.data.AppTheme
import com.datathrottle.data.SettingsRepository
import com.datathrottle.service.BandwidthControlService
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val networkType: NetworkType = NetworkType.NONE,
    val isServiceRunning: Boolean = false,
    val hasSecureSettingsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val bandwidthLimitMbps: Float = 1.0f,
    val isDiagnosticRunning: Boolean = false,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val settingsLoaded: Boolean = false
) {
    val hasAllPermissions: Boolean
        get() = hasSecureSettingsPermission && hasNotificationPermission && isIgnoringBatteryOptimizations
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainViewModel"
        const val LIMIT_CONFIRM_TIMEOUT_MS = 3_000L
    }

    private val settingsRepository = SettingsRepository(application)
    private val networkMonitor = NetworkMonitor(application)
    private val bandwidthController = BandwidthController(application.contentResolver)
    private val shizukuManager = ShizukuManager(application)
    private val streamTestEngine = StreamTestEngine(application, shizukuManager)

    private val _isDiagnosticRunning = MutableStateFlow(false)
    private val _secureSettingsGranted = MutableStateFlow(checkSecureSettingsPermission())
    private val _notificationGranted = MutableStateFlow(checkNotificationPermission())
    private val _batteryOptimizationIgnored = MutableStateFlow(checkBatteryOptimization())
    private val _showTestReport = MutableStateFlow(false)
    private val _showTestError = MutableStateFlow(false)
    private val _settingsLoaded = MutableStateFlow(false)

    val testState: StateFlow<TestState> = streamTestEngine.testState
    val showTestReport: StateFlow<Boolean> = _showTestReport.asStateFlow()
    val showTestError: StateFlow<Boolean> = _showTestError.asStateFlow()

    val appTheme: StateFlow<AppTheme> = settingsRepository.appTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM
        )

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    private val permissionsFlow = combine(
        _secureSettingsGranted,
        _notificationGranted,
        _batteryOptimizationIgnored
    ) { secure, notif, battery ->
        Triple(secure, notif, battery)
    }

    val uiState: StateFlow<MainUiState> = combine(
        networkMonitor.networkType,
        BandwidthControlService.isRunning,
        settingsRepository.bandwidthLimitMbps,
        _isDiagnosticRunning,
        combine(shizukuManager.status, permissionsFlow, _settingsLoaded) { shizuku, perms, loaded ->
            Triple(shizuku, perms, loaded)
        }
    ) { networkType, isRunning, limit, diagnosticRunning, (shizukuStatus, perms, settingsLoaded) ->
        val (hasSecureSettings, hasNotification, isIgnoringBattery) = perms
        MainUiState(
            networkType = networkType,
            isServiceRunning = isRunning,
            hasSecureSettingsPermission = hasSecureSettings,
            hasNotificationPermission = hasNotification,
            isIgnoringBatteryOptimizations = isIgnoringBattery,
            bandwidthLimitMbps = limit,
            isDiagnosticRunning = diagnosticRunning,
            shizukuStatus = shizukuStatus,
            settingsLoaded = settingsLoaded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        networkMonitor.startMonitoring()
        // Gate the picker's write-back: the persisted limit must be known before any
        // UI-driven change is allowed to persist (S1-02).
        viewModelScope.launch {
            settingsRepository.bandwidthLimitMbps.first()
            _settingsLoaded.value = true
        }
    }

    /**
     * Clear a throttling cap that outlived the service (S1-06): if the persisted
     * state says a limit is applied but the service is not running, reset it.
     */
    fun reconcileResidualLimit() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastApplied = settingsRepository.lastAppliedLimitBytes.first()
                // The service is the only thing that may keep the cap applied. After an
                // abrupt kill (SIGKILL/force-stop) the process restarted, so isRunning
                // is false while DataStore is stale — trust the live state, not the flag.
                if (!BandwidthControlService.isRunning.value && lastApplied != SettingsRepository.NO_LIMIT) {
                    Log.w(TAG, "Residual bandwidth limit $lastApplied detected at startup; resetting")
                    bandwidthController.resetToDefault()
                        .onSuccess { settingsRepository.setLastAppliedLimitBytes(SettingsRepository.NO_LIMIT) }
                        .onFailure { e -> Log.e(TAG, "Residual reset failed (permission?)", e) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Residual limit reconciliation failed", e)
            }
        }
    }

    private fun checkSecureSettingsPermission(): Boolean {
        return try {
            val packageInfo = getApplication<Application>().packageManager.getPackageInfo(
                getApplication<Application>().packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS
            )
            val requestedPermissions = packageInfo.requestedPermissions
            val requestedPermissionsFlags = packageInfo.requestedPermissionsFlags
            if (requestedPermissions != null && requestedPermissionsFlags != null) {
                for (i in requestedPermissions.indices) {
                    if (requestedPermissions[i] == Manifest.permission.WRITE_SECURE_SETTINGS) {
                        return (requestedPermissionsFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkNotificationPermission(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun checkBatteryOptimization(): Boolean {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun toggleService(enable: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, BandwidthControlService::class.java)
        if (enable) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
        viewModelScope.launch {
            settingsRepository.setServiceEnabled(enable)
        }
    }

    fun updateBandwidthLimit(limit: Float) {
        // Ignore writes that arrive before the persisted value has been loaded:
        // the picker emits its initial index during the first composition and that
        // must not overwrite the stored limit (S1-02).
        if (!_settingsLoaded.value) return
        viewModelScope.launch {
            settingsRepository.setBandwidthLimitMbps(limit)
        }
    }

    fun start100KbpsTest() {
        val context = getApplication<Application>()
        val intent = Intent(context, BandwidthControlService::class.java).apply {
            action = BandwidthControlService.ACTION_SET_DIAGNOSTIC
            putExtra(BandwidthControlService.EXTRA_LIMIT_BYTES, StreamTestEngine.TARGET_RATE_BYTES_PER_SEC)
        }
        ContextCompat.startForegroundService(context, intent)
        _isDiagnosticRunning.value = true
        _showTestError.value = false

        val resetIntent = Intent(context, BandwidthControlService::class.java).apply {
            action = BandwidthControlService.ACTION_SET_DIAGNOSTIC
            putExtra(BandwidthControlService.EXTRA_LIMIT_BYTES, -1L)
        }

        // The kernel shaper governs connections opened *after* the cap is
        // installed, so the test stream must not connect before the diagnostic
        // cap is visible in Settings.Global. Poll for confirmation (max 3 s)
        // and only start measuring once the 100 kbps cap is verified live.
        viewModelScope.launch {
            val applied = withTimeoutOrNull(LIMIT_CONFIRM_TIMEOUT_MS) {
                while (bandwidthController.currentIngressRateLimit()
                    != StreamTestEngine.TARGET_RATE_BYTES_PER_SEC
                ) {
                    delay(100)
                }
                true
            }
            if (applied == true) {
                streamTestEngine.startTest(StreamTestEngine.TARGET_RATE_BYTES_PER_SEC) { finalState ->
                    // Callback fires from the engine, possibly while the app is in
                    // the background: the service is already running, so
                    // startForegroundService is the allowed form; runCatching keeps
                    // the engine from crashing the process if it throws (S1-05).
                    runCatching {
                        ContextCompat.startForegroundService(context, resetIntent)
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to clear diagnostic limit", e)
                    }
                    _isDiagnosticRunning.value = false
                    if (finalState.status == TestStatus.COMPLETED) {
                        _showTestReport.value = true
                    } else if (finalState.status == TestStatus.ERROR) {
                        _showTestError.value = true
                    }
                }
            } else {
                Log.e(TAG, "Diagnostic limit not confirmed within ${LIMIT_CONFIRM_TIMEOUT_MS}ms; aborting test")
                runCatching {
                    ContextCompat.startForegroundService(context, resetIntent)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to clear unconfirmed diagnostic limit", e)
                }
                _isDiagnosticRunning.value = false
                streamTestEngine.reportLimitNotConfirmed(
                    context.getString(R.string.test_error_limit_not_applied)
                )
                _showTestError.value = true
            }
        }
    }

    fun cancel100KbpsTest() {
        streamTestEngine.cancelTest()
        val context = getApplication<Application>()
        val resetIntent = Intent(context, BandwidthControlService::class.java).apply {
            action = BandwidthControlService.ACTION_SET_DIAGNOSTIC
            putExtra(BandwidthControlService.EXTRA_LIMIT_BYTES, -1L)
        }
        runCatching { ContextCompat.startForegroundService(context, resetIntent) }
            .onFailure { e -> Log.e(TAG, "Failed to clear diagnostic limit", e) }
        _isDiagnosticRunning.value = false
    }

    fun dismissTestReport() {
        _showTestReport.value = false
        streamTestEngine.reset()
    }

    fun dismissTestError() {
        _showTestError.value = false
        streamTestEngine.reset()
    }

    fun safetyReset() {
        streamTestEngine.cancelTest()
        _isDiagnosticRunning.value = false
        // Stop via the dedicated action: startService() on the diagnostic-reset
        // intent would *create* the service and re-foreground it (S1-03).
        // startService is a synchronous binder handshake with AMS: running it on
        // a worker thread keeps the UI thread responsive even under system load.
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.startService(
                    Intent(context, BandwidthControlService::class.java).apply {
                        action = BandwidthControlService.ACTION_STOP_SERVICE
                    }
                )
            }.onFailure { e -> Log.e(TAG, "Failed to deliver stop request", e) }
            settingsRepository.setServiceEnabled(false)
            settingsRepository.setBandwidthLimitMbps(1.0f)
            bandwidthController.resetToDefault()
                .onSuccess { settingsRepository.setLastAppliedLimitBytes(SettingsRepository.NO_LIMIT) }
        }
    }

    fun refreshState() {
        _secureSettingsGranted.value = checkSecureSettingsPermission()
        _notificationGranted.value = checkNotificationPermission()
        _batteryOptimizationIgnored.value = checkBatteryOptimization()
        shizukuManager.updateStatus()
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun grantViaShizuku() {
        viewModelScope.launch {
            if (shizukuManager.grantWriteSecureSettings()) {
                shizukuManager.updateStatus()
                refreshState()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // S2-01: unregister the network callback for every ViewModel we create.
        networkMonitor.stopMonitoring()
        // S1-05: the diagnostic must not outlive the ViewModel.
        streamTestEngine.shutdown()
        shizukuManager.onDestroy()
    }
}
