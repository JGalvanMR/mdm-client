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
import com.mdm.client.R
import com.mdm.client.commands.CommandExecutor
import com.mdm.client.core.Constants
import com.mdm.client.core.MdmLog
import com.mdm.client.core.MdmResult
import com.mdm.client.core.sendLocalBroadcast
import com.mdm.client.data.models.CommandResultRequest
import com.mdm.client.data.models.CommandType
import com.mdm.client.data.models.HeartbeatRequest
import com.mdm.client.data.models.PollRequest
import com.mdm.client.data.network.ApiClient
import com.mdm.client.data.network.NetworkMonitor
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.data.queue.CommandResultQueue
import com.mdm.client.device.DeviceInfoCollector
import kotlinx.coroutines.*

class MdmPollingService : Service() {

    private val TAG = "MdmPollingService"

    // ── Dependencias ───────────────────────────────────────────────────────────
    private lateinit var prefs:           DevicePrefs
    private lateinit var apiClient:       ApiClient
    private lateinit var executor:        CommandExecutor
    private lateinit var registrar:       RegistrationManager
    private lateinit var networkMonitor:  NetworkMonitor
    private lateinit var infoCollector:   DeviceInfoCollector
    private lateinit var resultQueue:     CommandResultQueue

    // ── Coroutines ────────────────────────────────────────────────────────────
    private val serviceJob = SupervisorJob()
    private val scope       = CoroutineScope(Dispatchers.IO + serviceJob)

    // ── WakeLock: evita que el CPU duerma mientras ejecuta comandos críticos ──
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Estado interno ────────────────────────────────────────────────────────
    @Volatile private var isRunning       = false
    @Volatile private var lastPollSuccess = false
    @Volatile private var pollCycleCount  = 0

