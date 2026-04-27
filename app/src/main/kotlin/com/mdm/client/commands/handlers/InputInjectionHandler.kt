package com.mdm.client.commands.handlers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.mdm.client.core.ExecutionResult
import kotlin.math.max

class InputInjectionHandler(private val context: Context) {
    private val TAG = "InputInjection"

        companion object {
        var instance: AccessibilityService? = null
        
        var streamWidth: Int = 1080
        var streamHeight: Int = 2336
        
        @Volatile private var realWidth: Int = 0
        @Volatile private var realHeight: Int = 0
        
        fun updateDeviceMetrics(context: Context) {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ : WindowMetrics garantiza el tamaño físico real absoluto
                    val windowMetrics = windowManager.currentWindowMetrics
                    realWidth = windowMetrics.bounds.width()
                    realHeight = windowMetrics.bounds.height()
                } else {
                    // Android 10 o inferior: getRealSize obtiene la pantalla completa con barras
                    val display = windowManager.defaultDisplay
                    val size = android.graphics.Point()
                    display.getRealSize(size)
                    realWidth = size.x
                    realHeight = size.y
                }
                
                Log.i("InputInjection", "Dimensiones reales calculadas: ${realWidth}x${realHeight}")
            } catch (e: Exception) {
                // Fallback por si algo falla
                val metrics = context.resources.displayMetrics
                realWidth = metrics.widthPixels
                realHeight = metrics.heightPixels
            }
        }
    }

    init {
        updateDeviceMetrics(context)
    }

    data class InputEvent(
        val type: String,
        val x: Float?,
        val y: Float?,
        val keyCode: Int?,
        val scrollDeltaX: Float?,
        val scrollDeltaY: Float?
    )

    private var isGestureActive = false
    private val currentPath = Path()
    private var gestureStartTime = 0L
    private var lastDispatchedTime = 0L
    private var lastMoveCoords: Pair<Float, Float>? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    fun processInput(jsonParams: String?): ExecutionResult {
        if (instance == null) {
            return ExecutionResult.failure("AccessibilityService no disponible. Activa el servicio MDM en ajustes.")
        }

        return try {
            val event = parseInputEvent(jsonParams)
            when (event.type) {
                "touch_down" -> handleTouchDown(event)
                "touch_move" -> handleTouchMove(event)
                "touch_up" -> handleTouchUp(event)
                "tap" -> handleTap(event)
                "long_press" -> handleLongPress(event)
                "scroll" -> handleScroll(event)
                "key_down", "key_up" -> injectKey(event)
                "home" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "back" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "recents" -> performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "power" -> {
                    Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26"))
                    ExecutionResult.success("""{"injected":"power"}""")
                }
                else -> ExecutionResult.failure("Tipo desconocido: ${event.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando input", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    private fun parseInputEvent(json: String?): InputEvent {
        val obj = org.json.JSONObject(json ?: "{}")
        return InputEvent(
            type = obj.optString("eventType", ""),
            x = obj.optDouble("x", -1.0).takeIf { it >= 0 }?.toFloat(),
            y = obj.optDouble("y", -1.0).takeIf { it >= 0 }?.toFloat(),
            keyCode = obj.optInt("keyCode", -1).takeIf { it > 0 },
            scrollDeltaX = obj.optDouble("scrollDeltaX", 0.0).toFloat().takeIf { it != 0f },
            scrollDeltaY = obj.optDouble("scrollDeltaY", 0.0).toFloat().takeIf { it != 0f }
        )
    }

    private fun mapCoordinates(videoX: Float, videoY: Float): Pair<Float, Float> {
        if (realWidth == 0) updateDeviceMetrics(context)
        val rw = max(realWidth, 1).toFloat()
        val rh = max(realHeight, 1).toFloat()
        val sw = max(streamWidth, 1).toFloat()
        val sh = max(streamHeight, 1).toFloat()
        return Pair(videoX * (rw / sw), videoY * (rh / sh))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GESTOS (FIX: Usa willContinue en Android 12+ para evitar el crash de 5s)
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleTouchDown(event: InputEvent): ExecutionResult {
        if (event.x == null || event.y == null) return ExecutionResult.failure("Coordenadas inválidas")
        val (realX, realY) = mapCoordinates(event.x, event.y)

        isGestureActive = true
        lastMoveCoords = Pair(realX, realY)
        gestureStartTime = SystemClock.uptimeMillis()
        currentPath.reset()
        currentPath.moveTo(realX, realY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: willContinue = true mantiene el dedo abajo infinitamente
            val stroke = GestureDescription.StrokeDescription(currentPath, 0, 5000, true)
            dispatchStroke(stroke)
        } else {
            dispatchPath(currentPath, gestureStartTime, gestureStartTime + 5000)
        }
        return ExecutionResult.success("""{"injected":"down"}""")
    }

    private fun handleTouchMove(event: InputEvent): ExecutionResult {
        if (!isGestureActive || event.x == null || event.y == null) return ExecutionResult.success("{}")
        val (realX, realY) = mapCoordinates(event.x, event.y)
        val prev = lastMoveCoords ?: return ExecutionResult.success("{}")

        //val dx = realX - prev.first
        //val dy = realY - prev.second
        //if (dx * dx + dy * dy < touchSlop * touchSlop) return ExecutionResult.success("{}")

        val now = SystemClock.uptimeMillis()
        if (now - lastDispatchedTime < 8) return ExecutionResult.success("{}") // Max ~60fps
        lastDispatchedTime = now

        lastMoveCoords = Pair(realX, realY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Micro-gesto solo con el segmento actual (no acumula)
            val movePath = Path().apply { moveTo(prev.first, prev.second); lineTo(realX, realY) }
            val stroke = GestureDescription.StrokeDescription(movePath, 0, 50, true)
            dispatchStroke(stroke)
        } else {
            // Android 11-: Acumular en path, pero limitar a 4.9s para no crashear
            currentPath.lineTo(realX, realY)
            val elapsed = (now - gestureStartTime).coerceAtMost(4900L)
            dispatchPath(currentPath, gestureStartTime, gestureStartTime + elapsed)
        }
        return ExecutionResult.success("{}")
    }

    private fun handleTouchUp(event: InputEvent): ExecutionResult {
        if (!isGestureActive) return ExecutionResult.success("{}")
        isGestureActive = false
        val last = lastMoveCoords

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // ★ FIX: Usar startTime = 5000 para alinear con el touch_down original.
            // Si usamos 0, Android lo confunde con un "tap" y abre apps al soltar.
            val upPath = Path().apply { moveTo(last?.first ?: 0f, last?.second ?: 0f) }
            val stroke = GestureDescription.StrokeDescription(upPath, 5000, 1, false)
            dispatchStroke(stroke)
        } else {
            val now = SystemClock.uptimeMillis()
            val elapsed = (now - gestureStartTime).coerceIn(10L, 4900L)
            dispatchPath(currentPath, gestureStartTime, gestureStartTime + elapsed)
        }

        currentPath.reset()
        lastMoveCoords = null
        return ExecutionResult.success("""{"injected":"up"}""")
    }

    private fun dispatchStroke(stroke: GestureDescription.StrokeDescription) {
        val service = instance ?: return
        try {
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {}
                override fun onCancelled(gestureDescription: GestureDescription?) {}
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error despachando stroke", e)
        }
    }

    private fun dispatchPath(path: Path, startTime: Long, endTime: Long) {
        if (endTime <= startTime) return
        val service = instance ?: return
        try {
            val stroke = GestureDescription.StrokeDescription(path, startTime, endTime - startTime)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {}
                override fun onCancelled(gestureDescription: GestureDescription?) {}
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error despachando path", e)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TECLADO Y OTROS
    // ══════════════════════════════════════════════════════════════════════════

    private fun handleTap(event: InputEvent): ExecutionResult {
        if (event.x == null || event.y == null) return ExecutionResult.failure("Inválido")
        val (realX, realY) = mapCoordinates(event.x, event.y)
        dispatchPath(Path().apply { moveTo(realX, realY) }, 0, 50)
        return ExecutionResult.success("""{"injected":"tap"}""")
    }

    private fun handleLongPress(event: InputEvent): ExecutionResult {
        if (event.x == null || event.y == null) return ExecutionResult.failure("Inválido")
        val (realX, realY) = mapCoordinates(event.x, event.y)
        dispatchPath(Path().apply { moveTo(realX, realY) }, 0, 500)
        return ExecutionResult.success("""{"injected":"long_press"}""")
    }

    private fun handleScroll(event: InputEvent): ExecutionResult {
        if (event.x == null || event.y == null || event.scrollDeltaY == null) return ExecutionResult.failure("Inválido")
        val (startX, startY) = mapCoordinates(event.x, event.y)
        val endY = startY + event.scrollDeltaY * (realHeight.toFloat() / max(streamHeight, 1))
        dispatchPath(Path().apply { moveTo(startX, startY); lineTo(startX, endY) }, 0, 300)
        return ExecutionResult.success("""{"injected":"scroll"}""")
    }

    private fun injectKey(event: InputEvent): ExecutionResult {
        val keyCode = event.keyCode ?: return ExecutionResult.failure("KeyCode requerido")
        val service = instance ?: return ExecutionResult.failure("Servicio no disponible")
        try {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                KeyEvent.KEYCODE_HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                KeyEvent.KEYCODE_APP_SWITCH -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                KeyEvent.KEYCODE_VOLUME_UP -> Runtime.getRuntime().exec(arrayOf("input", "keyevent", "24"))
                KeyEvent.KEYCODE_VOLUME_DOWN -> Runtime.getRuntime().exec(arrayOf("input", "keyevent", "25"))
                else -> Runtime.getRuntime().exec(arrayOf("input", "keyevent", keyCode.toString()))
            }
            return ExecutionResult.success("""{"injected":"key","keyCode":$keyCode}""")
        } catch (e: Exception) {
            return ExecutionResult.failure("Error inyectando tecla: ${e.message}")
        }
    }
    
    private fun performGlobalAction(action: Int): ExecutionResult {
        val service = instance ?: return ExecutionResult.failure("Servicio no disponible")
        return try {
            if (service.performGlobalAction(action)) ExecutionResult.success("""{"injected":"global_action"}""")
            else ExecutionResult.failure("Global action falló")
        } catch (e: Exception) {
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}