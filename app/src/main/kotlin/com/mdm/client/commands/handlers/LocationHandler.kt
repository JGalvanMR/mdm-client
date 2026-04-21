package com.mdm.client.commands.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class LocationHandler(private val context: Context) {

    private val TAG = "LocationHandler"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun execute(parametersJson: String?): ExecutionResult {
        return runBlocking(Dispatchers.IO) {
            try {
                val timeoutSeconds = parseTimeout(parametersJson)
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val lm =
                        context.getSystemService(Context.LOCATION_SERVICE) as
                                android.location.LocationManager

                val gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val netEnabled =
                        lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

                if (!gpsEnabled && !netEnabled) {
                    return@runBlocking ExecutionResult.failure(
                            "GPS y red de localización deshabilitados."
                    )
                }

                // 1. Intentar última ubicación conocida (ahora síncrono con .await())
                val lastLocation =
                        try {
                            withTimeoutOrNull(3000) { // 3 segundos max para lastLocation
                                fusedClient.lastLocation.await()
                            }
                        } catch (e: Exception) {
                            MdmLog.w(TAG, "LastLocation error: ${e.message}")
                            null
                        }

                // Si es reciente (< 2 min), úsala
                if (lastLocation != null &&
                                (System.currentTimeMillis() - lastLocation.time) < 120000
                ) {
                    MdmLog.i(
                            TAG,
                            "Usando lastLocation: ${lastLocation.latitude},${lastLocation.longitude}"
                    )
                    return@runBlocking successResult(lastLocation)
                }

                // 2. Si no hay cache reciente, solicitar ubicación fresh
                MdmLog.i(TAG, "Solicitando ubicación fresh (timeout: ${timeoutSeconds}s)...")

                val freshLocation =
                        withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds.toLong())) {
                            getFreshLocation(fusedClient)
                        }

                if (freshLocation != null) {
                    successResult(freshLocation)
                } else {
                    ExecutionResult.success(
                            """{
                        "error":"Timeout esperando ubicación GPS. Intenta en exterior.",
                        "gpsEnabled":$gpsEnabled,
                        "networkEnabled":$netEnabled,
                        "hint":"El dispositivo necesita vista directa al cielo para primer fix GPS"
                    }"""
                    )
                }
            } catch (e: SecurityException) {
                ExecutionResult.failure("Permiso ACCESS_FINE_LOCATION denegado.")
            } catch (e: Exception) {
                MdmLog.e(TAG, "Error: ${e.message}", e)
                ExecutionResult.failure("Error obteniendo ubicación: ${e.message}")
            }
        }
    }

    private suspend fun getFreshLocation(fusedClient: FusedLocationProviderClient): Location? {
        return suspendCancellableCoroutine { continuation ->
            val request =
                    LocationRequest.Builder(
                                    Priority.PRIORITY_HIGH_ACCURACY,
                                    TimeUnit.SECONDS.toMillis(1)
                            )
                            .apply {
                                setWaitForAccurateLocation(false)
                                setMinUpdateIntervalMillis(500)
                            }
                            .build()

            val callback =
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { location ->
                                MdmLog.i(
                                        TAG,
                                        "Fresh location recibida: ${location.latitude},${location.longitude}"
                                )
                                continuation.resume(location)
                            }
                                    ?: run { continuation.resume(null) }
                            fusedClient.removeLocationUpdates(this)
                        }
                    }

            try {
                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: Exception) {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
        }
    }

    private fun parseTimeout(params: String?): Int {
        return try {
            params?.let {
                gson.fromJson(it, Map::class.java)["timeoutSeconds"]?.toString()?.toIntOrNull()
            }
                    ?: 15
        } catch (e: Exception) {
            15
        }
    }

    private fun successResult(loc: Location): ExecutionResult {
        val json =
                gson.toJson(
                        mapOf(
                                "latitude" to loc.latitude,
                                "longitude" to loc.longitude,
                                "accuracy" to loc.accuracy,
                                "provider" to loc.provider,
                                "timestamp" to loc.time,
                                "ageSeconds" to ((System.currentTimeMillis() - loc.time) / 1000)
                        )
                )
        return ExecutionResult.success(json)
    }
}
