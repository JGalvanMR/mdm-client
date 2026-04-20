package com.mdm.client.service

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.mdm.client.core.*
import com.mdm.client.data.network.*
import kotlinx.coroutines.*
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.data.network.ApiClient
import com.mdm.client.service.RegistrationManager
import com.mdm.client.commands.CommandExecutor
import com.mdm.client.commands.handlers.ScreenStreamHandler

class MdmPollingService : Service() {

    private lateinit var prefs: DevicePrefs
    private lateinit var apiClient: ApiClient
    private lateinit var registrar: RegistrationManager
    private lateinit var executor: CommandExecutor

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var wsClient: MdmWebSocketClient
    private var isRunning = false
    private var isWsMode = false

    // ════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()

        prefs     = DevicePrefs(this)
        apiClient = ApiClient()
        registrar = RegistrationManager(this, apiClient, prefs)
        executor  = CommandExecutor(this)

        // CRÍTICO: el canal DEBE existir antes de llamar a startForeground().
        // En Android 8+ (API 26) startForeground() valida que el channelId
        // referenciado en la notificación esté registrado en NotificationManager.
        // Si no existe → CannotPostForegroundServiceNotificationException → crash.
        // createNotificationChannel() es idempotente: llamarlo varias veces es seguro.
        createNotificationChannel()

        startForeground(Constants.NOTIF_ID_SERVICE, buildNotification("Iniciando..."))
        startWatchdog()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!isRunning) {
            isRunning = true
            scope.launch { init() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
		ScreenStreamHandler.WebSocketHolder.instance = null
        if (::wsClient.isInitialized) wsClient.disconnect()
        super.onDestroy()
    }

    // ════════════════════════════════════════════════════════════════════════
    // CANAL DE NOTIFICACIÓN
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Registra el canal de notificación del servicio en el sistema.
     *
     * Reglas:
     * - Debe llamarse ANTES de startForeground() (llamado en onCreate).
     * - Es idempotente: el sistema ignora la llamada si el canal ya existe.
     * - IMPORTANCE_LOW → sin sonido ni vibración para una notificación permanente
     *   de servicio; no molesta al usuario pero cumple el requisito del sistema.
     * - setShowBadge(false) → no muestra badge en el ícono de la app.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIF_CHANNEL_SERVICE,          // "mdm_service_channel"
            getString(com.mdm.client.R.string.notif_channel_name),    // "MDM Service"
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(com.mdm.client.R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    // ════════════════════════════════════════════════════════════════════════
    // NOTIFICACIÓN PERSISTENTE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Construye la notificación del foreground service.
     *
     * channelId usa Constants.NOTIF_CHANNEL_SERVICE ("mdm_service_channel"),
     * que es el mismo canal registrado en createNotificationChannel().
     * Antes usaba el literal "mdm" que nunca fue registrado → causa del crash.
     */
    private fun buildNotification(text: String, isError: Boolean = false): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_SERVICE)
            .setContentTitle(getString(com.mdm.client.R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)           // No se puede descartar deslizando
            .setOnlyAlertOnce(true)     // No re-suena en cada update
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String, isError: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.NOTIF_ID_SERVICE, buildNotification(text, isError))
    }

    // ════════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN (REGISTRO + CONEXIÓN WS)
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun init() {
        val registered = registrar.ensureRegistered()

        if (!registered) {
            updateNotification("Error de registro. Reintentando...", isError = true)
            delay(10_000)
            init()
            return
        }

        connectWS()
    }

    // ════════════════════════════════════════════════════════════════════════
    // WEBSOCKET (TIEMPO REAL)
    // ════════════════════════════════════════════════════════════════════════

    private fun connectWS() {
        val token = prefs.deviceToken ?: run {
            updateNotification("Sin token — registrando...", isError = true)
            scope.launch {
                val ok = registrar.ensureRegistered()
                if (ok) connectWS()
            }
            return
        }

        wsClient = MdmWebSocketClient(token).apply {
            connect(object : WsEventListener {

                override fun onConnected() {
                    isWsMode = true
                    updateNotification("Tiempo real activo")
                    startHeartbeat()
					
					ScreenStreamHandler.WebSocketHolder.instance = this@apply
                }

                override fun onDisconnected(reason: String) {
                    isWsMode = false
                    updateNotification("Reconectando (fallback polling)...", isError = true)
                    startPolling()
					
					ScreenStreamHandler.WebSocketHolder.instance = null
                }

                override fun onCommand(msg: WsCommandMessage) {
                    scope.launch { executeCommand(msg) }
                }

                override fun onError(error: String) {
                    updateNotification("Error WS: $error", isError = true)
                }
            })
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // EJECUCIÓN DE COMANDOS
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun executeCommand(cmd: WsCommandMessage) {
        val result = executor.execute(cmd.commandType, cmd.parameters)
        wsClient.sendResult(
            cmd.commandId,
            result.success,
            result.resultJson,
            result.errorMessage
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // POLLING (FALLBACK)
    // Se activa solo cuando el WebSocket no está disponible.
    // ════════════════════════════════════════════════════════════════════════

    private fun startPolling() {
        scope.launch {
            while (isRunning && !isWsMode) {
                delay(30_000)
                // Aquí podrías añadir un poll HTTP explícito si el servidor
                // lo requiere cuando WS está caído. Por ahora el watchdog
                // reintenta la conexión WS automáticamente.
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HEARTBEAT (cada 60s cuando WS activo)
    // ════════════════════════════════════════════════════════════════════════

    private fun startHeartbeat() {
        scope.launch {
            while (isRunning && isWsMode) {
                val collector = TelemetryCollector(this@MdmPollingService)
                val telemetry = collector.collect()

                wsClient.sendStatus(
                    battery  = telemetry.batteryLevel,
                    storage  = telemetry.storageAvailableMB,
                    kiosk    = telemetry.kioskModeEnabled,
                    cam      = telemetry.cameraDisabled,
                    ip       = telemetry.ipAddress
                )

                delay(60_000)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // WATCHDOG
    // Cada 60s verifica si el WS sigue activo; si no, intenta reconectar.
    // Esto garantiza resiliencia ante drops de red sin race conditions.
    // ════════════════════════════════════════════════════════════════════════

    private fun startWatchdog() {
        scope.launch {
            while (isRunning) {
                delay(60_000)
                if (!isWsMode) {
                    MdmLog.w("MdmPollingService", "Watchdog: WS caído, intentando reconectar...")
                    connectWS()
                }
            }
        }
    }
}