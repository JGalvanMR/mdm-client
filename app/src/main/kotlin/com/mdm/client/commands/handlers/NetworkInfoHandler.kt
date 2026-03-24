package com.mdm.client.commands.handlers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class NetworkInfoHandler(private val context: Context) {
    private val TAG  = "NetworkInfoHandler"
    private val gson = Gson()

    fun execute(): ExecutionResult {
        return try {
            val cm      = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps    = network?.let { cm.getNetworkCapabilities(it) }
            val wm      = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wm.connectionInfo

            val data = mapOf(
                "isConnected"   to (network != null),
                "hasWifi"       to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true),
                "hasCellular"   to (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true),
                "ssid"          to (wifiInfo.ssid?.replace("\"", "") ?: ""),
                "signalStrength" to wifiInfo.rssi,
                "linkSpeed"     to wifiInfo.linkSpeed,
                "ipAddress"     to formatIp(wifiInfo.ipAddress)
            )
            MdmLog.i(TAG, "Network info recopilada")
            ExecutionResult.success(gson.toJson(data))
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error: ${e.message}", e)
            ExecutionResult.failure("Error obteniendo info de red: ${e.message}")
        }
    }

    private fun formatIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}