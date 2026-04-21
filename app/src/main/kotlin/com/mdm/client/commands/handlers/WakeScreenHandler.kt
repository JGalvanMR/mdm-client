// app/src/main/kotlin/com/mdm/client/commands/handlers/WakeScreenHandler.kt
package com.mdm.client.commands.handlers

import android.content.Context
import android.os.PowerManager
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class WakeScreenHandler(private val context: Context) {

    private val TAG = "WakeScreenHandler"

    fun execute(): ExecutionResult {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP enciende la pantalla
            // aunque esté apagada — sin necesidad de interacción física
            @Suppress("DEPRECATION")
            val wl =
                    pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "MdmClient::WakeScreen"
                    )
            wl.acquire(3_000L) // mantener 3s y soltar
            wl.release()

            MdmLog.i(TAG, "Pantalla encendida remotamente.")
            ExecutionResult.success(
                    """{"wakeScreen":true,"timestamp":${System.currentTimeMillis()}}"""
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error encendiendo pantalla: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
