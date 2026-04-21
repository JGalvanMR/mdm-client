package com.mdm.client.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected: Boolean
        get() {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    /** Flow que emite true/false cada vez que cambia la conectividad. */
    val connectivityFlow: Flow<Boolean> =
            callbackFlow {
                        val callback =
                                object : ConnectivityManager.NetworkCallback() {
                                    override fun onAvailable(network: Network) {
                                        trySend(true)
                                    }
                                    override fun onLost(network: Network) {
                                        trySend(false)
                                    }
                                    override fun onUnavailable() {
                                        trySend(false)
                                    }
                                    override fun onCapabilitiesChanged(
                                            network: Network,
                                            caps: NetworkCapabilities
                                    ) {
                                        val valid =
                                                caps.hasCapability(
                                                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                                                ) &&
                                                        caps.hasCapability(
                                                                NetworkCapabilities
                                                                        .NET_CAPABILITY_VALIDATED
                                                        )
                                        trySend(valid)
                                    }
                                }

                        val request =
                                NetworkRequest.Builder()
                                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                        .build()

                        cm.registerNetworkCallback(request, callback)
                        trySend(isConnected) // Estado inicial

                        awaitClose { cm.unregisterNetworkCallback(callback) }
                    }
                    .distinctUntilChanged()
}
