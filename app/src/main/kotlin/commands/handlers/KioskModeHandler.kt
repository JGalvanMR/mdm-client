package com.mdm.client.commands.handlers

import android.content.Context
import com.mdm.client.core.Constants
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.core.sendLocalBroadcast
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.device.DeviceOwnerChecker

class KioskModeHandler(private val context: Context) {

    private val TAG     = "KioskModeHandler"
    private val checker = DeviceOwnerChecker(context)
    private val prefs   = DevicePrefs(context)

    fun execute(enable: Boolean): ExecutionResult {
        if (!checker.isDeviceOwner()) {
            return ExecutionResult.failure("La app no es Device Owner. No se puede activar kiosk mode.")
        }

        return try {
            if (enable) {
                enableKiosk()
            } else {
                disableKiosk()
            }
        } catch (e: SecurityException) {
            MdmLog.e(TAG, "SecurityException en kiosk mode: ${e.message}")
            ExecutionResult.failure("Permiso denegado para kiosk mode: ${e.message}")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error en kiosk mode: ${e.message}", e)
            ExecutionResult.failure("Error cambiando kiosk mode: ${e.message}")
        }
    }

    private fun enableKiosk(): ExecutionResult {
        // Autorizar este paquete para lock task
        checker.getDpm().setLockTaskPackages(
            checker.adminComponent,
            arrayOf(context.packageName)
        )
        prefs.kioskModeEnabled = true

        // Enviar broadcast a MainActivity para que llame startLockTask()
        // Solo MainActivity puede llamar startLockTask() (requiere Activity)
        context.sendLocalBroadcast(Constants.ACTION_START_KIOSK)

        MdmLog.i(TAG, "Kiosk mode HABILITADO. Paquete autorizado: ${context.packageName}")
        return ExecutionResult.success("""{"kioskModeEnabled":true}""")
    }

    private fun disableKiosk(): ExecutionResult {
        // Vaciar la lista de lock task (permite salir)
        checker.getDpm().setLockTaskPackages(checker.adminComponent, emptyArray())
        prefs.kioskModeEnabled = false

        // Broadcast para que MainActivity llame stopLockTask()
        context.sendLocalBroadcast(Constants.ACTION_STOP_KIOSK)

        MdmLog.i(TAG, "Kiosk mode DESHABILITADO.")
        return ExecutionResult.success("""{"kioskModeEnabled":false}""")
    }
}