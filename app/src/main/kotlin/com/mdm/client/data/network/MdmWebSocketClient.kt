package com.mdm.client.data.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.mdm.client.BuildConfig
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

data class WsCommandMessage(
    @SerializedName("type") val type: String,
    @SerializedName("commandId") val commandId: Int,
    @SerializedName("commandType") val commandType: String,
    @SerializedName("parameters") val parameters: String?,
    @SerializedName("priority") val priority: Int
)

data class WsResultMessage(
    val type: String = "RESULT",
    val commandId: Int,
    val success: Boolean,
    val resultJson: String?,
    val errorMessage: String?
)

data class WsStatusMessage(
    val type: String = "STATUS",
    val batteryLevel: Int?,
    val storageAvailableMB: Long?,
    val kioskModeEnabled: Boolean,
    val cameraDisabled: Boolean,
    val ipAddress: String?
)

interface WsEventListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onCommand(msg: WsCommandMessage)
    fun onError(error: String)
}

class MdmWebSocketClient(private val token: String) {

    private val TAG = "WSClient"
    private val gson: Gson = GsonBuilder().create()

    private val wsUrl =
        BuildConfig.SERVER_URL
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/') + "/ws/device"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var listener: WsEventListener? = null

    @Volatile var isConnected = false
        private set

    private val isConnecting = AtomicBoolean(false)

    private var reconnectAttempts = 0
    private val maxReconnectDelay = 60_000L
    private var shouldReconnect = true

    private fun getReconnectDelay(): Long {
        val delay = (2.0.pow(reconnectAttempts.toDouble()) * 1000L).toLong()
        return delay.coerceAtMost(maxReconnectDelay)
    }

    fun connect(eventListener: WsEventListener) {
        if (isConnected || isConnecting.getAndSet(true)) return
        listener = eventListener
        shouldReconnect = true
        performConnection()
    }

    private fun performConnection() {

        val request = Request.Builder()
            .url(wsUrl)
            .header("Device-Token", token)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                isConnecting.set(false)
                reconnectAttempts = 0
                Log.i(TAG, "WS conectado")
                listener?.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                handleDisconnect(reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect(t.message ?: "error")
            }
        })
    }

    private fun handleDisconnect(reason: String) {
        isConnected = false
        isConnecting.set(false)
        listener?.onDisconnected(reason)

        if (!shouldReconnect) return

        val delay = getReconnectDelay()
        Log.w(TAG, "Reintentando WS en ${delay}ms")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isConnected && shouldReconnect) {
                reconnectAttempts++
                performConnection()
            }
        }, delay)
    }

    private fun handleIncoming(text: String) {
        try {
            val base = gson.fromJson(text, Map::class.java)
            val type = base["type"] as? String ?: return

            when (type.uppercase()) {
                "COMMAND" -> listener?.onCommand(gson.fromJson(text, WsCommandMessage::class.java))
                "PING" -> sendJson("""{"type":"PONG"}""")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error ${e.message}")
        }
    }

    fun sendResult(id: Int, success: Boolean, result: String?, error: String?): Boolean {
        return sendJson(gson.toJson(WsResultMessage(commandId = id, success = success, resultJson = result, errorMessage = error)))
    }
	
	fun sendStatus(
    battery: Int?,
    storage: Long?,
    kiosk: Boolean,
    cam: Boolean,
    ip: String?
): Boolean {
    val msg = WsStatusMessage(
        batteryLevel = battery,
        storageAvailableMB = storage,
        kioskModeEnabled = kiosk,
        cameraDisabled = cam,
        ipAddress = ip
    )
    return sendJson(gson.toJson(msg))
}


    fun sendJson(json: String): Boolean {
        return try {
            webSocket?.send(json) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun sendBinary(data: ByteArray): Boolean {
        return webSocket?.send(ByteString.of(*data)) ?: false
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, null)
        webSocket = null
        isConnected = false
    }
}