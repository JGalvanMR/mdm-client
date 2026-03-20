package com.mdm.client.commands.handlers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class SystemSettingsHandler(private val context: Context) {

    private val TAG = "SystemSettingsHandler"
    private val gson = Gson()
    private val checker = DeviceOwnerChecker(context)

    // ── SET_VOLUME ─────────────────────────────────────────────────────────────
    fun setVolume(parametersJson: String?): ExecutionResult {
        return try {
            val params = gson.fromJson(parametersJson, Map::class.java)
            val level =
                    (params["level"] as? Double)?.toInt()
                            ?: return ExecutionResult.failure("Falta parámetro 'level' (0-100).")

            if (level !in 0..100) return ExecutionResult.failure("Nivel debe estar entre 0 y 100.")

            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (level * maxVol / 100.0).toInt()

            audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    target,
                    0 // Sin flag de UI
            )

            // También ajustar el volumen de ring si es 0 (silencio)
            if (level == 0) {
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            } else {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }

            MdmLog.i(TAG, "Volumen ajustado a $level% ($target/$maxVol)")
            ExecutionResult.success(
                    """{"volumePercent":$level,"rawValue":$target,"maxValue":$maxVol}"""
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error ajustando volumen: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    // ── SET_BRIGHTNESS ────────────────────────────────────────────────────────
    fun setBrightness(parametersJson: String?): ExecutionResult {
        return try {
            val params = gson.fromJson(parametersJson, Map::class.java)
            val level =
                    (params["level"] as? Double)?.toInt()
                            ?: return ExecutionResult.failure("Falta parámetro 'level' (0-255).")

            if (level !in 0..255) return ExecutionResult.failure("Nivel debe estar entre 0 y 255.")

            // Cambiar a modo manual
            Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            // Aplicar brillo
            Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    level
            )

            MdmLog.i(TAG, "Brillo ajustado a $level/255")
            ExecutionResult.success("""{"brightness":$level}""")
        } catch (e: SecurityException) {
            ExecutionResult.failure(
                    "Se requiere permiso WRITE_SETTINGS. " +
                            "Actívalo manualmente en Ajustes > Apps > MDM Agent > Permisos especiales > Modificar ajustes del sistema."
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error ajustando brillo: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    // ── GET_LOCATION ──────────────────────────────────────────────────────────
    fun getLocation(): ExecutionResult {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Verificar si el GPS está habilitado
            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!gpsEnabled && !netEnabled) {
                return ExecutionResult.failure("GPS y red de localización deshabilitados.")
            }

            // Obtener última ubicación conocida (más rápido, no requiere esperar fix)
            @Suppress("MissingPermission")
            val location: Location? =
                    if (gpsEnabled) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    else lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val age = (System.currentTimeMillis() - location.time) / 1000L
                val json =
                        gson.toJson(
                                mapOf(
                                        "latitude" to location.latitude,
                                        "longitude" to location.longitude,
                                        "accuracy" to location.accuracy,
                                        "provider" to location.provider,
                                        "ageSeconds" to age,
                                        "timestamp" to location.time
                                )
                        )
                MdmLog.i(TAG, "Ubicación obtenida: ${location.latitude},${location.longitude}")
                ExecutionResult.success(json)
            } else {
                // No hay ubicación cached — retornar estado
                ExecutionResult.success(
                        """{"error":"Sin ubicación cacheada","gpsEnabled":$gpsEnabled,"networkEnabled":$netEnabled}"""
                )
            }
        } catch (e: SecurityException) {
            ExecutionResult.failure("Permiso ACCESS_FINE_LOCATION denegado. Otórgalo manualmente.")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error obteniendo ubicación: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    // ── SEND_MESSAGE ──────────────────────────────────────────────────────────
    // Muestra una notificación en el dispositivo desde el admin
    fun sendMessage(parametersJson: String?): ExecutionResult {
        return try {
            val params = gson.fromJson(parametersJson, Map::class.java)
            val title = params["title"] as? String ?: "Mensaje del administrador"
            val body =
                    params["body"] as? String
                            ?: return ExecutionResult.failure("Falta parámetro 'body'.")

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                    NotificationChannel(
                            "mdm_messages",
                            "Mensajes MDM",
                            NotificationManager.IMPORTANCE_HIGH
                    )
            )

            val notif =
                    NotificationCompat.Builder(context, "mdm_messages")
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()

            nm.notify(System.currentTimeMillis().toInt(), notif)
            MdmLog.i(TAG, "Mensaje enviado al dispositivo: $title")
            ExecutionResult.success("""{"delivered":true,"title":"${title.replace("\"","'")}"}""")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error enviando mensaje: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
