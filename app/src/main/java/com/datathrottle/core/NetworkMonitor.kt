package com.datathrottle.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

import com.datathrottle.debug.DebugFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class NetworkType {
    WIFI, CELLULAR, OTHER, NONE
}

class NetworkMonitor(
    context: Context,
    private val onNetworkChanged: ((NetworkType) -> Unit)? = null
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val spoofScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _networkType = MutableStateFlow(getCurrentNetworkType())

    /**
     * Effective network type: the debug Wi-Fi-as-cellular spoof is applied on
     * top of the real detection. In release builds the flag is hard-wired to
     * false (see DebugFlags source-set split), so this is the real type.
     */
    val networkType: StateFlow<NetworkType> =
        combine(_networkType, DebugFlags.forceCellular) { real, forceCellular ->
            if (forceCellular && real != NetworkType.NONE) NetworkType.CELLULAR else real
        }.stateIn(spoofScope, SharingStarted.Eagerly, _networkType.value)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val type = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }
            
            if (type != _networkType.value) {
                _networkType.value = type
                Log.d("NetworkMonitor", "Network type changed to: $type")
                onNetworkChanged?.invoke(type)
            }
        }

        override fun onLost(network: Network) {
            if (_networkType.value != NetworkType.NONE) {
                _networkType.value = NetworkType.NONE
                Log.d("NetworkMonitor", "Network lost")
                onNetworkChanged?.invoke(NetworkType.NONE)
            }
        }
    }

    private var registered = false

    fun startMonitoring() {
        if (registered) return
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        registered = true
    }

    fun stopMonitoring() {
        if (!registered) return
        connectivityManager.unregisterNetworkCallback(networkCallback)
        registered = false
    }

    fun getCurrentNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }
}
