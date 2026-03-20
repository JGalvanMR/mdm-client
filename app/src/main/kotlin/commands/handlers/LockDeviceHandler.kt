package com.mdm.client.commands.handlers

import android.content.Context
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class LockDeviceHandler(private val context: Context) {

    private val TAG     = "LockDeviceHandler"
    private val checker = DeviceOwnerChecker(context)

    fun execute(): ExecutionResult {
        if (!checker.isDeviceOwner()) {
            return ExecutionResult.failure("La app no es Device Owner. No se puede bloquear la pantalla.")
        }

        return try {
            checker.getDpm().lockNow()
            MdmLog.i(TAG, "Dispositivo bloqueado exitosamente.")
            ExecutionResult.success("""{"locked":true,"timestamp":"${System.currentTimeMillis()}"}""")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error al bloquear: ${e.message}", e)
            ExecutionResult.failure("Error al bloquear pantalla: ${e.message}")
        }
    }
}