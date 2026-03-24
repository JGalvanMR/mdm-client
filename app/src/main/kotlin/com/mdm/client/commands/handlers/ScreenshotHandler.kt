package com.mdm.client.commands.handlers

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.mdm.client.core.ExecutionResult
import com.mdm.client.service.MdmAccessibilityService
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotHandler(private val context: Context) {

    private val TAG = "ScreenshotHandler"

    fun execute(parametersJson: String?): ExecutionResult {
        // Verificar servicio
        if (!MdmAccessibilityService.isReady()) {
            Log.e(TAG, "Servicio no listo. Instance=${MdmAccessibilityService.instance}")
            return ExecutionResult.failure(
                "Accessibility Service no activo o no inicializado. " +
                "Reinicia la app después de activar el servicio."
            )
        }

        val service = MdmAccessibilityService.instance!!
        val latch = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        var result: ExecutionResult = ExecutionResult.failure("Timeout")

        // Timeout de seguridad en thread separado
        Thread {
            Thread.sleep(8000) // 8 segundos max
            if (!completed.get()) {
                Log.w(TAG, "Timeout forzado")
                result = ExecutionResult.failure("Timeout esperando screenshot")
                latch.countDown()
            }
        }.start()

        Handler(Looper.getMainLooper()).post {
            try {
                service.takeScreenshot(-1) { success, data ->
                    if (completed.get()) return@takeScreenshot // Ya terminó
                    completed.set(true)

                    result = if (success) {
                        parseSuccess(data)
                    } else {
                        Log.e(TAG, "Error del servicio: $data")
                        ExecutionResult.failure("Screenshot falló: $data")
                    }
                    latch.countDown()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción llamando takeScreenshot: ${e.message}")
                if (!completed.get()) {
                    completed.set(true)
                    result = ExecutionResult.failure("Error interno: ${e.message}")
                    latch.countDown()
                }
            }
        }

        // Esperar con timeout
        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    private fun parseSuccess(data: String): ExecutionResult {
        return try {
            val json = org.json.JSONObject(data)
            val base64 = json.getString("screenshot")
            val sizeKB = json.getInt("sizeKB")
            
            val resultJson = """{"screenshot":"$base64","sizeKb":$sizeKB}"""
            Log.i(TAG, "Screenshot exitoso: ${sizeKB}KB")
            ExecutionResult.success(resultJson)
        } catch (e: Exception) {
            ExecutionResult.failure("Error parseando resultado: ${e.message}")
        }
    }
}