// app/src/main/kotlin/com/mdm/client/commands/handlers/AccessibilityScreenshotService.kt
package com.mdm.client.commands.handlers

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.mdm.client.core.MdmLog

/**
 * Servicio de accesibilidad que permite captura de pantalla silenciosa. Registrar en
 * AndroidManifest.xml como accessibility service.
 */
class AccessibilityScreenshotService : AccessibilityService() {

    private val TAG = "AccessibilityScreenshot"

    companion object {
        @Volatile private var instance: AccessibilityScreenshotService? = null

        fun getInstance(): AccessibilityScreenshotService? = instance
    }

    override fun onServiceConnected() {
        instance = this
        MdmLog.i(TAG, "Accessibility Screenshot Service conectado.")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun takeScreenshot(callback: (Bitmap?, String?) -> Unit) {
        // Reemplazar el bloque takeScreenshot completo:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    takeScreenshot(
        1, // TAKE_SCREENSHOT_NEXT_FRAME = 1
        mainExecutor,
        object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(
                    screenshotResult.hardwareBuffer,
                    screenshotResult.colorSpace
                )?.copy(Bitmap.Config.ARGB_8888, false)
                screenshotResult.hardwareBuffer.close()
                callback(bitmap, null)
            }
            override fun onFailure(errorCode: Int) {
                callback(null, "Error código: $errorCode")
            }
        }
    )
} else {
    callback(null, "Requiere Android 12+ (API 31)")
}
    }
}
