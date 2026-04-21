package com.mdm.client.data.models

import com.google.gson.annotations.SerializedName

// ════════════════════════════════════════════════════════════════
// REGISTER — data: RegisterDeviceResponse
// ════════════════════════════════════════════════════════════════
data class RegisterDeviceRequest(
        @SerializedName("deviceId") val deviceId: String,
        @SerializedName("deviceName") val deviceName: String?,
        @SerializedName("model") val model: String?,
        @SerializedName("manufacturer") val manufacturer: String?,
        @SerializedName("androidVersion") val androidVersion: String?,
        @SerializedName("apiLevel") val apiLevel: Int?
)

// Refleja MDMServer.DTOs.Device.RegisterDeviceResponse
data class RegisterDeviceResponse(
        @SerializedName("deviceId") val deviceId: String,
        @SerializedName("token") val token: String,
        @SerializedName("message") val message: String,
        @SerializedName("isNewRegistration") val isNewRegistration: Boolean
)

// ════════════════════════════════════════════════════════════════
// POLL — data: PollResponse
// ════════════════════════════════════════════════════════════════
data class PollRequest(
        @SerializedName("batteryLevel") val batteryLevel: Int?,
        @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
        @SerializedName("ipAddress") val ipAddress: String?,
        @SerializedName("kioskModeEnabled") val kioskModeEnabled: Boolean? = null,
        @SerializedName("cameraDisabled") val cameraDisabled: Boolean? = null
)

// Refleja MDMServer.DTOs.Poll.PollCommandDto
data class PollCommand(
        @SerializedName("commandId") val commandId: Int,
        @SerializedName("commandType") val commandType: String,
        @SerializedName("parameters") val parameters: String?,
        @SerializedName("priority") val priority: Int = 5 // ← faltaba
)

// Refleja MDMServer.DTOs.Poll.PollResponse
data class PollResponse(
        @SerializedName("serverTime") val serverTime: String,
        @SerializedName("commands") val commands: List<PollCommand>,
        @SerializedName("pendingAfter") val pendingAfter: Int = 0 // ← faltaba
)

// ════════════════════════════════════════════════════════════════
// COMMAND RESULT
// ════════════════════════════════════════════════════════════════
data class CommandResultRequest(
        @SerializedName("commandId") val commandId: Int,
        @SerializedName("success") val success: Boolean,
        @SerializedName("resultJson") val resultJson: String?,
        @SerializedName("errorMessage") val errorMessage: String?
)

// ════════════════════════════════════════════════════════════════
// HEARTBEAT
// ════════════════════════════════════════════════════════════════
data class HeartbeatRequest(
        @SerializedName("batteryLevel") val batteryLevel: Int?,
        @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
        @SerializedName("kioskModeEnabled") val kioskModeEnabled: Boolean,
        @SerializedName("cameraDisabled") val cameraDisabled: Boolean,
        @SerializedName("ipAddress") val ipAddress: String?
)

// ════════════════════════════════════════════════════════════════
// DEVICE INFO
// ════════════════════════════════════════════════════════════════
data class DeviceInfoPayload(
        @SerializedName("deviceId") val deviceId: String,
        @SerializedName("model") val model: String,
        @SerializedName("manufacturer") val manufacturer: String,
        @SerializedName("androidVersion") val androidVersion: String,
        @SerializedName("apiLevel") val apiLevel: Int,
        @SerializedName("batteryLevel") val batteryLevel: Int,
        @SerializedName("storageAvailableMB") val storageAvailableMB: Long,
        @SerializedName("totalStorageMB") val totalStorageMB: Long,
        @SerializedName("kioskModeEnabled") val kioskModeEnabled: Boolean,
        @SerializedName("cameraDisabled") val cameraDisabled: Boolean
)
