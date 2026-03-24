package com.mdm.client.commands.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import com.mdm.client.data.prefs.DevicePrefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class LocationTrackingHandler(private val context: Context) {

    private val TAG = "LocationTrackingHandler"
    private val gson = Gson()
    private val prefs = DevicePrefs(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    @Volatile private var isTracking = false

    @SuppressLint("MissingPermission")
    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val params = parametersJson?.let { gson.fromJson(it, Map::class.java) }
            val intervalSeconds = params?.get("intervalSeconds")?.toString()?.toLongOrNull() ?: 60L
            val minDistanceMeters =
                    params?.get("minDistanceMeters")?.toString()?.toFloatOrNull() ?: 10f

            if (isTracking) {
                return ExecutionResult.failure(
                        "El tracking ya está activo. Usa STOP_LOCATION_TRACK primero."
                )
            }

            fusedClient = LocationServices.getFusedLocationProviderClient(context)

            val request =
                    LocationRequest.Builder(
                                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                    TimeUnit.SECONDS.toMillis(intervalSeconds)
                            )
                            .apply {
                                setMinUpdateDistanceMeters(minDistanceMeters)
                                setWaitForAccurateLocation(false)
                            }
                            .build()

            locationCallback =
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { location ->
                                prefs.lastKnownLatitude = location.latitude
                                prefs.lastKnownLongitude = location.longitude
                                prefs.lastLocationAccuracy = location.accuracy
                                prefs.lastLocationTimestamp = location.time

                                MdmLog.i(
                                        TAG,
                                        "Loc: ${location.latitude},${location.longitude} ±${location.accuracy}m"
                                )
                            }
                        }
                    }

            locationCallback?.let { callback ->
                fusedClient?.requestLocationUpdates(request, callback, Looper.getMainLooper())
            }

            isTracking = true

            // Persistir estado para sobrevivir reinicios
            prefs.locationTrackingActive = true
            prefs.locationTrackingInterval = intervalSeconds

            ExecutionResult.success(
                    """{
                "trackingStarted": true,
                "intervalSeconds": $intervalSeconds,
                "minDistanceMeters": $minDistanceMeters
            }"""
            )
        } catch (e: SecurityException) {
            ExecutionResult.failure("Permiso de ubicación denegado.")
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error iniciando tracking: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }

    fun stopTracking(): ExecutionResult {
        return try {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            isTracking = false
            prefs.locationTrackingActive = false

            scope.cancel()
            ExecutionResult.success("""{"trackingStopped": true}""")
        } catch (e: Exception) {
            ExecutionResult.failure("Error deteniendo tracking: ${e.message}")
        }
    }

    companion object {
        // Singleton para mantener estado entre comandos
        @Volatile private var instance: LocationTrackingHandler? = null

        fun getInstance(context: Context): LocationTrackingHandler {
            return instance
                    ?: synchronized(this) {
                        instance
                                ?: LocationTrackingHandler(context.applicationContext).also {
                                    instance = it
                                }
                    }
        }
    }
}
