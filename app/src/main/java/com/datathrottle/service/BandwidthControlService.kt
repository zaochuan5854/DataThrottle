package com.datathrottle.service

import android.app.DownloadManager
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
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.datathrottle.MainActivity
import com.datathrottle.R
import com.datathrottle.core.BandwidthController
import com.datathrottle.core.NetworkMonitor
import com.datathrottle.core.NetworkType
import com.datathrottle.core.formatMbps
import com.datathrottle.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class BandwidthControlService : Service() {

    private lateinit var bandwidthController: BandwidthController
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var settingsRepository: SettingsRepository
    private val notificationId = 1
    private val alertNotificationId = 2

    private val channelIdService = "bandwidth_service_status_v1"
    private val channelIdAlerts = "bandwidth_alerts_channel_v1"

    private var serviceJob: Job? = null
    // Enforcement is ContentProvider IPC (Settings.Global) and DataStore disk I/O:
    // keep it off the main thread (S1-07). Teardown paths reset synchronously.
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var diagnosticLimit: Long? = null
    private var currentLimitMbps: Float = 1.0f
    private var lastAppliedLimit: Long? = null
    private var lastAppliedType: NetworkType? = null

    // Notification-driven 60 s unlimit (toggle). Volatile: written on the
    // service scope, read when (re)building the notification on the main thread.
    @Volatile private var paused = false
    @Volatile private var pauseRemainingSec = 0
    private var pauseJob: Job? = null

    companion object {
        private const val TAG = "BandwidthControlService"
        private const val UNLIMITED = -1L
        private const val TEARDOWN_TIMEOUT_MS = 1500L
        const val MBPS_TO_BYTES_PER_SECOND = 125000L

        const val ACTION_STOP_SERVICE = "com.datathrottle.STOP_SERVICE"
        const val ACTION_SET_DIAGNOSTIC = "com.datathrottle.SET_DIAGNOSTIC"
        const val ACTION_DEBUG_DM_PROBE = "com.datathrottle.DEBUG_DM_PROBE"
        const val ACTION_PAUSE_60S = "com.datathrottle.PAUSE_60S"

        /** The notification "unlimit" button releases the throttle for this long. */
        private const val PAUSE_MS = 60_000L

        // E7: probe object must be ≫ shaper burst (token-bucket), so use a 2 MB
        // object instead of the 82 KB test image. Range requests supported (206).
        private const val DM_PROBE_URL = "https://files.catbox.moe/64uvzg.bin"
        private const val DM_PROBE_TIMEOUT_MS = 300_000L
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
            ACTION_DEBUG_DM_PROBE -> runDebugDmProbe()
            ACTION_PAUSE_60S -> togglePause()
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

    /**
     * Debug-only DownloadManager transport probe (E3a definitive verdict):
     * enqueues the test image on the DownloadProvider uid and logs byte
     * progress, so shaping scope for uid 10099 can be measured in the same
     * window as a shell-uid control transfer.
     */
    private fun runDebugDmProbe() {
        val manager = getSystemService(DownloadManager::class.java)
        if (manager == null) {
            Log.e(TAG, "DM probe: no DownloadManager")
            return
        }
        val dest = java.io.File(getExternalFilesDir(null), "dt_probe.bin")
        dest.delete()
        val request = android.app.DownloadManager.Request(
            Uri.parse(DM_PROBE_URL)
        ).apply {
            setDestinationUri(Uri.fromFile(dest))
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val id = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "DM probe: enqueue failed: ${e.message}")
            return
        }
        serviceScope.launch {
            val t0 = System.nanoTime()
            var lastLogAt = 0L
            while (true) {
                delay(500)
                val now = System.nanoTime()
                val elapsedMs = (now - t0) / 1_000_000L
                val (status, bytes) = withContext(Dispatchers.IO) {
                    manager.query(
                        android.app.DownloadManager.Query().setFilterById(id)
                    ).use { c ->
                        if (c == null || !c.moveToFirst()) {
                            android.app.DownloadManager.STATUS_FAILED to 0L
                        } else {
                            c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)) to
                                c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        }
                    }
                }
                if (elapsedMs - lastLogAt >= 1000) {
                    Log.d(TAG, "DM probe t=${elapsedMs}ms bytes=$bytes")
                    lastLogAt = elapsedMs
                }
                when (status) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        val avgKbps = bytes * 8.0 / elapsedMs.coerceAtLeast(1)
                        Log.d(TAG, "DM probe DONE bytes=$bytes elapsedMs=$elapsedMs avgKbps=$avgKbps")
                        withContext(Dispatchers.IO) { manager.remove(id) }
                        return@launch
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        Log.e(TAG, "DM probe FAILED at ${elapsedMs}ms bytes=$bytes")
                        withContext(Dispatchers.IO) { manager.remove(id) }
                        return@launch
                    }
                }
                if (elapsedMs > DM_PROBE_TIMEOUT_MS) {
                    Log.e(TAG, "DM probe TIMEOUT at bytes=$bytes")
                    withContext(Dispatchers.IO) { manager.remove(id) }
                    return@launch
                }
            }
        }
    }

    private fun applyAppropriateLimit(limitMbps: Float, networkType: NetworkType) {
        val limit = when {
            paused -> UNLIMITED
            diagnosticLimit != null -> diagnosticLimit!!
            networkType == NetworkType.CELLULAR -> (limitMbps * MBPS_TO_BYTES_PER_SECOND).toLong()
            else -> UNLIMITED
        }

        val hasStateChanged = (lastAppliedLimit != null) && (limit != lastAppliedLimit || networkType != lastAppliedType)
        lastAppliedLimit = limit
        lastAppliedType = networkType

        Log.d(TAG, "Applying bandwidth limit: $limit (Type: $networkType, Diag: ${diagnosticLimit != null}, StateChanged: $hasStateChanged)")
        var enforced = true
        bandwidthController.setIngressRateLimit(limit)
            .onFailure { e ->
                enforced = false
                Log.e(TAG, "Bandwidth limit NOT enforced ($limit): ${e.message}", e)
                showPermissionErrorNotification()
            }
        // Record the applied value so fail-safe paths (task removal, boot, launch)
        // can clear a residual limit even if this process is killed abruptly.
        if (enforced) {
            serviceScope.launch {
                settingsRepository.setLastAppliedLimitBytes(limit)
            }
        }
        updateNotification(networkType, diagnosticLimit != null, limitMbps, shouldAlert = hasStateChanged, notEnforced = !enforced)
    }

    /**
     * The notification "unlimit" button: a 60 s toggle. Ignored while a
     * diagnostic scanline test owns the cap (pause would distort the verdict).
     * Re-tapping while paused resumes the throttle immediately.
     */
    private fun togglePause() {
        if (diagnosticLimit != null) {
            Log.w(TAG, "Pause request ignored: diagnostic test is running")
            return
        }
        pauseJob?.cancel()
        pauseJob = null
        if (!paused) {
            paused = true
            pauseRemainingSec = (PAUSE_MS / 1000L).toInt()
            Log.d(TAG, "Throttle paused for ${PAUSE_MS / 1000} s")
            pauseJob = serviceScope.launch {
                val m = settingsRepository.bandwidthLimitMbps.first()
                applyAppropriateLimit(m, networkMonitor.networkType.value)
                // Refresh the notification once per second with the live
                // remaining-seconds countdown until the window elapses.
                val t0 = System.currentTimeMillis()
                while (true) {
                    delay(1000)
                    val remaining = ((PAUSE_MS - (System.currentTimeMillis() - t0)) / 1000L).toInt()
                    if (remaining <= 0) break
                    pauseRemainingSec = remaining
                    updateNotification(networkMonitor.networkType.value,
                        isDiagnostic = false,
                        limitMbps = currentLimitMbps)
                }
                pauseRemainingSec = 0
                paused = false
                pauseJob = null
                Log.d(TAG, "Pause window elapsed: resuming throttle")
                val m2 = settingsRepository.bandwidthLimitMbps.first()
                applyAppropriateLimit(m2, networkMonitor.networkType.value)
            }
        } else {
            paused = false
            pauseRemainingSec = 0
            Log.d(TAG, "Pause cancelled early: resuming throttle now")
            serviceScope.launch {
                val m = settingsRepository.bandwidthLimitMbps.first()
                applyAppropriateLimit(m, networkMonitor.networkType.value)
            }
        }
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
        shouldAlert: Boolean = false,
        notEnforced: Boolean = false
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

        // Single toggle intent: label/color flip with [paused].
        val pausePendingIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, BandwidthControlService::class.java).apply { action = ACTION_PAUSE_60S },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedLimit = formatMbps(limitMbps)

        val title = when {
            notEnforced -> getString(R.string.status_not_enforced)
            paused -> getString(R.string.status_paused)
            isDiagnostic -> getString(R.string.test_running)
            type == NetworkType.CELLULAR -> getString(R.string.status_limited_to, formattedLimit)
            type == NetworkType.WIFI -> getString(R.string.status_unlimited_wifi)
            else -> getString(R.string.status_unlimited)
        }

        val desc = when {
            notEnforced -> getString(R.string.status_not_enforced_desc)
            paused -> getString(R.string.status_desc_paused, pauseRemainingSec)
            isDiagnostic -> getString(R.string.notification_desc_test)
            type == NetworkType.CELLULAR -> getString(R.string.status_desc_cellular, formattedLimit).replace("\n", " ")
            type == NetworkType.WIFI -> getString(R.string.status_desc_wifi).replace("\n", " ")
            else -> getString(R.string.status_desc_disabled).replace("\n", " ")
        }

        val color = when {
            notEnforced -> Color.parseColor("#DC2626") // Red
            paused -> Color.parseColor("#DC2626") // Red: limit currently off
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
            // Pause toggle: offered while a cellular throttle is active, and
            // (as "resume") while the pause window is open. Not offered during
            // a diagnostic test — the pause path ignores it anyway.
            .apply {
                if (paused || (type == NetworkType.CELLULAR && !isDiagnostic)) {
                    addAction(
                        R.drawable.ic_stat_speed,
                        getString(if (paused) R.string.notification_action_resume
                                 else R.string.notification_action_pause_1min),
                        pausePendingIntent
                    )
                }
            }
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
        shouldAlert: Boolean = false,
        notEnforced: Boolean = false
    ) {
        val notification = createNotification(type, isDiagnostic, limitMbps, shouldAlert, notEnforced)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Fail-safe (S1-06): when the user swipes the task away, clear the persistent
     * kernel-level cap before the process dies. The write is intentionally
     * synchronous — an async write may never run if the system kills us right after.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: clearing bandwidth limit (fail-safe)")
        serviceJob?.cancel()
        // Fail-safe teardown: must land before process death (S1-06), but bounded
        // so a contended system_server cannot stall the main thread into an ANR.
        teardownSynchronously()
        // DataStore bookkeeping is best-effort on a detached scope: if the write
        // races with process death, launch-time reconciliation clears the residual.
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            settingsRepository.setLastAppliedLimitBytes(UNLIMITED)
            settingsRepository.setServiceEnabled(false)
        }
        ThrottleTileService.requestTileUpdate(this)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        _isRunning.value = false
        serviceJob?.cancel()
        networkMonitor.stopMonitoring()
        // Synchronous teardown: the reset must land before process death (S1-06).
        teardownSynchronously()
        GlobalScope.launch(Dispatchers.IO + NonCancellable) {
            settingsRepository.setLastAppliedLimitBytes(UNLIMITED)
            settingsRepository.setServiceEnabled(false)
        }
        ThrottleTileService.requestTileUpdate(this)
    }

    /**
     * Kernel-level teardown executed on the calling (main) thread with a hard
     * timeout: a write through SettingsProvider is a synchronous binder call and
     * an unbounded join here is a known ANR source under heavy system load.
     */
    private fun teardownSynchronously() {
        runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeout(TEARDOWN_TIMEOUT_MS) {
                    bandwidthController.resetToDefault()
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Teardown timed out; relying on launch-time reconciliation", e)
        }
    }
}

