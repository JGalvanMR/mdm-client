package com.mdm.client.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.mdm.client.core.MdmLog
import com.mdm.client.commands.handlers.InputInjectionHandler
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MdmAccessibilityService : AccessibilityService() {

    private val TAG = "MdmAccessibilityService"
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    companion object {
        @Volatile private var instance: MdmAccessibilityService? = null
        @Volatile private var isServiceReady = false

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
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * Toma screenshot y devuelve Base64.
     * @param displayId -1 para la pantalla por defecto
     * @param callback (success: Boolean, jsonResult: String)
     */
    fun takeScreenshot(displayId: Int, callback: (Boolean, String) -> Unit) {
        if (!isReady) {
            callback(false, """{"error":"Servicio no disponible"}""")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(false, """{"error":"Requiere Android 11+ (API 30)"}""")
            return
        }

        val targetDisplayId = if (displayId == -1) Display.DEFAULT_DISPLAY else displayId

        takeScreenshot(
            targetDisplayId,
            backgroundExecutor,            // ← se procesa en background
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)

                        result.hardwareBuffer.close()

                        if (bitmap == null) {
                            callback(false, """{"error":"No se pudo procesar el bitmap"}""")
                            return
                        }

                        val outputStream = ByteArrayOutputStream()
                        try {
                            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                            if (!compressed) {
                                callback(false, """{"error":"Error comprimiendo imagen"}""")
                                return
                            }

                            val byteArray = outputStream.toByteArray()
                            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                            val json = JSONObject().apply {
                                put("screenshot", base64)
                                put("sizeKB", byteArray.size / 1024)
                                put("format", "jpeg")
                            }
                            callback(true, json.toString())
                        } finally {
                            bitmap.recycle()
                            outputStream.close()
                        }
                    } catch (e: Exception) {
                        MdmLog.e(TAG, "Error procesando screenshot: ${e.message}")
                        callback(false, """{"error":"${e.message}"}""")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val errorMsg = when (errorCode) {
                        TakeScreenshotCallback.ERROR_TAKE_SCREENSHOT_NO_PERMISSIONS -> "Sin permisos de screenshot"
                        TakeScreenshotCallback.ERROR_TAKE_SCREENSHOT_SECURITY_POLICY -> "Bloqueado por política de seguridad"
                        else -> "Error código: $errorCode"
                    }
                    callback(false, """{"error":"$errorMsg","code":$errorCode}""")
                }
            }
        )
    }
}