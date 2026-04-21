// commands/handlers/RebootHandler.kt
package com.mdm.client.commands.handlers

import android.content.Context
import android.os.Build
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class RebootHandler(private val context: Context) {

    private val TAG = "RebootHandler"
    private val checker = DeviceOwnerChecker(context)

    fun execute(): ExecutionResult {
        if (!checker.isDeviceOwner())
                return ExecutionResult.failure("Device Owner requerido para REBOOT_DEVICE.")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                return ExecutionResult.failure("REBOOT_DEVICE requiere Android 7.0+ (API 24).")

        return try {
            checker.getDpm().reboot(checker.adminComponent)
            MdmLog.i(TAG, "Reboot solicitado exitosamente.")
            ExecutionResult.success(
                    """{"rebootRequested":true,"timestamp":${System.currentTimeMillis()}}"""
            )
        } catch (e: SecurityException) {
            MdmLog.e(TAG, "SecurityException en reboot: ${e.message}")
            ExecutionResult.failure("Permiso denegado: ${e.message}")
        } catch (e: IllegalStateException) {
            MdmLog.e(TAG, "IllegalStateException en reboot: ${e.message}")
            ExecutionResult.failure("No se puede reiniciar ahora: ${e.message}")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error en reboot: ${e.message}", e)
            ExecutionResult.failure("Error ejecutando reboot: ${e.message}")
        }
    }
}
