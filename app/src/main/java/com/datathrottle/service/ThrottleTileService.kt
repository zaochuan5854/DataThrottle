package com.datathrottle.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.datathrottle.R
import com.datathrottle.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThrottleTileService : TileService() {

    private var tileScope: CoroutineScope? = null
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        tileScope?.cancel()
        tileScope = CoroutineScope(Dispatchers.Main + Job()).apply {
            launch {
                combine(
                    BandwidthControlService.isRunning,
                    settingsRepository.bandwidthLimitMbps
                ) { isRunning, limitMbps ->
                    updateTile(isRunning, limitMbps)
                }.collect {}
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        tileScope?.cancel()
        tileScope = null
    }

    override fun onClick() {
        super.onClick()
        val isRunning = BandwidthControlService.isRunning.value
        val nextState = !isRunning

        val intent = Intent(this, BandwidthControlService::class.java)
        if (nextState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }

        tileScope?.launch {
            settingsRepository.setServiceEnabled(nextState)
            val limit = settingsRepository.bandwidthLimitMbps.first()
            updateTile(nextState, limit)
        }
    }

    private fun updateTile(isRunning: Boolean, limitMbps: Float) {
        val tile = qsTile ?: return
        val formattedLimit = if (limitMbps < 1.0f) String.format("%.1f Mbps", limitMbps) else if (limitMbps % 1.0f == 0f) String.format("%.0f Mbps", limitMbps) else String.format("%.1f Mbps", limitMbps)

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.app_name)
            tile.subtitle = formattedLimit
            tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_bandwidth)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
            tile.subtitle = getString(R.string.service_status_stopped)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_bandwidth)
        }
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, ThrottleTileService::class.java)
                    )
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
