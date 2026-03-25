package com.mdm.client.commands.handlers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mdm.client.core.ExecutionResult
import com.mdm.client.service.MdmAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotHandler(private val context: Context) {

    private val TAG = "ScreenshotHandler"

    fun execute(parametersJson: String?): ExecutionResult {
        // Verificar servicio
        if (!MdmAccessibilityService.isReady()) {
            Log.e(TAG, "Servicio no listo. Instance=${MdmAccessibilityService.getInstance()}")
            return ExecutionResult.failure(
                "Accessibility Service no activo. Ve a Ajustes > Accesibilidad y activa MDM Screenshot."
            )
        }

        val service = MdmAccessibilityService.getInstance()!!
        val latch = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        var result: ExecutionResult = ExecutionResult.failure("Timeout")

        // Timeout de seguridad
        val timeoutThread = Thread {
            try {
                Thread.sleep(10000) // 10 segundos max
                if (!completed.get()) {
                    Log.w(TAG, "Timeout forzado")
                    result = ExecutionResult.failure("""{"error":"Timeout esperando screenshot (10s)"}""")
                    latch.countDown()
                }
            } catch (e: InterruptedException) {
                // Normal si completa antes
            }
        }.apply { isDaemon = true }

        timeoutThread.start()

        Handler(Looper.getMainLooper()).post {
            try {
                service.takeScreenshot(-1) { success, data ->
                    if (completed.getAndSet(true)) return@takeScreenshot // Evitar doble llamada
                    
                    result = if (success) {
                        Log.i(TAG, "Screenshot exitoso: ${data.length} chars")
                        ExecutionResult.success(data)
                    } else {
                        Log.e(TAG, "Error del servicio: $data")
                        ExecutionResult.failure(data)
                    }
                    latch.countDown()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción: ${e.message}")
                if (!completed.getAndSet(true)) {
                    result = ExecutionResult.failure("""{"error":"${e.message}"}""")
                    latch.countDown()
                }
            }
        }

        latch.await(12, TimeUnit.SECONDS)
        return result
    }
}