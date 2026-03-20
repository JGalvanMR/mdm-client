package com.mdm.client.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.mdm.client.core.Constants
import com.mdm.client.core.MdmLog
import com.mdm.client.service.MdmPollingService
import com.mdm.client.worker.MdmSyncWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        MdmLog.i(TAG, "Boot/Update detectado ($action). Levantando servicios MDM...")

        // 1. Iniciar el ForegroundService inmediatamente
        try {
            context.startForegroundService(
                Intent(context, MdmPollingService::class.java)
            )
            MdmLog.i(TAG, "ForegroundService iniciado desde boot.")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error iniciando servicio en boot: ${e.message}", e)
        }

        // 2. Programar WorkManager como respaldo periódico (cada 15 min mínimo)
        scheduleWorkManager(context)
    }

    private fun scheduleWorkManager(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<MdmSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WORK_MDM_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )

        MdmLog.i(TAG, "WorkManager periódico programado.")
    }
}