package com.mdm.client.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mdm.client.core.MdmLog
import com.mdm.client.service.MdmPollingService

/**
 * WorkManager como mecanismo de respaldo.
 * Si el ForegroundService muere y Android no lo reinicia inmediatamente,
 * WorkManager garantiza que el servicio se vuelva a levantar.
 *
 * Programado como trabajo periódico de 15 minutos (mínimo de WorkManager).
 */
class MdmSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "MdmSyncWorker"

    override suspend fun doWork(): Result {
        MdmLog.i(TAG, "WorkManager: verificando si el servicio está activo...")

        // Reiniciar el servicio si no está corriendo
        try {
            val intent = Intent(context, MdmPollingService::class.java)
            context.startForegroundService(intent)
            MdmLog.i(TAG, "WorkManager: servicio reiniciado.")
        } catch (e: Exception) {
            MdmLog.e(TAG, "WorkManager: error reiniciando servicio: ${e.message}", e)
            return Result.retry()
        }

        return Result.success()
    }
}