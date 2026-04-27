package com.mdm.client.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.mdm.client.core.MdmLog
import java.io.ByteArrayOutputStream
import android.util.Log
import com.mdm.client.commands.handlers.InputInjectionHandler

class MdmAccessibilityService : AccessibilityService() {

    private val TAG = "MdmAccessibilityService"

    companion object {
        @Volatile private var instance: MdmAccessibilityService? = null
        private var isServiceReady = false

        fun getInstance(): MdmAccessibilityService? = instance
        fun isReady(): Boolean = isServiceReady && instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceReady = true
		InputInjectionHandler.instance = this
        MdmLog.i(TAG, "Servicio de accesibilidad conectado.")
    }

    override fun onDestroy() {
        isServiceReady = false
        instance = null
		InputInjectionHandler.instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * Toma screenshot y devuelve Base64 directamente
     * @param displayId -1 para pantalla por defecto, o Display.DEFAULT_DISPLAY (0)
     * @param callback (success: Boolean, jsonResult: String)
     */
    fun takeScreenshot(displayId: Int, callback: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { // API 30+
            callback(false, """{"error":"Requiere Android 11+ (API 30)"}""")
            return
        }

        // Usar Display.DEFAULT_DISPLAY (valor 0) si se pasa -1
        val targetDisplayId = if (displayId == -1) Display.DEFAULT_DISPLAY else displayId

        takeScreenshot(
                targetDisplayId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val bitmap =
                                    Bitmap.wrapHardwareBuffer(
                                                    result.hardwareBuffer,
                                                    result.colorSpace
                                            )
                                            ?.copy(Bitmap.Config.ARGB_8888, false)

                            result.hardwareBuffer.close()

                            if (bitmap == null) {
                                callback(false, """{"error":"No se pudo procesar el bitmap"}""")
                                return
                            }

                            // Comprimir y convertir a Base64
                            val outputStream = ByteArrayOutputStream()
                            val compressed =
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)

                            if (!compressed) {
                                bitmap.recycle()
                                callback(false, """{"error":"Error comprimiendo imagen"}""")
                                return
                            }

                            val byteArray = outputStream.toByteArray()
                            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                            // Liberar memoria
                            bitmap.recycle()
                            outputStream.close()

                            // Respuesta con metadata
                            val sizeKB = byteArray.size / 1024
                            val jsonResult =
                                    """{"screenshot":"$base64","sizeKB":$sizeKB,"format":"jpeg"}"""
                            callback(true, jsonResult)
                        } catch (e: Exception) {
                            MdmLog.e(TAG, "Error procesando screenshot: ${e.message}")
                            callback(false, """{"error":"${e.message}"}""")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        // Valores literales: ERROR_SCREENSHOT_NO_PERMISSIONS=1,
                        // ERROR_SCREENSHOT_SECURITY_POLICY=2
                        val errorMsg =
                                when (errorCode) {
                                    1 -> "Sin permisos de screenshot"
                                    2 -> "Bloqueado por política de seguridad"
                                    else -> "Error código: $errorCode"
                                }
                        callback(false, """{"error":"$errorMsg","code":$errorCode}""")
                    }
                }
        )
    }
}