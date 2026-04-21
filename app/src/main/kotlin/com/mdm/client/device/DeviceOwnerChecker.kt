package com.mdm.client.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.mdm.client.core.MdmLog
import com.mdm.client.receiver.DeviceOwnerReceiver

class DeviceOwnerChecker(private val context: Context) {

    private val TAG = "DeviceOwnerChecker"

    private val dpmManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    val adminComponent: ComponentName by lazy {
        ComponentName(context, DeviceOwnerReceiver::class.java)
    }

    fun isDeviceOwner(): Boolean = dpmManager.isDeviceOwnerApp(context.packageName)

    fun isDeviceAdmin(): Boolean = dpmManager.isAdminActive(adminComponent)

    /**
     * Diagnóstico completo del estado de permisos. Retorna lista de problemas encontrados (vacía =
     * todo OK).
     */
    fun diagnose(): List<String> {
        val issues = mutableListOf<String>()

        if (!isDeviceAdmin()) {
            issues.add(
                    "NO es Device Admin. El componente ${adminComponent.className} no está activo."
            )
        }

        if (!isDeviceOwner()) {
            issues.add(
                    "NO es Device Owner. Ejecuta:\n" +
                            "adb shell dpmManager set-device-owner ${context.packageName}/.receiver.DeviceOwnerReceiver"
            )
        }

        if (!isDeviceAdmin() && !isDeviceOwner()) {
            issues.add(
                    "ADVERTENCIA CRÍTICA: Sin permisos de administración. Los comandos MDM fallarán."
            )
        }

        if (issues.isEmpty()) {
            MdmLog.i(TAG, "✓ Dispositivo configurado correctamente como Device Owner.")
        } else {
            issues.forEach { MdmLog.e(TAG, "⚠ $it") }
        }

        return issues
    }

    fun getDpm(): DevicePolicyManager = dpmManager
}
