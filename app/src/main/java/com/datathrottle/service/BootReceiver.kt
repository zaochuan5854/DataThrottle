package com.datathrottle.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.datathrottle.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Boot completed event received: ${intent.action}")
            val settingsRepository = SettingsRepository(context)
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val isServiceEnabled = settingsRepository.isServiceEnabled.first()
                    Log.d(TAG, "Restoring service on boot: isServiceEnabled=$isServiceEnabled")
                    if (isServiceEnabled) {
                        val serviceIntent = Intent(context, BandwidthControlService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring BandwidthControlService on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
