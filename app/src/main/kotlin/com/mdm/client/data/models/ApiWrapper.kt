package com.mdm.client.data.models

import com.google.gson.annotations.SerializedName

/**
 * Wrapper que refleja el ApiResponse<T> del servidor. TODOS los endpoints retornan esta estructura.
 */
data class ApiWrapper<T>(
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("data") val data: T? = null,
        @SerializedName("error") val error: String? = null,
        @SerializedName("message") val message: String? = null,
        @SerializedName("timestamp") val timestamp: String? = null,
        @SerializedName("requestId") val requestId: String? = null
)
