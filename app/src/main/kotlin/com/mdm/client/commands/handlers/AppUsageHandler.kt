// app/src/main/kotlin/com/mdm/client/commands/handlers/AppUsageHandler.kt
package com.mdm.client.commands.handlers

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class AppUsageHandler(private val context: Context) {

    private val TAG = "AppUsageHandler"
    private val gson = Gson()

    /**
     * Requiere permiso PACKAGE_USAGE_STATS (no es normal, debe concederse manualmente o via
     * DevicePolicyManager.setPermittedAccessibilityServices en Device Owner). params: {"hours": 24}
     * → reporta las últimas N horas
     */
    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val hours =
                    try {
                        gson.fromJson(parametersJson, Map::class.java)
                                ?.get("hours")
                                ?.toString()
                                ?.toDouble()
                                ?.toInt()
                                ?: 24
                    } catch (e: Exception) {
                        24
                    }

            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (hours * 60 * 60 * 1000L)

            // Obtener stats de uso por app
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

            if (stats.isNullOrEmpty()) {
                return ExecutionResult.success(
                        """{"error":"Sin datos. Activa el permiso Uso de apps en Ajustes.","apps":[],"hours":$hours}"""
                )
            }

            // Filtrar solo apps del usuario con tiempo > 0
            val appUsage =
                    stats
                            .filter { it.totalTimeInForeground > 0 }
                            .map { stat ->
                                val appName =
                                        try {
                                            pm.getApplicationLabel(
                                                            pm.getApplicationInfo(
                                                                    stat.packageName,
                                                                    0
                                                            )
                                                    )
                                                    .toString()
                                        } catch (e: Exception) {
                                            stat.packageName
                                        }

                                mapOf(
                                        "packageName" to stat.packageName,
                                        "appName" to appName,
                                        "totalTimeMin" to (stat.totalTimeInForeground / 60_000),
                                        "lastUsed" to stat.lastTimeUsed,
                                        "launchCount" to stat.lastTimeStamp
                                )
                            }
                            .sortedByDescending { it["totalTimeMin"] as Long }
                            .take(20)

            // Obtener eventos de los últimos N horas (abre/cierra)
            val events = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var launches = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) launches++
            }

            val result =
                    mapOf(
                            "hours" to hours,
                            "totalLaunches" to launches,
                            "apps" to appUsage,
                            "generatedAt" to System.currentTimeMillis()
                    )

            MdmLog.i(TAG, "App usage recopilado: ${appUsage.size} apps en $hours horas")
            ExecutionResult.success(gson.toJson(result))
        } catch (e: SecurityException) {
            ExecutionResult.failure(
                    "Permiso denegado. Activa: Ajustes → Apps → Acceso especial → Uso de apps"
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error obteniendo uso de apps: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}