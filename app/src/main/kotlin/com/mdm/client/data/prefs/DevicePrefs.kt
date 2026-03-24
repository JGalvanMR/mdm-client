package com.mdm.client.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mdm.client.core.Constants
import com.mdm.client.core.MdmLog

class DevicePrefs(context: Context) {

    private val TAG = "DevicePrefs"

    // ── SharedPreferences cifradas con AES-256 ────────────────────────────────
    private val prefs: SharedPreferences =
            try {
                val masterKey =
                        MasterKey.Builder(context)
                                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                .build()
                EncryptedSharedPreferences.create(
                        context,
                        Constants.PREF_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                MdmLog.e(
                        TAG,
                        "Error inicializando EncryptedSharedPreferences: ${e.message}. Usando prefs normales."
                )
                context.getSharedPreferences(
                        Constants.PREF_FILE + "_fallback",
                        Context.MODE_PRIVATE
                )
            }

    // ── Token de autenticación con el servidor ────────────────────────────────
    var deviceToken: String?
        get() = prefs.getString(Constants.KEY_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.KEY_TOKEN, value).apply()

    // ── ID único del dispositivo ──────────────────────────────────────────────
    fun getOrCreateDeviceId(context: Context): String {
        var id = prefs.getString(Constants.KEY_DEVICE_ID, null)
        if (!id.isNullOrBlank()) return id

        // ANDROID_ID es el más confiable en Device Owner
        val androidId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        // El valor "9774d56d682e549c" es el androidId por defecto en emuladores = inválido
        id =
                if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                    androidId
                } else {
                    java.util.UUID.randomUUID().toString().replace("-", "")
                }

        prefs.edit().putString(Constants.KEY_DEVICE_ID, id).apply()
        MdmLog.i(TAG, "DeviceId generado: ${id.take(8)}****")
        return id
    }

    var isRegistered: Boolean
        get() = prefs.getBoolean(Constants.KEY_IS_REGISTERED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_IS_REGISTERED, value).apply()

    var kioskModeEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_KIOSK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_KIOSK_ENABLED, value).apply()

    var cameraDisabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_CAMERA_DISABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_CAMERA_DISABLED, value).apply()

    var commandsExecuted: Int
        get() = prefs.getInt(Constants.KEY_COMMANDS_EXECUTED, 0)
        set(value) = prefs.edit().putInt(Constants.KEY_COMMANDS_EXECUTED, value).apply()

    var lastPollTimestamp: Long
        get() = prefs.getLong(Constants.KEY_LAST_POLL_TS, 0L)
        set(value) = prefs.edit().putLong(Constants.KEY_LAST_POLL_TS, value).apply()

    var registrationRetryCount: Int
        get() = prefs.getInt(Constants.KEY_REGISTRATION_RETRY, 0)
        set(value) = prefs.edit().putInt(Constants.KEY_REGISTRATION_RETRY, value).apply()

    // Location tracking
    var lastKnownLatitude: Double
        get() = prefs.getFloat("last_lat", 0f).toDouble()
        set(value) = prefs.edit().putFloat("last_lat", value.toFloat()).apply()

    var lastKnownLongitude: Double
        get() = prefs.getFloat("last_lng", 0f).toDouble()
        set(value) = prefs.edit().putFloat("last_lng", value.toFloat()).apply()

    var lastLocationAccuracy: Float
        get() = prefs.getFloat("last_acc", 0f)
        set(value) = prefs.edit().putFloat("last_acc", value).apply()

    var lastLocationTimestamp: Long
        get() = prefs.getLong("last_loc_ts", 0L)
        set(value) = prefs.edit().putLong("last_loc_ts", value).apply()

    var locationTrackingActive: Boolean
        get() = prefs.getBoolean("loc_tracking", false)
        set(value) = prefs.edit().putBoolean("loc_tracking", value).apply()

    var locationTrackingInterval: Long
        get() = prefs.getLong("loc_interval", 60L)
        set(value) = prefs.edit().putLong("loc_interval", value).apply()

    fun clearSession() {
        prefs.edit()
                .remove(Constants.KEY_TOKEN)
                .remove(Constants.KEY_IS_REGISTERED)
                .remove(Constants.KEY_REGISTRATION_RETRY)
                .apply()
        MdmLog.w(TAG, "Sesión eliminada. El dispositivo necesitará re-registrarse.")
    }
}
