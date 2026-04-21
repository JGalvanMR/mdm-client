package com.mdm.client

import android.app.Application
import androidx.work.*
import com.mdm.client.core.MdmLog

class App : Application(), Configuration.Provider {

    private val TAG = "MdmApp"

    override fun onCreate() {
        super.onCreate()

        MdmLog.i(TAG, "═══════════════════════════════════════")
        MdmLog.i(TAG, "  MDM Client v${BuildConfig.VERSION_NAME} iniciando")
        MdmLog.i(TAG, "  Servidor: ${BuildConfig.SERVER_URL}")
        MdmLog.i(TAG, "  Poll interval: ${BuildConfig.POLL_INTERVAL_MS}ms")
        MdmLog.i(TAG, "═══════════════════════════════════════")
    }

    // WorkManager con configuración personalizada (inicialización manual)
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
