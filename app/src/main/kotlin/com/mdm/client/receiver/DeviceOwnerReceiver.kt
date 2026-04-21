package com.mdm.client.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.mdm.client.core.MdmLog
import com.mdm.client.service.MdmPollingService

class DeviceOwnerReceiver : DeviceAdminReceiver() {

    private val TAG = "DeviceOwnerReceiver"

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        MdmLog.i(TAG, "✓ Device Admin HABILITADO.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        MdmLog.w(TAG, "⚠ Device Admin DESHABILITADO. La app perdió permisos de administración.")
        // En producción aquí podrías alertar al servidor de que se perdió el control
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Deshabilitar el administrador de dispositivos eliminará el control remoto MDM."
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        MdmLog.i(TAG, "Aprovisionamiento completado. Iniciando servicio MDM...")
        startService(context)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        MdmLog.i(TAG, "Entrando a Lock Task Mode. Paquete: $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        MdmLog.i(TAG, "Saliendo de Lock Task Mode.")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        MdmLog.i(TAG, "Contraseña del dispositivo cambiada.")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        MdmLog.w(TAG, "Intento fallido de contraseña.")
    }

    private fun startService(context: Context) {
        try {
            context.startForegroundService(Intent(context, MdmPollingService::class.java))
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error iniciando servicio: ${e.message}", e)
        }
    }
}
