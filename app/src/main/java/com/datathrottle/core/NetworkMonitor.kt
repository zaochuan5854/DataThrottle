package com.datathrottle.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkType {
    WIFI, CELLULAR, OTHER, NONE
}

class NetworkMonitor(
    context: Context,
    private val onNetworkChanged: ((NetworkType) -> Unit)? = null
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

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

    fun startMonitoring() {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    fun stopMonitoring() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
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
