package com.mdm.client.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import com.mdm.client.core.MdmLog
import com.mdm.client.data.models.DeviceInfoPayload
import com.mdm.client.data.prefs.DevicePrefs

class DeviceInfoCollector(private val context: Context) {

    private val TAG   = "DeviceInfoCollector"
    private val prefs = DevicePrefs(context)

    fun collect(): DeviceInfoPayload {
        return DeviceInfoPayload(
            deviceId           = prefs.getOrCreateDeviceId(context),
            model              = Build.MODEL,
            manufacturer       = Build.MANUFACTURER,
            androidVersion     = Build.VERSION.RELEASE,
            apiLevel           = Build.VERSION.SDK_INT,
            batteryLevel       = getBatteryLevel(),
            storageAvailableMB = getAvailableStorageMB(),
            totalStorageMB     = getTotalStorageMB(),
            kioskModeEnabled   = prefs.kioskModeEnabled,
            cameraDisabled     = prefs.cameraDisabled
        )
    }

    fun getBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error obteniendo batería: ${e.message}")
            -1
        }
    }

    fun getAvailableStorageMB(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) { -1L }
    }

    fun getTotalStorageMB(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (e: Exception) { -1L }
    }

    fun getLocalIpAddress(): String? {
        return try {
            val cm      = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val props   = cm.getLinkProperties(network) ?: return null
            props.linkAddresses
                .mapNotNull { it.address.hostAddress }
                .firstOrNull { !it.startsWith("127.") && !it.contains(":") }
        } catch (e: Exception) { null }
    }

    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}