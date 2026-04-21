package com.mdm.client.commands.handlers

import android.content.Context
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class UnlockDeviceHandler(private val context: Context) {

    private val TAG = "UnlockDeviceHandler"
    private val checker = DeviceOwnerChecker(context)

    fun execute(): ExecutionResult {
        if (!checker.isDeviceOwner()) {
            return ExecutionResult.failure(
                    "La app no es Device Owner. No se puede resetear el password."
            )
        }

        return try {
            // Esto elimina el PIN/Patrón actual del dispositivo
            // Nota: En Android 8.0+ se recomienda usar resetPasswordWithToken
            // pero para una implementación básica:
            checker.getDpm().resetPassword("", 0)

            MdmLog.i(TAG, "Bloqueo de pantalla removido exitosamente.")
            ExecutionResult.success(
                    """{"unlocked":true,"passwordRemoved":true,"timestamp":"${System.currentTimeMillis()}"}"""
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error al quitar bloqueo: ${e.message}", e)
            ExecutionResult.failure("Error al quitar bloqueo: ${e.message}")
        }
    }
}
