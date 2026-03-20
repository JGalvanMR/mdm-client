package com.mdm.client.commands.handlers

import android.content.Context
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.device.DeviceOwnerChecker

class CameraHandler(private val context: Context) {

    private val TAG     = "CameraHandler"
    private val checker = DeviceOwnerChecker(context)
    private val prefs   = DevicePrefs(context)

    fun execute(disable: Boolean): ExecutionResult {
        if (!checker.isDeviceOwner()) {
            return ExecutionResult.failure("La app no es Device Owner. No se puede controlar la cámara.")
        }

        return try {
            checker.getDpm().setCameraDisabled(checker.adminComponent, disable)
            prefs.cameraDisabled = disable

            val action = if (disable) "DESHABILITADA" else "HABILITADA"
            MdmLog.i(TAG, "Cámara $action.")

            ExecutionResult.success("""{"cameraDisabled":$disable,"timestamp":"${System.currentTimeMillis()}"}""")
        } catch (e: SecurityException) {
            MdmLog.e(TAG, "SecurityException cambiando estado de cámara: ${e.message}")
            ExecutionResult.failure("Permiso denegado para controlar la cámara: ${e.message}")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error cambiando estado de cámara: ${e.message}", e)
            ExecutionResult.failure("Error controlando cámara: ${e.message}")
        }
    }
}