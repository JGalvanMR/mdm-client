package com.mdm.client.service

import android.content.Context
import android.os.Build
import com.mdm.client.BuildConfig
import com.mdm.client.core.MdmLog
import com.mdm.client.core.MdmResult
import com.mdm.client.data.models.RegisterDeviceRequest
import com.mdm.client.data.network.ApiClient
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.device.DeviceInfoCollector
import kotlinx.coroutines.delay

class RegistrationManager(
    private val context: Context,
    private val apiClient: ApiClient,
    private val prefs: DevicePrefs
) {
    private val TAG       = "RegistrationManager"
    private val collector = DeviceInfoCollector(context)

    /**
     * Intenta registrar el dispositivo si no está registrado.
     * Implementa backoff exponencial: 5s → 10s → 20s → 40s → 80s (máx 5 intentos).
     * Retorna true si al terminar el dispositivo está registrado.
     */
    suspend fun ensureRegistered(): Boolean {
        if (prefs.isRegistered && !prefs.deviceToken.isNullOrBlank()) {
            return true
        }

        val maxAttempts = BuildConfig.MAX_RETRY_ATTEMPTS
        var attempt     = 0

        while (attempt < maxAttempts) {
            attempt++
            MdmLog.i(TAG, "Intento de registro $attempt/$maxAttempts...")

            val result = tryRegister()

            when {
                result.isSuccess -> {
                    prefs.registrationRetryCount = 0
                    MdmLog.i(TAG, "✓ Registro exitoso.")
                    return true
                }
                else -> {
                    val err = result.getErrorOrNull()
                    MdmLog.w(TAG, "✗ Registro fallido (intento $attempt): $err")
                    prefs.registrationRetryCount = attempt

                    if (attempt < maxAttempts) {
                        // Backoff exponencial: 5s, 10s, 20s, 40s...
                        val delayMs = BuildConfig.RETRY_DELAY_MS * (1L shl (attempt - 1))
                        MdmLog.i(TAG, "Reintentando en ${delayMs / 1000}s...")
                        delay(delayMs)
                    }
                }
            }
        }

        MdmLog.e(TAG, "Registro fallido después de $maxAttempts intentos.")
        return false
    }

    private fun tryRegister(): MdmResult<Unit> {
        val deviceId = prefs.getOrCreateDeviceId(context)

        val request = RegisterDeviceRequest(
            deviceId       = deviceId,
            deviceName     = collector.getDeviceName(),
            model          = Build.MODEL,
            manufacturer   = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel       = Build.VERSION.SDK_INT
        )

        return when (val response = apiClient.registerDevice(request)) {
            is MdmResult.Success -> {
                val data = response.data
                if (data.success && data.token.length >= 32) {
                    prefs.deviceToken  = data.token
                    prefs.isRegistered = true
                    MdmResult.Success(Unit)
                } else {
                    MdmResult.Failure("Respuesta de registro inválida: token vacío o muy corto.")
                }
            }
            is MdmResult.Failure -> MdmResult.Failure(response.errorMessage, response.cause)
        }
    }
}