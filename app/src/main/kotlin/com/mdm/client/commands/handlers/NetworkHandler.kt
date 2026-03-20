package com.mdm.client.commands.handlers

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.device.DeviceOwnerChecker

class NetworkHandler(private val context: Context) {

    private val TAG = "NetworkHandler"
    private val gson = Gson()
    private val checker = DeviceOwnerChecker(context)

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // ── ENABLE_WIFI / DISABLE_WIFI ────────────────────────────────────────────
    // Nota: En Android 10+ solo apps de sistema o Device Owner pueden hacerlo
    // usando setWifiEnabled via DevicePolicyManager o la API de sugerencias
    @Suppress("DEPRECATION")
    fun setWifiEnabled(enable: Boolean): ExecutionResult {
        return try {
            // Método 1: WifiManager directo (funciona en Android < 10 o Device Owner)
            val result = wifiManager.setWifiEnabled(enable)
            if (result) {
                MdmLog.i(TAG, "WiFi ${if (enable) "habilitado" else "deshabilitado"}")
                ExecutionResult.success("""{"wifiEnabled":$enable}""")
            } else {
                // Método 2: Para Android 10+ con Device Owner, usar Settings global
                if (checker.isDeviceOwner()) {
                    android.provider.Settings.Global.putInt(
                            context.contentResolver,
                            android.provider.Settings.Global.WIFI_ON,
                            if (enable) 1 else 0
                    )
                    ExecutionResult.success(
                            """{"wifiEnabled":$enable,"method":"settings_global"}"""
                    )
                } else {
                    ExecutionResult.failure(
                            "No se pudo cambiar el estado del WiFi. " +
                                    "En Android 10+ se requiere Device Owner."
                    )
                }
            }
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error WiFi: ${e.message}", e)
            ExecutionResult.failure("Error cambiando WiFi: ${e.message}")
        }
    }

    // ── SET_WIFI_CONFIG ───────────────────────────────────────────────────────
    // Agrega y conecta a una red WiFi específica (Device Owner requerido)
    @Suppress("DEPRECATION")
    fun setWifiConfig(parametersJson: String?): ExecutionResult {
        if (!checker.isDeviceOwner())
                return ExecutionResult.failure("Device Owner requerido para configurar WiFi.")

        return try {
            val params = gson.fromJson(parametersJson, Map::class.java)
            val ssid = params["ssid"] as? String ?: return ExecutionResult.failure("Falta 'ssid'.")
            val password = params["password"] as? String ?: ""
            val security = (params["security"] as? String)?.uppercase() ?: "WPA2"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: usar WifiNetworkSuggestion via DevicePolicyManager
                val suggestion =
                        android.net.wifi.WifiNetworkSuggestion.Builder()
                                .setSsid(ssid)
                                .apply { if (password.isNotEmpty()) setWpa2Passphrase(password) }
                                .build()

                val suggestions = listOf(suggestion)
                val status = wifiManager.addNetworkSuggestions(suggestions)

                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                    MdmLog.i(TAG, "Sugerencia WiFi agregada: $ssid")
                    ExecutionResult.success(
                            """{"ssid":"$ssid","method":"suggestion","status":"added"}"""
                    )
                } else {
                    ExecutionResult.failure("Error agregando sugerencia WiFi: status=$status")
                }
            } else {
                // Android < 10: WifiConfiguration clásico
                val config =
                        WifiConfiguration().apply {
                            SSID = "\"$ssid\""
                            when (security) {
                                "WPA", "WPA2" -> {
                                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                                    preSharedKey = "\"$password\""
                                }
                                "OPEN" -> {
                                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                                }
                                else -> {
                                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                                    preSharedKey = "\"$password\""
                                }
                            }
                        }
                val netId = wifiManager.addNetwork(config)
                if (netId != -1) {
                    wifiManager.enableNetwork(netId, true)
                    MdmLog.i(TAG, "Red WiFi configurada: $ssid (netId=$netId)")
                    ExecutionResult.success(
                            """{"ssid":"$ssid","netId":$netId,"status":"configured"}"""
                    )
                } else {
                    ExecutionResult.failure("No se pudo agregar la red WiFi '$ssid'.")
                }
            }
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error configurando WiFi: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    // ── ENABLE_BLUETOOTH / DISABLE_BLUETOOTH ──────────────────────────────────
    @Suppress("DEPRECATION", "MissingPermission")
    fun setBluetooth(enable: Boolean): ExecutionResult {
        return try {
            val btManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as
                            android.bluetooth.BluetoothManager
            val adapter =
                    btManager.adapter
                            ?: return ExecutionResult.failure(
                                    "Bluetooth no disponible en este dispositivo."
                            )

            val result = if (enable) adapter.enable() else adapter.disable()
            MdmLog.i(TAG, "Bluetooth ${if (enable) "habilitado" else "deshabilitado"}: $result")
            ExecutionResult.success("""{"bluetoothEnabled":$enable}""")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error Bluetooth: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}
