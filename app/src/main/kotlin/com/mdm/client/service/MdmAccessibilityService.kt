package com.mdm.client.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

class MdmAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "MdmAccessibility"
        var instance: MdmAccessibilityService? = null
            private set
        
        // Constantes literales para Android 11+
        private const val FLAG_SELECTED = 0
        private const val FLAG_FULLSCREEN = 1
        
        fun isReady(): Boolean = instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "✅ Servicio conectado")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "❌ Servicio desconectado")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun takeScreenshot(commandId: Int, callback: (success: Boolean, result: String) -> Unit) {
        Log.d(TAG, "Intentando screenshot cmd=$commandId")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(false, "REQUIERE_ANDROID_11")
            return
        }

        try {
            // Intentar con FLAG_SELECTED (0) primero
            takeScreenshot(
                FLAG_SELECTED,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        Log.i(TAG, "Screenshot OK cmd=$commandId")
                        processScreenshot(screenshot, callback)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot falló código=$errorCode")
                        
                        // Si falla, intentar con FLAG_FULLSCREEN (1)
                        if (errorCode == 4) {
                            Log.w(TAG, "Intentando con FLAG fullscreen...")
                            tryFullscreenScreenshot(callback)
                        } else {
                            callback(false, errorCodeToString(errorCode))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Excepción: ${e.message}")
            callback(false, "EXCEPCION: ${e.message}")
        }
    }

    private fun tryFullscreenScreenshot(callback: (success: Boolean, result: String) -> Unit) {
        try {
            takeScreenshot(
                FLAG_FULLSCREEN,
                ContextCompat.getMainExecutor(this),
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        processScreenshot(screenshot, callback)
                    }
                    override fun onFailure(errorCode: Int) {
                        callback(false, errorCodeToString(errorCode))
                    }
                }
            )
        } catch (e: Exception) {
            callback(false, "ERROR_FALLBACK: ${e.message}")
        }
    }

    private fun processScreenshot(screenshot: ScreenshotResult, callback: (success: Boolean, result: String) -> Unit) {
        try {
            val hardwareBuffer = screenshot.hardwareBuffer
            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
            
            if (bitmap == null) {
                callback(false, "NO_BITMAP")
                return
            }
            
            val base64 = bitmapToBase64(bitmap)
            val sizeKB = bitmap.byteCount / 1024
            hardwareBuffer.close()
            bitmap.recycle()
            
            callback(true, """{"screenshot":"$base64","sizeKB":$sizeKB}""")
        } catch (e: Exception) {
            callback(false, "PROCESS_ERROR: ${e.message}")
        }
    }

    private fun errorCodeToString(code: Int): String {
        return when(code) {
            1 -> "ERROR_NO_PERMISO"
            2 -> "ERROR_INTERVALO_CORTO" 
            3 -> "ERROR_DISPLAY_INVALIDO"
            4 -> "ERROR_POLITICA_RESTRINGIDA"
            else -> "ERROR_CODIGO_$code"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }
}