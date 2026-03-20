package com.mdm.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
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
import com.mdm.client.data.models.*
import com.mdm.client.data.network.ApiClient
import com.mdm.client.data.network.NetworkMonitor
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.data.queue.CommandResultQueue
import com.mdm.client.device.DeviceInfoCollector
import kotlinx.coroutines.*

class MdmPollingService : Service() {

    private val TAG = "MdmPollingService"

    // ── Dependencias ───────────────────────────────────────────────────────────
    private lateinit var prefs:          DevicePrefs
    private lateinit var apiClient:      ApiClient
    private lateinit var executor:       CommandExecutor
    private lateinit var registrar:      RegistrationManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var infoCollector:  DeviceInfoCollector
    private lateinit var resultQueue:    CommandResultQueue

    // ── Coroutines ────────────────────────────────────────────────────────────
    private val serviceJob = SupervisorJob()
    private val scope       = CoroutineScope(Dispatchers.IO + serviceJob)

    // ── WakeLock ──
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
        MdmLog.w(TAG, "MdmPollingService destruido.")
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
        MdmLog.i(TAG, "Loop iniciado. Intervalo: ${BuildConfig.POLL_INTERVAL_MS}ms")
        while (isRunning && scope.isActive) {
            try {
                executeCycle()
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                MdmLog.e(TAG, "Error en ciclo: ${e.message}")
            }
            delay(BuildConfig.POLL_INTERVAL_MS)
        }
    }

    private suspend fun executeCycle() {
        pollCycleCount++
        if (!networkMonitor.isConnected) {
            updateNotification("Sin red. Esperando...", isError = true)
            return
        }

        val registered = withContext(Dispatchers.IO) { registrar.ensureRegistered() }
        if (!registered) return

        val token = prefs.deviceToken ?: return

        drainPendingResultQueue(token)

        if (pollCycleCount % 2 == 0) {
            sendHeartbeat(token)
        }

        doPoll(token)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // POLL
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun doPoll(token: String) = withContext(Dispatchers.IO) {
        val pollRequest = PollRequest(
            batteryLevel = infoCollector.getBatteryLevel().takeIf { it >= 0 },
            storageAvailableMB = infoCollector.getAvailableStorageMB().takeIf { it >= 0 },
            ipAddress = infoCollector.getLocalIpAddress(),
            kioskModeEnabled = prefs.kioskModeEnabled,
            cameraDisabled = prefs.cameraDisabled
        )

        when (val result = apiClient.poll(token, pollRequest)) {
            is MdmResult.Success -> {
                val response = result.data
                prefs.lastPollTimestamp = System.currentTimeMillis()
                lastPollSuccess = true

                val commands = response.commands
                if (commands.isNotEmpty()) {
                    MdmLog.i(TAG, "✓ Poll OK: ${commands.size} comandos.")
                    executeCommands(token, commands)
                } else {
                    updateNotification("Esperando comandos...")
                }
                broadcastUiUpdate("Poll OK — ${commands.size} cmds")
            }
            is MdmResult.Failure -> {
                MdmLog.e(TAG, "✗ Poll fallido: ${result.errorMessage}")
                if (result.errorCode == 401) prefs.clearSession()
            }
        }
    }

    private suspend fun executeCommands(token: String, commands: List<PollCommand>) {
        val sorted = commands.sortedBy { it.priority }
        for (command in sorted) {
            MdmLog.i(TAG, "▶ [P${command.priority}] Ejecutando ${command.commandType}")

            val execResult = if (command.commandType in CommandType.REQUIRES_MAIN_THREAD) {
                withContext(Dispatchers.Main) { executor.execute(command.commandType, command.parameters) }
            } else {
                withContext(Dispatchers.IO) { executor.execute(command.commandType, command.parameters) }
            }

            if (execResult.success) prefs.commandsExecuted++

            val report = CommandResultRequest(
                commandId = command.commandId,
                success = execResult.success,
                resultJson = execResult.resultJson,
                errorMessage = execResult.errorMessage
            )
            reportResult(token, report)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // AUXILIARES
    // ════════════════════════════════════════════════════════════════════════════

    private suspend fun reportResult(token: String, request: CommandResultRequest) {
        if (!networkMonitor.isConnected) {
            resultQueue.enqueue(request)
            return
        }
        apiClient.reportCommandResult(token, request)
    }

    private suspend fun drainPendingResultQueue(token: String) {
        val pending = resultQueue.dequeueAll()
        for (item in pending) {
            if (apiClient.reportCommandResult(token, item) is MdmResult.Failure) {
                resultQueue.enqueue(item)
            }
        }
    }

    private suspend fun sendHeartbeat(token: String) {
        val req = HeartbeatRequest(
            batteryLevel = infoCollector.getBatteryLevel(),
            storageAvailableMB = infoCollector.getAvailableStorageMB(),
            kioskModeEnabled = prefs.kioskModeEnabled,
            cameraDisabled = prefs.cameraDisabled,
            ipAddress = infoCollector.getLocalIpAddress()
        )
        apiClient.heartbeat(token, req)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            Constants.NOTIF_CHANNEL_SERVICE, "MDM Service", NotificationManager.IMPORTANCE_LOW
        ))
    }

    private fun buildNotification(text: String, isError: Boolean = false): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_SERVICE)
            .setContentTitle("MDM Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pi)
            .setColor(if (isError) 0xFFFF4757.toInt() else 0xFF00D897.toInt())
            .build()
    }

    private fun updateNotification(text: String, isError: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.NOTIF_ID_SERVICE, buildNotification(text, isError))
    }

    private fun broadcastUiUpdate(logMessage: String) {
        sendLocalBroadcast(Constants.ACTION_UPDATE_UI) {
            putExtra(Constants.EXTRA_LOG_MESSAGE, logMessage)
            putExtra(Constants.EXTRA_LAST_POLL, System.currentTimeMillis())
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mdm::WakeLock").apply { acquire(10 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun initDependencies() {
        prefs = DevicePrefs(this)
        apiClient = ApiClient()
        executor = CommandExecutor(this)
        networkMonitor = NetworkMonitor(this)
        infoCollector = DeviceInfoCollector(this)
        resultQueue = CommandResultQueue(this)
        registrar = RegistrationManager(this, apiClient, prefs)
    }
}