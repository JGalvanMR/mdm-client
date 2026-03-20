package com.mdm.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mdm.client.BuildConfig
import com.mdm.client.MainActivity
import com.mdm.client.commands.CommandExecutor
import com.mdm.client.core.Constants
import com.mdm.client.core.MdmLog
import com.mdm.client.core.MdmResult
import com.mdm.client.core.sendLocalBroadcast
import com.mdm.client.data.models.*
import com.mdm.client.data.network.ApiClient
import com.mdm.client.data.network.MdmWebSocketClient
import com.mdm.client.data.network.NetworkMonitor
import com.mdm.client.data.network.WsCommandMessage
import com.mdm.client.data.network.WsEventListener
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.data.queue.CommandResultQueue
import com.mdm.client.device.DeviceInfoCollector
import kotlinx.coroutines.*

class MdmPollingService : Service() {

    private val TAG = "MdmPollingService"

    private lateinit var prefs:          DevicePrefs
    private lateinit var apiClient:      ApiClient
    private lateinit var executor:       CommandExecutor
    private lateinit var registrar:      RegistrationManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var infoCollector:  DeviceInfoCollector
    private lateinit var resultQueue:    CommandResultQueue

    private var wsClient: MdmWebSocketClient? = null

    private val serviceJob  = SupervisorJob()
    private val scope       = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var isRunning      = false
    @Volatile private var pollCycleCount = 0

    // Intervalo de polling de respaldo cuando WS está activo (5 min)
    // Intervalo de polling normal cuando no hay WS (30s del BuildConfig)
    private val fallbackPollIntervalMs = 5 * 60 * 1000L

