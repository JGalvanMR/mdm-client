package com.mdm.client.commands.handlers

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class PasswordPolicyHandler(private val context: Context) {

    private val TAG = "PasswordPolicyHandler"
    private val checker = DeviceOwnerChecker(context)
    private val gson = Gson()

    fun execute(parametersJson: String?): ExecutionResult {
        if (!checker.isDeviceOwner()) {
            return ExecutionResult.failure("Device Owner requerido para políticas de contraseña.")
        }

        return try {
            val params =
                    parametersJson?.let { JsonParser.parseString(it).asJsonObject }
                            ?: return ExecutionResult.failure("Faltan parámetros.")

            val dpm = checker.getDpm()
            val admin = checker.adminComponent

            // Calidad de contraseña
            val qualityStr = params.get("quality")?.asString ?: "SOMETHING"
            val quality =
                    when (qualityStr.uppercase()) {
                        "NONE" -> DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
                        "SOMETHING" -> DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
                        "NUMERIC" -> DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
                        "ALPHABETIC" -> DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC
                        "ALPHANUMERIC" -> DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
                        "COMPLEX" -> DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                        else -> DevicePolicyManager.PASSWORD_QUALITY_SOMETHING
                    }

            dpm.setPasswordQuality(admin, quality)

            // Longitud mínima
            params.get("minLength")?.asInt?.let { length ->
                dpm.setPasswordMinimumLength(admin, length)
            }

            // Expiración (días)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.get("expirationDays")?.asInt?.let { days ->
                    dpm.setPasswordExpirationTimeout(admin, days * 24 * 60 * 60 * 1000L)
                }
            }

            // Historial (no repetir últimas N)
            params.get("historySize")?.asInt?.let { size ->
                dpm.setPasswordHistoryLength(admin, size)
            }

            // Intentos fallidos antes de wipe
            params.get("maxFailedAttempts")?.asInt?.let { attempts ->
                dpm.setMaximumFailedPasswordsForWipe(admin, attempts)
            }

            // Tiempo de bloqueo tras inactividad
            params.get("maxTimeToLockSeconds")?.asInt?.let { seconds ->
                dpm.setMaximumTimeToLock(admin, seconds * 1000L)
            }

            MdmLog.i(TAG, "Política aplicada: quality=$qualityStr")

            ExecutionResult.success(
                    """{
                "quality": "$qualityStr",
                "applied": true
            }"""
            )
        } catch (e: SecurityException) {
            ExecutionResult.failure("Permiso denegado: ${e.message}")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error aplicando política: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
