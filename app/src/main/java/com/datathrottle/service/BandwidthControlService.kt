package com.datathrottle.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.datathrottle.MainActivity
import com.datathrottle.R
import com.datathrottle.core.BandwidthController
import com.datathrottle.core.NetworkMonitor
import com.datathrottle.core.NetworkType
import com.datathrottle.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BandwidthControlService : Service() {

    private lateinit var bandwidthController: BandwidthController
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var settingsRepository: SettingsRepository
    private val notificationId = 1
    private val alertNotificationId = 2

    private val channelIdService = "bandwidth_service_status_v1"
    private val channelIdAlerts = "bandwidth_alerts_channel_v1"

    private var serviceJob: Job? = null
    private var testTimerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var diagnosticLimit: Long? = null
    private var currentLimitMbps: Float = 1.0f
    private var lastAppliedLimit: Long? = null
    private var lastAppliedType: NetworkType? = null

    companion object {
        private const val TAG = "BandwidthControlService"
        private const val UNLIMITED = -1L
        const val MBPS_TO_BYTES_PER_SECOND = 125000L
        const val RATE_5KB_PER_SECOND = 5000L // 5 KB/s = 40 kbps

        const val ACTION_STOP_SERVICE = "com.datathrottle.STOP_SERVICE"
        const val ACTION_SET_DIAGNOSTIC = "com.datathrottle.SET_DIAGNOSTIC"
        const val EXTRA_LIMIT_BYTES = "limit_bytes"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        _isRunning.value = true
        bandwidthController = BandwidthController(contentResolver)
        settingsRepository = SettingsRepository(this)
        createNotificationChannels()
        
        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()

        serviceScope.launch {
            settingsRepository.setServiceEnabled(true)
            ThrottleTileService.requestTileUpdate(this@BandwidthControlService)
        }

        serviceJob = serviceScope.launch {
            combine(
                settingsRepository.bandwidthLimitMbps,
                networkMonitor.networkType
            ) { limitMbps, networkType ->
                currentLimitMbps = limitMbps
                applyAppropriateLimit(limitMbps, networkType)
            }.collect {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Stopping service from notification action")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_DIAGNOSTIC -> {
                val limit = intent.getLongExtra(EXTRA_LIMIT_BYTES, -1L)
                diagnosticLimit = if (limit == -1L) null else limit
                serviceScope.launch {
                    val limitMbps = settingsRepository.bandwidthLimitMbps.first()
                    applyAppropriateLimit(limitMbps, networkMonitor.networkType.value)
                }
            }
        }

        val initialType = networkMonitor.networkType.value
        val notification = createNotification(initialType, diagnosticLimit != null, currentLimitMbps, shouldAlert = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        
        return START_STICKY
    }

    private fun applyAppropriateLimit(limitMbps: Float, networkType: NetworkType) {
        val limit = when {
            diagnosticLimit != null -> diagnosticLimit!!
            networkType == NetworkType.CELLULAR -> (limitMbps * MBPS_TO_BYTES_PER_SECOND).toLong()
            else -> UNLIMITED
        }

        val hasStateChanged = (lastAppliedLimit != null) && (limit != lastAppliedLimit || networkType != lastAppliedType)
        lastAppliedLimit = limit
        lastAppliedType = networkType

        Log.d(TAG, "Applying bandwidth limit: $limit (Type: $networkType, Diag: ${diagnosticLimit != null}, StateChanged: $hasStateChanged)")
        try {
            bandwidthController.setIngressRateLimit(limit)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to set bandwidth limit: Missing WRITE_SECURE_SETTINGS permission", e)
            showPermissionErrorNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error setting bandwidth limit", e)
        }
        updateNotification(networkType, diagnosticLimit != null, limitMbps, shouldAlert = hasStateChanged)
    }

    private fun showPermissionErrorNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelIdAlerts)
            .setContentTitle(getString(R.string.permission_error_title))
            .setContentText(getString(R.string.permission_error_message))
            .setSmallIcon(R.drawable.ic_stat_bandwidth)
            .setColor(Color.RED)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(alertNotificationId, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Delete old silent channels if present
            try {
                manager.deleteNotificationChannel("bandwidth_control_service_channel")
                manager.deleteNotificationChannel("bandwidth_control_channel")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning old channels", e)
            }

            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val serviceChannel = NotificationChannel(
                channelIdService,
                getString(R.string.notification_channel_service_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_service_description)
                setSound(defaultSound, audioAttributes)
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(serviceChannel)

            val alertsChannel = NotificationChannel(
                channelIdAlerts,
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alerts_description)
                setSound(defaultSound, audioAttributes)
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(alertsChannel)
        }
    }

    private fun createNotification(
        type: NetworkType,
        isDiagnostic: Boolean = false,
        limitMbps: Float = 1.0f,
        shouldAlert: Boolean = false
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, BandwidthControlService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedLimit = if (limitMbps < 1.0f) String.format("%.1f Mbps", limitMbps) else if (limitMbps % 1.0f == 0f) String.format("%.0f Mbps", limitMbps) else String.format("%.1f Mbps", limitMbps)

        val title = when {
            isDiagnostic -> getString(R.string.test_running)
            type == NetworkType.CELLULAR -> getString(R.string.status_limited_to, formattedLimit)
            type == NetworkType.WIFI -> getString(R.string.status_unlimited_wifi)
            else -> getString(R.string.status_unlimited)
        }

        val desc = when {
            isDiagnostic -> getString(R.string.notification_desc_test)
            type == NetworkType.CELLULAR -> getString(R.string.status_desc_cellular, formattedLimit).replace("\n", " ")
            type == NetworkType.WIFI -> getString(R.string.status_desc_wifi).replace("\n", " ")
            else -> getString(R.string.status_desc_disabled).replace("\n", " ")
        }

        val color = when {
            isDiagnostic -> Color.parseColor("#00E5FF") // Cyan
            type == NetworkType.CELLULAR -> Color.parseColor("#2563EB") // Blue
            type == NetworkType.WIFI -> Color.parseColor("#0288D1") // Light Blue
            else -> Color.parseColor("#757575") // Grey
        }

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelIdService)
            .setContentTitle(title)
            .setContentText(desc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(desc))
            .setSmallIcon(R.drawable.ic_stat_bandwidth)
            .setColor(color)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_stat_stop,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )

        if (shouldAlert) {
            builder.setOnlyAlertOnce(false)
                .setSound(defaultSound)
                .setDefaults(Notification.DEFAULT_ALL)
        } else {
            builder.setOnlyAlertOnce(true)
        }

        return builder.build()
    }

    private fun updateNotification(
        type: NetworkType,
        isDiagnostic: Boolean = false,
        limitMbps: Float = 1.0f,
        shouldAlert: Boolean = false
    ) {
        val notification = createNotification(type, isDiagnostic, limitMbps, shouldAlert)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        _isRunning.value = false
        serviceJob?.cancel()
        testTimerJob?.cancel()
        networkMonitor.stopMonitoring()
        bandwidthController.resetToDefault()

        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.setServiceEnabled(false)
        }
        ThrottleTileService.requestTileUpdate(this)
    }
}

