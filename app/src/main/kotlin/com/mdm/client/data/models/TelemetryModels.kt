// app/src/main/kotlin/com/mdm/client/data/models/TelemetryModels.kt
package com.mdm.client.data.models

import com.google.gson.annotations.SerializedName

data class TelemetryReport(
        @SerializedName("batteryLevel") val batteryLevel: Int?,
        @SerializedName("batteryCharging") val batteryCharging: Boolean,
        @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
        @SerializedName("totalStorageMB") val totalStorageMB: Long?,
        @SerializedName("latitude") val latitude: Double?,
        @SerializedName("longitude") val longitude: Double?,
        @SerializedName("locationAccuracy") val locationAccuracy: Float?,
        @SerializedName("locationAgeSeconds") val locationAgeSeconds: Long?,
        @SerializedName("connectionType") val connectionType: String?, // WIFI|CELLULAR|NONE
        @SerializedName("ssid") val ssid: String?,
        @SerializedName("signalStrength") val signalStrength: Int?,
        @SerializedName("ipAddress") val ipAddress: String?,
        @SerializedName("kioskModeEnabled") val kioskModeEnabled: Boolean,
        @SerializedName("cameraDisabled") val cameraDisabled: Boolean,
        @SerializedName("screenOn") val screenOn: Boolean,
        @SerializedName("uptimeHours") val uptimeHours: Long,
        @SerializedName("ramUsedMB") val ramUsedMB: Long?,
        @SerializedName("cpuTemp") val cpuTemp: Float?,
        @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
