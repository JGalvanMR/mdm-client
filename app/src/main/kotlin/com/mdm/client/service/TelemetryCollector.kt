package com.mdm.client.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.mdm.client.data.models.TelemetryReport
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.device.DeviceInfoCollector

class TelemetryCollector(private val context: Context) {
        private val infoCollector = DeviceInfoCollector(context)
        private val prefs = DevicePrefs(context)

        fun collect(): TelemetryReport {
                val battIntent =
                        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = battIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging =
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                val percent = if (scale > 0) level * 100 / scale else -1
                val uptimeHours = SystemClock.elapsedRealtime() / 3_600_000L

                // RAM
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val ramUsed = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)

                // Pantalla encendida
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val screenOn = pm.isInteractive

                // Red
                val cm =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as
                                ConnectivityManager
                val network = cm.activeNetwork
                val caps = network?.let { cm.getNetworkCapabilities(it) }
                val connType =
                        when {
                                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ->
                                        "WIFI"
                                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ==
                                        true -> "CELLULAR"
                                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ==
                                        true -> "ETHERNET"
                                else -> "NONE"
                        }

                // WiFi SSID y señal
                val wm =
                        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as
                                WifiManager
                @Suppress("DEPRECATION") val wifiInfo = wm.connectionInfo
                val ssid =
                        if (connType == "WIFI")
                                wifiInfo.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" }
                        else null
                val signal = if (connType == "WIFI") wifiInfo.rssi else null

                return TelemetryReport(
                        batteryLevel = percent,
                        batteryCharging = isCharging,
                        storageAvailableMB = infoCollector.getAvailableStorageMB(),
                        totalStorageMB = infoCollector.getTotalStorageMB(),
                        latitude = prefs.lastKnownLatitude.takeIf { it != 0.0 },
                        longitude = prefs.lastKnownLongitude.takeIf { it != 0.0 },
                        locationAgeSeconds =
                                if (prefs.lastLocationTimestamp > 0)
                                        (System.currentTimeMillis() - prefs.lastLocationTimestamp) /
                                                1000
                                else null,
                        locationAccuracy = null,
                        connectionType = connType,
                        ssid = ssid,
                        signalStrength = signal,
                        ipAddress = infoCollector.getLocalIpAddress(),
                        kioskModeEnabled = prefs.kioskModeEnabled,
                        cameraDisabled = prefs.cameraDisabled,
                        screenOn = screenOn,
                        uptimeHours = uptimeHours,
                        ramUsedMB = ramUsed,
                        cpuTemp = null
                )
        }
}
