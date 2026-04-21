// commands/handlers/WipeHandler.kt
package com.mdm.client.commands.handlers

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class WipeHandler(private val context: Context) {

    private val TAG = "WipeHandler"
    private val checker = DeviceOwnerChecker(context)

    fun execute(parametersJson: String?): ExecutionResult {
        if (!checker.isDeviceOwner())
                return ExecutionResult.failure("Device Owner requerido para WIPE_DATA.")

        // La validación de {"confirm":true} ya ocurre en CommandValidator / SendCommandValidator
        // Aquí solo ejecutamos
        return try {
            // FLAG 0 = wipe normal, FLAG_WIPE_EXTERNAL_STORAGE = también SD
            checker.getDpm().wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
            MdmLog.i(TAG, "WIPE_DATA ejecutado. El dispositivo se está restableciendo.")
            ExecutionResult.success(
                    """{"wipeInitiated":true,"timestamp":${System.currentTimeMillis()}}"""
            )
        } catch (e: SecurityException) {
            MdmLog.e(TAG, "SecurityException en wipe: ${e.message}")
            ExecutionResult.failure("Permiso denegado: ${e.message}")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error en wipe: ${e.message}", e)
            ExecutionResult.failure("Error ejecutando wipe: ${e.message}")
        }
    }
}
