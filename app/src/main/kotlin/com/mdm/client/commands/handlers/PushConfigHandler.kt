package com.mdm.client.commands.handlers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.data.prefs.DevicePrefs

class PushConfigHandler(private val context: Context) {
    private val TAG = "PushConfigHandler"
    private val gson = Gson()
    private val prefs = DevicePrefs(context)

    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val params =
                    parametersJson?.let { JsonParser.parseString(it).asJsonObject }
                            ?: return ExecutionResult.failure("Falta parámetro de configuración.")

            val applied = mutableListOf<String>()

            if (params.has("kioskMode")) {
                prefs.kioskModeEnabled = params.get("kioskMode").asBoolean
                applied.add("kioskMode")
            }
            if (params.has("cameraDisabled")) {
                prefs.cameraDisabled = params.get("cameraDisabled").asBoolean
                applied.add("cameraDisabled")
            }

            MdmLog.i(TAG, "Config aplicada: $applied")
            ExecutionResult.success(
                    gson.toJson(mapOf("applied" to applied, "count" to applied.size))
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error: ${e.message}", e)
            ExecutionResult.failure("Error aplicando config: ${e.message}")
        }
    }
}
