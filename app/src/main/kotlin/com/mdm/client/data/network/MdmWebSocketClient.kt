package com.mdm.client.data.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.mdm.client.BuildConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

// ── Mensajes WS ───────────────────────────────────────────────────────────────
data class WsCommandMessage(
        @SerializedName("type") val type: String,
        @SerializedName("commandId") val commandId: Int,
        @SerializedName("commandType") val commandType: String,
        @SerializedName("parameters") val parameters: String?,
        @SerializedName("priority") val priority: Int
)

data class WsResultMessage(
        @SerializedName("type") val type: String = "RESULT",
        @SerializedName("commandId") val commandId: Int,
        @SerializedName("success") val success: Boolean,
        @SerializedName("resultJson") val resultJson: String?,
        @SerializedName("errorMessage") val errorMessage: String?
)

data class WsStatusMessage(
        @SerializedName("type") val type: String = "STATUS",
        @SerializedName("batteryLevel") val batteryLevel: Int?,
        @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
        @SerializedName("kioskModeEnabled") val kioskModeEnabled: Boolean,
        @SerializedName("cameraDisabled") val cameraDisabled: Boolean,
        @SerializedName("ipAddress") val ipAddress: String?
)

// ── Callbacks ─────────────────────────────────────────────────────────────────
interface WsEventListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onCommand(msg: WsCommandMessage)
    fun onError(error: String)
}

// ── Cliente WebSocket ─────────────────────────────────────────────────────────
class MdmWebSocketClient(private val token: String) {

    private val TAG = "MdmWebsocketClient"
    private val gson: Gson = GsonBuilder().create()

    private val wsUrl =
            BuildConfig.SERVER_URL
                    .trimEnd('/')
                    .replace("http://", "ws://")
                    .replace("https://", "wss://")
                    .plus("/ws/device")

    private val client =
            OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = sin timeout para WS
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .pingInterval(25, TimeUnit.SECONDS) // keepalive nativo de OkHttp
                    .build()

    private var webSocket: WebSocket? = null
    private var listener: WsEventListener? = null

    @Volatile
    var isConnected = false
        private set

    private val isConnecting = AtomicBoolean(false)
    private val reconnectDelayMs = 5000L
    private val maxReconnectAttempts = 10
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    // ── Conectar ──────────────────────────────────────────────────────────────
    fun connect(eventListener: WsEventListener) {
        if (isConnected || isConnecting.getAndSet(true)) return
        listener = eventListener
        shouldReconnect = true

        performConnection()
    }

    private fun performConnection() {
        val request =
                Request.Builder()
                        .url(wsUrl)
                        .header("Device-Token", token)
                        .header("Accept", "application/json")
                        .build()

        webSocket =
                client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(ws: WebSocket, response: Response) {
                                isConnected = true
                                isConnecting.set(false)
                                reconnectAttempts = 0
                                Log.i(TAG, "WS conectado.")
                                listener?.onConnected()
                            }

                            override fun onMessage(ws: WebSocket, text: String) {
                                handleIncoming(text)
                            }

                            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                                Log.i(TAG, "WS cerrando: $code $reason")
                                ws.close(1000, null)
                            }

                            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                                isConnected = false
                                isConnecting.set(false)
                                listener?.onDisconnected(reason)
                                attemptReconnect()
                            }

                            override fun onFailure(
                                    ws: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                isConnected = false
                                isConnecting.set(false)
                                Log.e(TAG, "WS error: ${t.message}")
                                listener?.onError(t.message ?: "Error desconocido")
                                attemptReconnect()
                            }
                        }
                )
    }

    private fun attemptReconnect() {
        if (!shouldReconnect || reconnectAttempts >= maxReconnectAttempts) return

        reconnectAttempts++
        Log.w(TAG, "Reconectando en ${reconnectDelayMs}ms... (intento $reconnectAttempts)")

        Handler(Looper.getMainLooper())
                .postDelayed(
                        {
                            if (!isConnected && shouldReconnect) {
                                performConnection()
                            }
                        },
                        reconnectDelayMs * reconnectAttempts
                ) // Backoff exponencial simple
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Cliente cerrando")
        webSocket = null
        isConnected = false
    }

    // ── Enviar resultado de comando ───────────────────────────────────────────
    fun sendResult(
            commandId: Int,
            success: Boolean,
            resultJson: String?,
            errorMessage: String?
    ): Boolean {
        if (!isConnected) return false
        val msg =
                WsResultMessage(
                        commandId = commandId,
                        success = success,
                        resultJson = resultJson,
                        errorMessage = errorMessage
                )
        return sendJson(gson.toJson(msg))
    }

    fun sendBinary(data: ByteArray): Boolean {
        return try {
            webSocket?.send(okio.ByteString.of(*data)) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending binary", e)
            false
        }
    }

    // ── Enviar status del dispositivo ─────────────────────────────────────────
    fun sendStatus(
            batteryLevel: Int?,
            storageMB: Long?,
            kioskMode: Boolean,
            cameraDisabled: Boolean,
            ip: String?
    ): Boolean {
        if (!isConnected) return false
        val msg =
                WsStatusMessage(
                        batteryLevel = batteryLevel,
                        storageAvailableMB = storageMB,
                        kioskModeEnabled = kioskMode,
                        cameraDisabled = cameraDisabled,
                        ipAddress = ip
                )
        return sendJson(gson.toJson(msg))
    }

    // ── Procesar mensaje entrante ─────────────────────────────────────────────
    private fun handleIncoming(text: String) {
        try {
            val base = gson.fromJson(text, Map::class.java)
            val type = base["type"] as? String ?: return

            when (type.uppercase()) {
                "COMMAND" -> {
                    val cmd = gson.fromJson(text, WsCommandMessage::class.java)
                    listener?.onCommand(cmd)
                }
                "INPUT" -> {
                    // Input remoto desde el viewer web
                    val inputJson = text // El texto completo es el JSON del input
                    // Procesar como comando INPUT
                    // Esto se maneja igual que un comando normal, pero viene por WS
                    listener?.onCommand(
                            WsCommandMessage(
                                    type = "INPUT",
                                    commandId = 0, // Los inputs no tienen ID de comando
                                    commandType = "INPUT",
                                    parameters = inputJson,
                                    priority = 5
                            )
                    )
                }
                "PING" -> {
                    sendJson("""{"type":"PONG"}""")
                }
                else -> Log.d(TAG, "Mensaje WS ignorado: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando WS: ${e.message}")
        }
    }

    fun sendJson(json: String): Boolean {
        return try {
            webSocket?.send(json) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando WS: ${e.message}")
            false
        }
    }
}
