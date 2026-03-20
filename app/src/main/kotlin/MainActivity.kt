package com.mdm.client

import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.mdm.client.core.Constants
import com.mdm.client.core.MdmLog
import com.mdm.client.core.toReadableDate
import com.mdm.client.data.prefs.DevicePrefs
import com.mdm.client.databinding.ActivityMainBinding
import com.mdm.client.device.DeviceOwnerChecker
import com.mdm.client.service.MdmPollingService

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var binding:  ActivityMainBinding
    private lateinit var prefs:    DevicePrefs
    private lateinit var checker:  DeviceOwnerChecker

    // Buffer de log para mostrar en pantalla
    private val logBuffer = ArrayDeque<String>(Constants.MAX_LOG_LINES)

    // ════════════════════════════════════════════════════════════════════════════
    // BROADCAST RECEIVER — recibe eventos del servicio y comandos de kiosk
    // ════════════════════════════════════════════════════════════════════════════
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                Constants.ACTION_START_KIOSK -> {
                    MdmLog.i(TAG, "Activando kiosk mode...")
                    startLockTask()
                    appendLog("✓ Kiosk mode ACTIVADO")
                    updateMdmStatusUI()
                }

                Constants.ACTION_STOP_KIOSK -> {
                    MdmLog.i(TAG, "Desactivando kiosk mode...")
                    try { stopLockTask() } catch (e: Exception) {
                        MdmLog.e(TAG, "Error deteniendo lockTask: ${e.message}")
                    }
                    appendLog("✓ Kiosk mode DESACTIVADO")
                    updateMdmStatusUI()
                }

                Constants.ACTION_UPDATE_UI -> {
                    val log    = intent.getStringExtra(Constants.EXTRA_LOG_MESSAGE)
                    val pollTs = intent.getLongExtra(Constants.EXTRA_LAST_POLL, 0L)

                    log?.let { appendLog(it) }
                    if (pollTs > 0) {
                        binding.tvLastPoll.text = "Último poll: ${pollTs.toReadableDate()}"
                    }
                    binding.tvCommandsExecuted.text =
                        "Comandos ejecutados: ${prefs.commandsExecuted}"
                    updateStatusDot(online = true)
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantener pantalla encendida (útil en dispositivos MDM kiosk)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs   = DevicePrefs(this)
        checker = DeviceOwnerChecker(this)

        setupUI()
        registerEventReceiver()
        startMdmService()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Restaurar kiosk si estaba activo (ej: después de que el servicio lo activó)
        if (prefs.kioskModeEnabled && checker.isDeviceOwner()) {
            try { startLockTask() } catch (e: Exception) {
                MdmLog.w(TAG, "No se pudo restaurar kiosk mode: ${e.message}")
            }
        }
        refreshAllStatus()
    }

    override fun onDestroy() {
        unregisterReceiver(eventReceiver)
        super.onDestroy()
    }

    // En kiosk mode, deshabilitar el botón Back para que no salga
    override fun onBackPressed() {
        if (prefs.kioskModeEnabled) {
            MdmLog.d(TAG, "Back ignorado en kiosk mode.")
            return
        }
        super.onBackPressed()
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SETUP UI
    // ════════════════════════════════════════════════════════════════════════════

    private fun setupUI() {
        // Versión
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        // Info del dispositivo
        binding.tvDeviceId.text     = "ID: ${prefs.getOrCreateDeviceId(this).take(16)}…"
        binding.tvDeviceModel.text  = "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}"
        binding.tvAndroidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // Scroll del log al final automáticamente
        binding.tvLog.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // no-op, el layout_gravity=bottom se encarga
        }
    }

    private fun refreshAllStatus() {
        updateDeviceOwnerStatusUI()
        updateMdmStatusUI()
        updateServiceStatusUI()
    }

    private fun updateDeviceOwnerStatusUI() {
        val isOwner = checker.isDeviceOwner()
        binding.tvDeviceOwnerStatus.apply {
            text = if (isOwner) "Device Owner: ✓ Activo" else "Device Owner: ✗ No configurado"
            setTextColor(if (isOwner) Color.parseColor("#00D897") else Color.parseColor("#FF4757"))
        }
    }

    private fun updateMdmStatusUI() {
        binding.tvRegistered.apply {
            val registered = prefs.isRegistered
            text = if (registered) "Registro: ✓ Completado" else "Registro: ⏳ Pendiente"
            setTextColor(if (registered) Color.parseColor("#00D897") else Color.parseColor("#FFD93D"))
        }

        val lastPoll = prefs.lastPollTimestamp
        binding.tvLastPoll.text = if (lastPoll > 0)
            "Último poll: ${lastPoll.toReadableDate()}"
        else
            "Último poll: —"

        binding.tvCommandsExecuted.text = "Comandos ejecutados: ${prefs.commandsExecuted}"

        binding.tvKioskStatus.apply {
            val k = prefs.kioskModeEnabled
            text = if (k) "Kiosk: ✓ Activo" else "Kiosk: Desactivado"
            setTextColor(if (k) Color.parseColor("#00D897") else Color.parseColor("#7B7F8E"))
        }

        binding.tvCameraStatus.apply {
            val d = prefs.cameraDisabled
            text = if (d) "Cámara: ✗ Deshabilitada" else "Cámara: ✓ Habilitada"
            setTextColor(if (d) Color.parseColor("#FF4757") else Color.parseColor("#00D897"))
        }
    }

    private fun updateServiceStatusUI() {
        binding.tvServiceStatus.text  = "Servicio activo (polling ${BuildConfig.POLL_INTERVAL_MS / 1000}s)"
        updateStatusDot(online = true)
    }

    private fun updateStatusDot(online: Boolean) {
        binding.viewStatusDot.background?.setTint(
            if (online) Color.parseColor("#00D897") else Color.parseColor("#FF4757")
        )
        binding.tvServiceStatus.setTextColor(
            if (online) Color.parseColor("#EAEAEA") else Color.parseColor("#FF4757")
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // LOG
    // ════════════════════════════════════════════════════════════════════════════

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$timestamp] $message"

        logBuffer.addLast(entry)
        if (logBuffer.size > Constants.MAX_LOG_LINES) logBuffer.removeFirst()

        runOnUiThread {
            binding.tvLog.text = logBuffer.joinToString("\n")
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SERVICE
    // ════════════════════════════════════════════════════════════════════════════

    private fun startMdmService() {
        val intent = Intent(this, MdmPollingService::class.java)
        startForegroundService(intent)
        MdmLog.i(TAG, "MdmPollingService iniciado desde MainActivity.")
        appendLog("Servicio MDM iniciado.")
    }

    // ════════════════════════════════════════════════════════════════════════════
    // RECEIVERS
    // ════════════════════════════════════════════════════════════════════════════

    private fun registerEventReceiver() {
        val filter = IntentFilter().apply {
            addAction(Constants.ACTION_START_KIOSK)
            addAction(Constants.ACTION_STOP_KIOSK)
            addAction(Constants.ACTION_UPDATE_UI)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventReceiver, filter)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PERMISOS
    // ════════════════════════════════════════════════════════════════════════════

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }
}