    // ════════════════════════════════════════════════════════════════════════════
    // CICLO DE VIDA
    // ════════════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        initDependencies()
        createNotificationChannels()
        acquireWakeLock()
        startForeground(Constants.NOTIF_ID_SERVICE, buildNotification("Iniciando MDM Agent..."))
        MdmLog.i(TAG, "MdmPollingService creado.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MdmLog.i(TAG, "onStartCommand recibido (startId=$startId).")
        if (!isRunning) {
            isRunning = true
            scope.launch { mainLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        MdmLog.w(TAG, "MdmPollingService destruido. El sistema lo reiniciará si es START_STICKY.")
        isRunning = false
        serviceJob.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ════════════════════════════════════════════════════════════════════════════
    // LOOP PRINCIPAL
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun mainLoop() {
        MdmLog.i(TAG, "Loop principal iniciado. Intervalo: ${BuildConfig.POLL_INTERVAL_MS}ms")

        while (isRunning && scope.isActive) {
            try {
                executeCycle()
            } catch (e: CancellationException) {
                MdmLog.i(TAG, "Loop cancelado por CancellationException.")
                break
            } catch (e: Exception) {
                MdmLog.e(TAG, "Error no capturado en ciclo: ${e.message}", e)
            }

            delay(BuildConfig.POLL_INTERVAL_MS)
        }

        MdmLog.i(TAG, "Loop principal terminado.")
    }

    private suspend fun executeCycle() {
        pollCycleCount++
        MdmLog.d(TAG, "── Ciclo #$pollCycleCount ──")

        // Sin red → actualizar notificación y esperar
        if (!networkMonitor.isConnected) {
            MdmLog.w(TAG, "Sin conexión de red.")
            updateNotification("Sin red. Esperando conexión...", isError = true)
            lastPollSuccess = false
            return
        }

        // Registro si no se ha completado
        val registered = withContext(Dispatchers.IO) { registrar.ensureRegistered() }
        if (!registered) {
            updateNotification("Error de registro. Reintentando...", isError = true)
            return
        }

        val token = prefs.deviceToken ?: return

        // Enviar resultados pendientes en cola (de ciclos anteriores sin red)
        drainPendingResultQueue(token)

        // Heartbeat cada 2 ciclos (para no sobrecargar el servidor)
        if (pollCycleCount % 2 == 0) {
            sendHeartbeat(token)
        }

        // Poll principal
        doPoll(token)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // POLL
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun doPoll(token: String) = withContext(Dispatchers.IO) {
        val pollRequest = PollRequest(
            batteryLevel       = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageAvailableMB = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            ipAddress          = infoCollector.getLocalIpAddress()
        )

        val result = apiClient.poll(token, pollRequest)

        when (result) {
            is MdmResult.Success -> {
                val response = result.data
                prefs.lastPollTimestamp = System.currentTimeMillis()
                lastPollSuccess         = true

                val count = response.commands.size
                if (count > 0) {
                    MdmLog.i(TAG, "✓ Poll OK: $count comando(s) recibido(s).")
                    updateNotification("Ejecutando $count comando(s)...")
                    executeCommands(token, response.commands)
                } else {
                    MdmLog.d(TAG, "✓ Poll OK: sin comandos pendientes.")
                    updateNotification("Esperando comandos del servidor...")
                }

                broadcastUiUpdate("Poll OK — ${if (count > 0) "$count cmds" else "sin cmds"}")
            }

            is MdmResult.Failure -> {
                lastPollSuccess = false
                MdmLog.e(TAG, "✗ Poll fallido: ${result.errorMessage}")
                updateNotification("Error de conexión. Reintentando...", isError = true)

                // Si es 401, limpiar sesión para forzar re-registro
                if (result.errorCode == 401) {
                    MdmLog.w(TAG, "Token inválido (401). Limpiando sesión...")
                    prefs.clearSession()
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // EJECUTAR COMANDOS
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun executeCommands(
        token: String,
        commands: List<com.mdm.client.data.models.PollCommand>
    ) {
        for (command in commands) {
            MdmLog.i(TAG, "▶ Ejecutando commandId=${command.commandId} tipo=${command.commandType}")

            val execResult = if (command.commandType in CommandType.REQUIRES_MAIN_THREAD) {
                withContext(Dispatchers.Main) {
                    executor.execute(command.commandType, command.parameters)
                }
            } else {
                withContext(Dispatchers.IO) {
                    executor.execute(command.commandType, command.parameters)
                }
            }

            // Actualizar contador
            if (execResult.success) {
                prefs.commandsExecuted++
            }

            // Reportar resultado al servidor
            val reportRequest = CommandResultRequest(
                commandId    = command.commandId,
                success      = execResult.success,
                resultJson   = execResult.resultJson,
                errorMessage = execResult.errorMessage
            )

            reportResult(token, reportRequest)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // REPORTAR RESULTADO
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun reportResult(token: String, request: CommandResultRequest) =
        withContext(Dispatchers.IO) {
            if (!networkMonitor.isConnected) {
                MdmLog.w(TAG, "Sin red. Encolando resultado para commandId=${request.commandId}")
                resultQueue.enqueue(request)
                return@withContext
            }

            when (val result = apiClient.reportCommandResult(token, request)) {
                is MdmResult.Success -> {
                    MdmLog.i(TAG, "✓ Resultado reportado para commandId=${request.commandId}")
                }
                is MdmResult.Failure -> {
                    MdmLog.e(TAG, "✗ Error reportando commandId=${request.commandId}: ${result.errorMessage}")
                    // Encolar para reintento
                    resultQueue.enqueue(request)
                }
            }
        }

    private suspend fun drainPendingResultQueue(token: String) = withContext(Dispatchers.IO) {
        val pending = resultQueue.dequeueAll()
        if (pending.isEmpty()) return@withContext

        MdmLog.i(TAG, "Enviando ${pending.size} resultado(s) pendiente(s) de la cola...")
        for (item in pending) {
            when (val result = apiClient.reportCommandResult(token, item)) {
                is MdmResult.Success -> MdmLog.i(TAG, "✓ Cola: resultado enviado para commandId=${item.commandId}")
                is MdmResult.Failure -> {
                    MdmLog.w(TAG, "✗ Cola: falló para commandId=${item.commandId}. Re-encolando.")
                    resultQueue.enqueue(item) // volver a encolar
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // HEARTBEAT
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun sendHeartbeat(token: String) = withContext(Dispatchers.IO) {
        val request = HeartbeatRequest(
            batteryLevel       = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageAvailableMB = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            kioskModeEnabled   = prefs.kioskModeEnabled,
            cameraDisabled     = prefs.cameraDisabled,
            ipAddress          = infoCollector.getLocalIpAddress()
        )

        when (val result = apiClient.heartbeat(token, request)) {
            is MdmResult.Success -> MdmLog.d(TAG, "♥ Heartbeat OK")
            is MdmResult.Failure -> MdmLog.w(TAG, "♥ Heartbeat fallido: ${result.errorMessage}")
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // NOTIFICACIÓN FOREGROUND
    // ════════════════════════════════════════════════════════════════════════════

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Canal principal del servicio
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.NOTIF_CHANNEL_SERVICE,
                "MDM Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description  = "MDM Agent corriendo en segundo plano"
                setShowBadge(false)
                enableVibration(false)
            }
        )

        // Canal para alertas de admin
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.NOTIF_CHANNEL_ALERTS,
                "MDM Alertas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas críticas del sistema MDM"
            }
        )
    }

    private fun buildNotification(text: String, isError: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_SERVICE)
            .setContentTitle("MDM Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setColor(if (isError) 0xFFFF4757.toInt() else 0xFF00D897.toInt())
            .build()
    }

    private fun updateNotification(text: String, isError: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.NOTIF_ID_SERVICE, buildNotification(text, isError))
    }

    // ════════════════════════════════════════════════════════════════════════════
    // UI BROADCAST
    // ════════════════════════════════════════════════════════════════════════════

    private fun broadcastUiUpdate(logMessage: String) {
        sendLocalBroadcast(Constants.ACTION_UPDATE_UI) {
            putExtra(Constants.EXTRA_LOG_MESSAGE, logMessage)
            putExtra(Constants.EXTRA_LAST_POLL, System.currentTimeMillis())
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // WAKELOCK
    // ════════════════════════════════════════════════════════════════════════════

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MdmClient::PollingWakeLock"
        ).also {
            it.acquire(10 * 60 * 1000L) // Máximo 10 minutos por adquisición
        }
        MdmLog.d(TAG, "WakeLock adquirido.")
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        MdmLog.d(TAG, "WakeLock liberado.")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // INIT
    // ════════════════════════════════════════════════════════════════════════════

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