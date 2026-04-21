package com.mdm.client.commands.handlers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class DeviceLogsHandler(private val context: Context) {
    private val TAG = "DeviceLogsHandler"
    private val gson = Gson()

    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val lines =
                    try {
                        val params = parametersJson?.let { JsonParser.parseString(it).asJsonObject }
                        val count = params?.get("lines")?.asInt ?: 100
                        count
                    } catch (e: Exception) {
                        100
                    }

            val process =
                    Runtime.getRuntime()
                            .exec(
                                    arrayOf(
                                            "logcat",
                                            "-d",
                                            "-t",
                                            lines.toString(),
                                            "-v",
                                            "time",
                                            "*:W"
                                    )
                            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val result =
                    mapOf(
                            "lines" to output.lines().size,
                            "log" to output.take(50_000) // max 50KB
                    )
            MdmLog.i(TAG, "Logs capturados: ${result["lines"]} líneas")
            ExecutionResult.success(gson.toJson(result))
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error capturando logs: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
