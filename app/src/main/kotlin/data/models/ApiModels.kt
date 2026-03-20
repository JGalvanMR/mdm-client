package com.mdm.client.data.models

import com.google.gson.annotations.SerializedName

// ════════════════════════════════════════════════════════════════
// REGISTER
// ════════════════════════════════════════════════════════════════
data class RegisterDeviceRequest(
    @SerializedName("deviceId")       val deviceId:       String,
    @SerializedName("deviceName")     val deviceName:     String?,
    @SerializedName("model")          val model:          String?,
    @SerializedName("manufacturer")   val manufacturer:   String?,
    @SerializedName("androidVersion") val androidVersion: String?,
    @SerializedName("apiLevel")       val apiLevel:       Int?
)

data class RegisterDeviceResponse(
    @SerializedName("success")  val success:  Boolean,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("token")    val token:    String,
    @SerializedName("message")  val message:  String
)

// ════════════════════════════════════════════════════════════════
// POLL
// ════════════════════════════════════════════════════════════════
data class PollRequest(
    @SerializedName("batteryLevel")       val batteryLevel:       Int?,
    @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
    @SerializedName("ipAddress")          val ipAddress:          String?
)

data class PollCommand(
    @SerializedName("commandId")   val commandId:   Int,
    @SerializedName("commandType") val commandType: String,
    @SerializedName("parameters")  val parameters:  String?
)

data class PollResponse(
    @SerializedName("success")    val success:    Boolean,
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("commands")   val commands:   List<PollCommand>
)

// ════════════════════════════════════════════════════════════════
// COMMAND RESULT
// ════════════════════════════════════════════════════════════════
data class CommandResultRequest(
    @SerializedName("commandId")    val commandId:    Int,
    @SerializedName("success")      val success:      Boolean,
    @SerializedName("resultJson")   val resultJson:   String?,
    @SerializedName("errorMessage") val errorMessage: String?
)

data class CommandResultResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// ════════════════════════════════════════════════════════════════
// HEARTBEAT
// ════════════════════════════════════════════════════════════════
data class HeartbeatRequest(
    @SerializedName("batteryLevel")       val batteryLevel:       Int?,
    @SerializedName("storageAvailableMB") val storageAvailableMB: Long?,
    @SerializedName("kioskModeEnabled")   val kioskModeEnabled:   Boolean,
    @SerializedName("cameraDisabled")     val cameraDisabled:     Boolean,
    @SerializedName("ipAddress")          val ipAddress:          String?
)

data class HeartbeatResponse(
    @SerializedName("success")    val success:    Boolean,
    @SerializedName("serverTime") val serverTime: String
)

// ════════════════════════════════════════════════════════════════
// DEVICE INFO
// ════════════════════════════════════════════════════════════════
data class DeviceInfoPayload(
    @SerializedName("deviceId")           val deviceId:           String,
    @SerializedName("model")              val model:              String,
    @SerializedName("manufacturer")       val manufacturer:       String,
    @SerializedName("androidVersion")     val androidVersion:     String,
    @SerializedName("apiLevel")           val apiLevel:           Int,
    @SerializedName("batteryLevel")       val batteryLevel:       Int,
    @SerializedName("storageAvailableMB") val storageAvailableMB: Long,
    @SerializedName("totalStorageMB")     val totalStorageMB:     Long,
    @SerializedName("kioskModeEnabled")   val kioskModeEnabled:   Boolean,
    @SerializedName("cameraDisabled")     val cameraDisabled:     Boolean
)

// ════════════════════════════════════════════════════════════════
// ERROR envelope genérico del servidor
// ════════════════════════════════════════════════════════════════
data class ApiError(
    @SerializedName("error")   val error:   String?,
    @SerializedName("message") val message: String?
)