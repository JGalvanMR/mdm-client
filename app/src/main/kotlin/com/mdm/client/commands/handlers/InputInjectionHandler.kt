package com.mdm.client.commands.handlers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.mdm.client.core.ExecutionResult

class InputInjectionHandler(private val context: Context) {
    private val TAG = "InputInjection"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var accessibilityService: AccessibilityService? = null
    
    companion object {
        // Referencia estática al servicio de accesibilidad
        var instance: AccessibilityService? = null
    }

    data class InputEvent(
        val type: String,
        val x: Float?,
        val y: Float?,
        val keyCode: Int?
    )

    fun processInput(jsonParams: String?): ExecutionResult {
        if (instance == null) {
            return ExecutionResult.failure("AccessibilityService no disponible. Activa el servicio MDM Screenshot en ajustes.")
        }

        return try {
            val event = parseInputEvent(jsonParams)
            when (event.type) {
                "touch_down", "touch_up", "touch_move" -> injectTouch(event)
                "key_down", "key_up" -> injectKey(event)
                else -> ExecutionResult.failure("Tipo de input desconocido: ${event.type}")
            }
        } catch (e: Exception) {
            ExecutionResult.failure("Error procesando input: ${e.message}")
        }
    }

    private fun parseInputEvent(json: String?): InputEvent {
        val obj = org.json.JSONObject(json ?: "{}")
        return InputEvent(
            type = obj.optString("eventType", ""),
            x = obj.optDouble("x", -1.0).takeIf { it >= 0 }?.toFloat(),
            y = obj.optDouble("y", -1.0).takeIf { it >= 0 }?.toFloat(),
            keyCode = obj.optInt("keyCode", -1).takeIf { it > 0 }
        )
    }

    private fun injectTouch(event: InputEvent): ExecutionResult {
        if (event.x == null || event.y == null) {
            return ExecutionResult.failure("Coordenadas inválidas")
        }

        val path = Path().apply {
            moveTo(event.x, event.y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        mainHandler.post {
            instance?.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Toque inyectado en (${event.x}, ${event.y})")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Toque cancelado")
                }
            }, null)
        }

        return ExecutionResult.success("""{"injected":"touch","x":${event.x},"y":${event.y}}""")
    }

    private fun injectKey(event: InputEvent): ExecutionResult {
        val keyCode = event.keyCode ?: return ExecutionResult.failure("KeyCode requerido")
        
        mainHandler.post {
            try {
                // Mapeo de teclas comunes a acciones globales
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    KeyEvent.KEYCODE_HOME -> instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    KeyEvent.KEYCODE_APP_SWITCH -> instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    else -> Log.w(TAG, "KeyCode $keyCode no mapeado a acción global")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inyectando tecla", e)
            }
        }

        return ExecutionResult.success("""{"injected":"key","keyCode":$keyCode}""")
    }
}