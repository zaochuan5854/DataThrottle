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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED
) {
    val hasAllPermissions: Boolean
        get() = hasSecureSettingsPermission && hasNotificationPermission && isIgnoringBatteryOptimizations
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val networkMonitor = NetworkMonitor(application)
    private val bandwidthController = BandwidthController(application.contentResolver)
    private val shizukuManager = ShizukuManager(application)
    private val streamTestEngine = StreamTestEngine(application)

    private val _isDiagnosticRunning = MutableStateFlow(false)
    private val _secureSettingsGranted = MutableStateFlow(checkSecureSettingsPermission())
    private val _notificationGranted = MutableStateFlow(checkNotificationPermission())
    private val _batteryOptimizationIgnored = MutableStateFlow(checkBatteryOptimization())
    private val _showTestReport = MutableStateFlow(false)
    private val _showTestError = MutableStateFlow(false)

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
        combine(shizukuManager.status, permissionsFlow, ::Pair)
    ) { networkType, isRunning, limit, diagnosticRunning, (shizukuStatus, perms) ->
        val (hasSecureSettings, hasNotification, isIgnoringBattery) = perms
        MainUiState(
            networkType = networkType,
            isServiceRunning = isRunning,
            hasSecureSettingsPermission = hasSecureSettings,
            hasNotificationPermission = hasNotification,
            isIgnoringBatteryOptimizations = isIgnoringBattery,
            bandwidthLimitMbps = limit,
            isDiagnosticRunning = diagnosticRunning,
            shizukuStatus = shizukuStatus
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        networkMonitor.startMonitoring()
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
        context.startService(intent)
        _isDiagnosticRunning.value = true
        _showTestError.value = false

        streamTestEngine.startTest(StreamTestEngine.TARGET_RATE_BYTES_PER_SEC) { finalState ->
            val resetIntent = Intent(context, BandwidthControlService::class.java).apply {
                action = BandwidthControlService.ACTION_SET_DIAGNOSTIC
                putExtra(BandwidthControlService.EXTRA_LIMIT_BYTES, -1L)
            }
            context.startService(resetIntent)
            _isDiagnosticRunning.value = false
            if (finalState.status == TestStatus.COMPLETED) {
                _showTestReport.value = true
            } else if (finalState.status == TestStatus.ERROR) {
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
        context.startService(resetIntent)
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
        viewModelScope.launch {
            toggleService(false)
            cancel100KbpsTest()
            settingsRepository.setBandwidthLimitMbps(1.0f)
            bandwidthController.resetToDefault()
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
        shizukuManager.onDestroy()
    }
}
