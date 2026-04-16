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

class MdmPollingService : Service() {

private lateinit var prefs: DevicePrefs
private lateinit var apiClient: ApiClient
private lateinit var registrar: RegistrationManager
private lateinit var executor: CommandExecutor

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var wsClient: MdmWebSocketClient
    private var isRunning = false
    private var isWsMode = false

    override fun onCreate() {
    super.onCreate()

    prefs = DevicePrefs(this)
    apiClient = ApiClient()
    registrar = RegistrationManager(this, apiClient, prefs)
    executor = CommandExecutor(this)

    startForeground(1, buildNotification("Iniciando..."))
    startWatchdog()
}

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!isRunning) {
            isRunning = true
            scope.launch { init() }
        }
        return START_STICKY
    }

    private suspend fun init() {
    val registered = registrar.ensureRegistered()

    if (!registered) {
        updateNotification("Error registro", true)
        delay(10000)
        init()
        return
    }

    connectWS()
}

    private fun connectWS() {

    val token = prefs.deviceToken ?: run {
        updateNotification("Sin token - registrando...", true)

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
            }

            override fun onDisconnected(reason: String) {
                isWsMode = false
                updateNotification("Fallback polling", true)
                startPolling()
            }

            override fun onCommand(msg: WsCommandMessage) {
                scope.launch { execute(msg) }
            }

            override fun onError(error: String) {
                updateNotification("Error WS", true)
            }
        })
    }
}

private suspend fun execute(cmd: WsCommandMessage) {
    val result = executor.execute(cmd.commandType, cmd.parameters)

    wsClient.sendResult(
        cmd.commandId,
        result.success,
        result.resultJson,
        result.errorMessage
    )
}

    private fun startPolling() {
        scope.launch {
            while (isRunning && !isWsMode) {
                delay(30_000)
            }
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isRunning && isWsMode) {
				wsClient.sendStatus(80, 1000L, false, false, "ip")
                delay(60_000)
            }
        }
    }

    private fun startWatchdog() {
        scope.launch {
            while (true) {
                delay(60_000)
                if (!isWsMode) connectWS()
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "mdm")
            .setContentTitle("MDM")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
    }

    private fun updateNotification(text: String, error: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}