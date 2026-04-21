package com.mdm.client.commands.handlers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class BatteryDetailHandler(private val context: Context) {
    private val TAG = "BatteryDetailHandler"
    private val gson = Gson()

    fun execute(): ExecutionResult {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            val percent = if (scale > 0) level * 100 / scale else -1
            val isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
            val data =
                    mapOf(
                            "percent" to percent,
                            "temperature" to temp / 10.0,
                            "voltage" to voltage,
                            "isCharging" to isCharging,
                            "plugged" to plugged,
                            "status" to status,
                            "health" to health
                    )
            MdmLog.i(TAG, "Battery detail: $percent% temp=${temp/10.0}°C")
            ExecutionResult.success(gson.toJson(data))
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error: ${e.message}", e)
            ExecutionResult.failure("Error obteniendo detalle de batería: ${e.message}")
        }
    }
}
