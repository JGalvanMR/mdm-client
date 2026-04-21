package com.mdm.client.commands.handlers

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RingDeviceHandler(private val context: Context) {
    private val TAG = "RingDeviceHandler"
    private val gson = Gson()

    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val durationMs =
                    try {
                        val params = parametersJson?.let { JsonParser.parseString(it).asJsonObject }
                        (params?.get("seconds")?.asInt ?: 10) * 1000L
                    } catch (e: Exception) {
                        10_000L
                    }

            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ring = RingtoneManager.getRingtone(context, uri)

            // Forzar volumen al máximo para que se escuche
            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            ring.play()

            // Detener después del tiempo configurado
            CoroutineScope(Dispatchers.IO).launch {
                delay(durationMs)
                ring.stop()
                MdmLog.i(TAG, "Ring detenido tras ${durationMs/1000}s")
            }

            MdmLog.i(TAG, "Ring iniciado por ${durationMs/1000}s")
            ExecutionResult.success("""{"ringing":true,"durationSeconds":${durationMs/1000}}""")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error: ${e.message}", e)
            ExecutionResult.failure("Error haciendo ring: ${e.message}")
        }
    }
}