    override fun onCreate() {
        super.onCreate()
        initDependencies()
        createNotificationChannels()
        acquireWakeLock()
        startForeground(Constants.NOTIF_ID_SERVICE, buildNotification("Iniciando MDM Agent..."))
        MdmLog.i(TAG, "Servicio MDM creado.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            scope.launch { mainLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceJob.cancel()
        wsClient?.disconnect()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Loop principal ────────────────────────────────────────────────────────
    private suspend fun mainLoop() {
        MdmLog.i(TAG, "Loop MDM iniciado.")

        // Esperar registro antes de conectar WS
        val registered = withContext(Dispatchers.IO) { registrar.ensureRegistered() }
        if (!registered) {
            MdmLog.e(TAG, "No se pudo registrar. Reintentando en 30s...")
            delay(30_000)
            scope.launch { mainLoop() }
            return
        }

        // Conectar WebSocket
        connectWebSocket()

        // Polling loop de respaldo
        while (isRunning && scope.isActive) {
            try {
                executePollCycle()
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                MdmLog.e(TAG, "Error en ciclo: ${e.message}", e)
            }

            // Si WS activo → poll cada 5min (solo para sync de estado)
            // Si WS inactivo → poll cada 30s (modo legacy completo)
            val interval = if (wsClient?.isConnected == true)
                fallbackPollIntervalMs
            else
                BuildConfig.POLL_INTERVAL_MS

            MdmLog.d(TAG, "Próximo poll en ${interval/1000}s (WS: ${wsClient?.isConnected})")
            delay(interval)
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────
    private fun connectWebSocket() {
        val token = prefs.deviceToken ?: return
        wsClient?.disconnect()

        wsClient = MdmWebSocketClient(token).apply {
            connect(object : WsEventListener {
                override fun onConnected() {
                    MdmLog.i(TAG, "✓ WebSocket conectado. Push activo.")
                    updateNotification("Conectado (push activo)")
                    // Enviar status inmediatamente al conectar
                    sendCurrentStatus()
                }

                override fun onDisconnected(reason: String) {
                    MdmLog.w(TAG, "WS desconectado: $reason. Reconectando en 10s...")
                    updateNotification("Reconectando...", isError = true)
                    scope.launch {
                        delay(10_000)
                        if (isRunning) connectWebSocket()
                    }
                }

                override fun onCommand(msg: WsCommandMessage) {
                    MdmLog.i(TAG, "▶ WS Push: ${msg.commandType} (Id=${msg.commandId})")
                    updateNotification("Ejecutando ${msg.commandType}...")
                    scope.launch { handlePushCommand(msg) }
                }

                override fun onError(error: String) {
                    MdmLog.e(TAG, "WS error: $error")
                }
            })
        }
    }

    private fun sendCurrentStatus() {
        val ws = wsClient ?: return
        ws.sendStatus(
            batteryLevel   = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageMB      = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            kioskMode      = prefs.kioskModeEnabled,
            cameraDisabled = prefs.cameraDisabled,
            ip             = infoCollector.getLocalIpAddress()
        )
    }

    // ── Ejecutar comando recibido por WS push ─────────────────────────────────
    private suspend fun handlePushCommand(msg: WsCommandMessage) {
        val execResult = if (msg.commandType in CommandType.REQUIRES_MAIN_THREAD) {
            withContext(Dispatchers.Main) {
                executor.execute(msg.commandType, msg.parameters)
            }
        } else {
            withContext(Dispatchers.IO) {
                executor.execute(msg.commandType, msg.parameters)
            }
        }

        if (execResult.success) prefs.commandsExecuted++

        // Reportar resultado — primero por WS, luego por HTTP si falla
        val reportedViaWs = wsClient?.sendResult(
            commandId    = msg.commandId,
            success      = execResult.success,
            resultJson   = execResult.resultJson,
            errorMessage = execResult.errorMessage
        ) ?: false

        if (!reportedViaWs) {
            // Fallback a HTTP
            val token = prefs.deviceToken
            if (token != null) {
                val req = CommandResultRequest(
                    commandId    = msg.commandId,
                    success      = execResult.success,
                    resultJson   = execResult.resultJson,
                    errorMessage = execResult.errorMessage
                )
                when (apiClient.reportCommandResult(token, req)) {
                    is MdmResult.Success -> MdmLog.i(TAG, "✓ Resultado reportado via HTTP")
                    is MdmResult.Failure -> resultQueue.enqueue(req)
                }
            }
        }

        val status = if (execResult.success) "✓" else "✗"
        MdmLog.i(TAG, "$status ${msg.commandType} completado")
        updateNotification(if (wsClient?.isConnected == true) "Conectado (push activo)" else "Esperando comandos...")
        broadcastUiUpdate("$status ${msg.commandType}")

        // Actualizar status por WS después de ejecutar
        sendCurrentStatus()
    }

    // ── Poll de respaldo ──────────────────────────────────────────────────────
    private suspend fun executePollCycle() {
        pollCycleCount++

        if (!networkMonitor.isConnected) {
            updateNotification("Sin red. Esperando...", isError = true)
            return
        }

        val token = prefs.deviceToken ?: run {
            val ok = withContext(Dispatchers.IO) { registrar.ensureRegistered() }
            if (ok) {
                connectWebSocket()
            }
            return
        }

        // Drenar cola de resultados pendientes
        drainResultQueue(token)

        // Si WS activo: solo heartbeat, sin pedir comandos (WS los entrega)
        if (wsClient?.isConnected == true) {
            sendHeartbeatHttp(token)
            return
        }

        // WS inactivo: poll completo
        doPoll(token)
    }

    private suspend fun doPoll(token: String) = withContext(Dispatchers.IO) {
        val pollRequest = PollRequest(
            batteryLevel       = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageAvailableMB = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            ipAddress          = infoCollector.getLocalIpAddress(),
            kioskModeEnabled   = prefs.kioskModeEnabled,
            cameraDisabled     = prefs.cameraDisabled
        )

        when (val result = apiClient.poll(token, pollRequest)) {
            is MdmResult.Success -> {
                prefs.lastPollTimestamp = System.currentTimeMillis()
                val commands = result.data.commands
                if (commands.isNotEmpty()) {
                    updateNotification("Ejecutando ${commands.size} comando(s)...")
                    executeCommands(token, commands)
                } else {
                    updateNotification("Esperando comandos (WS offline)...")
                }
                broadcastUiUpdate("Poll OK — ${commands.size} cmds")
            }
            is MdmResult.Failure -> {
                MdmLog.e(TAG, "Poll fallido: ${result.errorMessage}")
                if (result.errorCode == 401) prefs.clearSession()
            }
        }
    }

    private suspend fun executeCommands(token: String, commands: List<PollCommand>) {
        for (cmd in commands.sortedBy { it.priority }) {
            val execResult = if (cmd.commandType in CommandType.REQUIRES_MAIN_THREAD) {
                withContext(Dispatchers.Main) { executor.execute(cmd.commandType, cmd.parameters) }
            } else {
                withContext(Dispatchers.IO) { executor.execute(cmd.commandType, cmd.parameters) }
            }
            if (execResult.success) prefs.commandsExecuted++
            reportResult(token, CommandResultRequest(cmd.commandId, execResult.success, execResult.resultJson, execResult.errorMessage))
        }
    }

    private suspend fun reportResult(token: String, req: CommandResultRequest) =
        withContext(Dispatchers.IO) {
            when (apiClient.reportCommandResult(token, req)) {
                is MdmResult.Success -> {}
                is MdmResult.Failure -> resultQueue.enqueue(req)
            }
        }

    private suspend fun drainResultQueue(token: String) = withContext(Dispatchers.IO) {
        val pending = resultQueue.dequeueAll()
        for (item in pending) {
            when (apiClient.reportCommandResult(token, item)) {
                is MdmResult.Success -> {}
                is MdmResult.Failure -> resultQueue.enqueue(item)
            }
        }
    }

    private suspend fun sendHeartbeatHttp(token: String) = withContext(Dispatchers.IO) {
        val req = HeartbeatRequest(
            batteryLevel       = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageAvailableMB = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            kioskModeEnabled   = prefs.kioskModeEnabled,
            cameraDisabled     = prefs.cameraDisabled,
            ipAddress          = infoCollector.getLocalIpAddress()
        )
        apiClient.heartbeat(token, req)
    }

    // ── Notificación ──────────────────────────────────────────────────────────
    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Constants.NOTIF_CHANNEL_SERVICE, "MDM Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "MDM Agent activo"; setShowBadge(false); enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(Constants.NOTIF_CHANNEL_ALERTS, "MDM Alertas",
                NotificationManager.IMPORTANCE_HIGH).apply { description = "Alertas MDM" }
        )
    }

    private fun buildNotification(text: String, isError: Boolean = false): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_SERVICE)
            .setContentTitle("MDM Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setColor(if (isError) 0xFFFF4757.toInt() else 0xFF00D897.toInt())
            .build()
    }

    private fun updateNotification(text: String, isError: Boolean = false) {
        getSystemService(NotificationManager::class.java)
            .notify(Constants.NOTIF_ID_SERVICE, buildNotification(text, isError))
    }

    private fun broadcastUiUpdate(msg: String) {
        sendLocalBroadcast(Constants.ACTION_UPDATE_UI) {
            putExtra(Constants.EXTRA_LOG_MESSAGE, msg)
            putExtra(Constants.EXTRA_LAST_POLL, System.currentTimeMillis())
        }
    }

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MdmClient::WakeLock")
            .also { it.acquire(10 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun initDependencies() {
        prefs          = DevicePrefs(this)
        apiClient      = ApiClient()
        executor       = CommandExecutor(this)
        networkMonitor = NetworkMonitor(this)
        infoCollector  = DeviceInfoCollector(this)
        resultQueue    = CommandResultQueue(this)
        registrar      = RegistrationManager(this, apiClient, prefs)
    }
}