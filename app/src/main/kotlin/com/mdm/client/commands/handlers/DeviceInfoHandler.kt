package com.mdm.client.commands.handlers

import android.content.Context
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceInfoCollector

class DeviceInfoHandler(private val context: Context) {

    private val TAG = "DeviceInfoHandler"
    private val collector = DeviceInfoCollector(context)
    private val gson = Gson()

    fun execute(): ExecutionResult {
        return try {
            val info = collector.collect()
            val json = gson.toJson(info)
            MdmLog.i(TAG, "Device info recopilada: ${json.take(100)}…")
            ExecutionResult.success(json)
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error recopilando device info: ${e.message}", e)
            ExecutionResult.failure("Error obteniendo info del dispositivo: ${e.message}")
        }
    }
}
