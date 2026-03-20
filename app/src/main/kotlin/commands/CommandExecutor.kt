package com.mdm.client.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.commands.handlers.*
import com.mdm.client.data.models.CommandType
import com.mdm.client.device.DeviceOwnerChecker   

class CommandExecutor(private val context: Context) {

    private val TAG       = "CommandExecutor"
    private val validator = CommandValidator()
    private val checker   = DeviceOwnerChecker(context)

    // Handlers
    private val lockHandler       = LockDeviceHandler(context)
    private val cameraHandler     = CameraHandler(context)
    private val kioskHandler      = KioskModeHandler(context)
    private val deviceInfoHandler = DeviceInfoHandler(context)

    /**
     * Punto de entrada principal. Valida y despacha el comando.
     * Siempre retorna ExecutionResult (nunca lanza excepción).
     */
    fun execute(commandType: String, parametersJson: String?): ExecutionResult {
        MdmLog.i(TAG, "▶ Ejecutando: $commandType | params=${parametersJson ?: "null"}")

        // Paso 1: Validar
        val validation = validator.validate(commandType, parametersJson)
        if (validation.isFailure) {
            val err = validation.getErrorOrNull()!!
            MdmLog.w(TAG, "Validación fallida: $err")
            return ExecutionResult.failure(err)
        }

        // Paso 2: Verificar Device Owner para comandos que lo requieren
        val requiresDO = commandType !in setOf(CommandType.GET_DEVICE_INFO)
        if (requiresDO && !checker.isDeviceOwner()) {
            val err = "Device Owner requerido para $commandType. " +
                      "Ejecuta: adb shell dpm set-device-owner ${context.packageName}/.receiver.DeviceOwnerReceiver"
            MdmLog.e(TAG, err)
            return ExecutionResult.failure(err)
        }

        // Paso 3: Despachar
        val result = when (commandType) {
            CommandType.LOCK_DEVICE        -> lockHandler.execute()
            CommandType.DISABLE_CAMERA     -> cameraHandler.execute(disable = true)
            CommandType.ENABLE_CAMERA      -> cameraHandler.execute(disable = false)
            CommandType.ENABLE_KIOSK_MODE  -> kioskHandler.execute(enable = true)
            CommandType.DISABLE_KIOSK_MODE -> kioskHandler.execute(enable = false)
            CommandType.GET_DEVICE_INFO    -> deviceInfoHandler.execute()
            CommandType.REBOOT_DEVICE      -> rebootDevice()
            CommandType.WIPE_DATA          -> wipeData()
            CommandType.SET_SCREEN_TIMEOUT -> setScreenTimeout(parametersJson)
            else -> ExecutionResult.failure("Comando no implementado: $commandType")
        }

        val status = if (result.success) "✓ OK" else "✗ FAIL"
        MdmLog.i(TAG, "$status $commandType → ${result.resultJson ?: result.errorMessage}")
        return result
    }

    // ── Comandos adicionales implementados directamente ───────────────────────

    private fun rebootDevice(): ExecutionResult {
        return try {
            checker.getDpm().reboot(checker.adminComponent)
            MdmLog.i(TAG, "Reboot solicitado.")
            ExecutionResult.success("""{"rebootRequested":true}""")
        } catch (e: Exception) {
            ExecutionResult.failure("Error al reiniciar: ${e.message}")
        }
    }

    private fun wipeData(): ExecutionResult {
        // WIPE_DATA ya fue validado (requiere "confirm": true en parameters)
        return try {
            checker.getDpm().wipeData(0)
            MdmLog.i(TAG, "¡WIPE DATA solicitado! El dispositivo hará factory reset.")
            ExecutionResult.success("""{"wipeRequested":true}""")
        } catch (e: Exception) {
            ExecutionResult.failure("Error al hacer wipe: ${e.message}")
        }
    }

    private fun setScreenTimeout(parametersJson: String?): ExecutionResult {
        return try {
            val seconds = JsonParser.parseString(parametersJson!!)
                .asJsonObject.get("seconds").asInt
            val ms      = seconds * 1000
            checker.getDpm().setMaximumTimeToLock(checker.adminComponent, ms.toLong())
            MdmLog.i(TAG, "Screen timeout configurado a $seconds segundos.")
            ExecutionResult.success("""{"screenTimeoutSeconds":$seconds}""")
        } catch (e: Exception) {
            ExecutionResult.failure("Error configurando screen timeout: ${e.message}")
        }
    }
}