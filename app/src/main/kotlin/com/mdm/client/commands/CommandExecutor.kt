// app/src/main/kotlin/com/mdm/client/commands/CommandExecutor.kt
package com.mdm.client.commands

import android.content.Context
import android.content.Intent
import com.google.gson.JsonParser
import com.mdm.client.commands.handlers.*
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.data.models.CommandType
import com.mdm.client.device.DeviceOwnerChecker
import com.mdm.client.service.MdmPollingService

class CommandExecutor(private val context: Context) {

    private val TAG = "CommandExecutor"

    private val validator = CommandValidator()
    private val checker = DeviceOwnerChecker(context)
    private val lockHandler = LockDeviceHandler(context)
    private val cameraHandler = CameraHandler(context)
    private val kioskHandler = KioskModeHandler(context)
    private val infoHandler = DeviceInfoHandler(context)
    private val appHandler = AppManagementHandler(context)
    private val networkHandler = NetworkHandler(context)
    private val settingsHandler = SystemSettingsHandler(context)
    private val wipeHandler = WipeHandler(context)
    private val rebootHandler = RebootHandler(context)
    private val messageHandler = MessageHandler(context)
    private val wakeScreenHandler = WakeScreenHandler(context)
    // Agregar nuevos handlers:
    private val screenshotHandler = ScreenshotHandler(context)
    private val networkInfoHandler = NetworkInfoHandler(context)
    private val appUsageHandler = AppUsageHandler(context)
    private val ringHandler = RingDeviceHandler(context)
    private val pushConfigHandler = PushConfigHandler(context)
    private val batteryDetailHandler = BatteryDetailHandler(context)
    private val deviceLogsHandler = DeviceLogsHandler(context)
    private val locationTrackingHandler = LocationTrackingHandler.getInstance(context)
    private val passwordPolicyHandler = PasswordPolicyHandler(context)
	private val screenStreamHandler = ScreenStreamHandler(context)

    fun execute(commandType: String, parametersJson: String?): ExecutionResult {
        MdmLog.i(TAG, ">> $commandType | params=${parametersJson?.take(80) ?: "null"}")

        val validation = validator.validate(commandType, parametersJson)
        if (validation.isFailure) return ExecutionResult.failure(validation.getErrorOrNull()!!)

        val noOwnerRequired =
                setOf(CommandType.GET_DEVICE_INFO, CommandType.LIST_APPS, CommandType.SEND_MESSAGE)
        if (commandType !in noOwnerRequired && !checker.isDeviceOwner()) {
            return ExecutionResult.failure(
                    "Device Owner requerido para $commandType. " +
                            "Ejecuta: adb shell dpm set-device-owner " +
                            "${context.packageName}/.receiver.DeviceOwnerReceiver"
            )
        }

        // Sin lineas en blanco dentro del when: evita bug del parser Kotlin+Gradle en Windows
        val result =
                when (commandType) {
                    CommandType.LOCK_DEVICE -> lockHandler.execute()
                    CommandType.WAKE_SCREEN -> wakeScreenHandler.execute()
                    CommandType.DISABLE_CAMERA -> cameraHandler.execute(disable = true)
                    CommandType.ENABLE_CAMERA -> cameraHandler.execute(disable = false)
                    CommandType.ENABLE_KIOSK_MODE -> kioskHandler.execute(enable = true)
                    CommandType.DISABLE_KIOSK_MODE -> kioskHandler.execute(enable = false)
                    CommandType.GET_DEVICE_INFO -> infoHandler.execute()
                    CommandType.REBOOT_DEVICE -> rebootHandler.execute()
                    CommandType.WIPE_DATA -> wipeHandler.execute(parametersJson)
                    CommandType.SET_SCREEN_TIMEOUT -> setScreenTimeout(parametersJson)
                    CommandType.INSTALL_APP -> appHandler.installApp(parametersJson)
                    CommandType.UNINSTALL_APP -> appHandler.uninstallApp(parametersJson)
                    CommandType.LIST_APPS -> appHandler.listApps()
                    CommandType.CLEAR_APP_DATA -> appHandler.clearAppData(parametersJson)
                    CommandType.ENABLE_WIFI -> networkHandler.setWifiEnabled(true)
                    CommandType.DISABLE_WIFI -> networkHandler.setWifiEnabled(false)
                    CommandType.SET_WIFI_CONFIG -> networkHandler.setWifiConfig(parametersJson)
                    CommandType.ENABLE_BLUETOOTH -> networkHandler.setBluetooth(true)
                    CommandType.DISABLE_BLUETOOTH -> networkHandler.setBluetooth(false)
                    CommandType.SET_VOLUME -> settingsHandler.setVolume(parametersJson)
                    CommandType.SET_BRIGHTNESS -> settingsHandler.setBrightness(parametersJson)
                    CommandType.GET_LOCATION -> LocationHandler(context).execute(parametersJson)
                    CommandType.SEND_MESSAGE -> messageHandler.execute(parametersJson)

                    // ── NUEVOS ──────────────────────────────────────────────────────
                    CommandType.TAKE_SCREENSHOT -> screenshotHandler.execute(parametersJson)
                    CommandType.GET_NETWORK_INFO -> networkInfoHandler.execute()
                    CommandType.GET_APP_USAGE -> appUsageHandler.execute(parametersJson)
                    CommandType.RING_DEVICE -> ringHandler.execute(parametersJson)
                    CommandType.PUSH_CONFIG -> pushConfigHandler.execute(parametersJson)
                    CommandType.GET_BATTERY_DETAIL -> batteryDetailHandler.execute()
                    CommandType.GET_LOGS -> deviceLogsHandler.execute(parametersJson)
                    CommandType.START_LOCATION_TRACK ->
                            locationTrackingHandler.execute(parametersJson)
                    CommandType.STOP_LOCATION_TRACK -> locationTrackingHandler.stopTracking()
                    CommandType.SET_PASSWORD_POLICY -> passwordPolicyHandler.execute(parametersJson)
                    CommandType.START_SCREEN_STREAM -> screenStreamHandler.start(parametersJson)
					CommandType.STOP_SCREEN_STREAM -> screenStreamHandler.stop()
					
					CommandType.GRANT_SCREEN_CAPTURE -> {// Llamar al servicio para lanzar la actividad
					val intent = Intent(context, MdmPollingService::class.java).apply {
					action = "ACTION_GRANT_SCREEN_CAPTURE"
					}
					context.startService(intent)
					ExecutionResult.success("""{"status":"permission_requested"}""")
}
					
                    else -> ExecutionResult.failure("Comando no implementado: $commandType")
                }

        MdmLog.i(TAG, "${if (result.success) "OK" else "FAIL"} $commandType")
        return result
    }

    // Block body: evita ambiguedad del parser con expression body en funciones que pueden throw
    private fun setScreenTimeout(params: String?): ExecutionResult {
        return try {
            val seconds = JsonParser.parseString(params!!).asJsonObject.get("seconds").asInt
            checker.getDpm().setMaximumTimeToLock(checker.adminComponent, seconds * 1000L)
            ExecutionResult.success("""{"screenTimeoutSeconds":$seconds}""")
        } catch (e: Exception) {
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
