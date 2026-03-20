package com.mdm.client.core

/**
 * Resultado de ejecución de comandos MDM
 */
data class ExecutionResult(
    val success: Boolean,
    val resultJson: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(json: String? = null) = ExecutionResult(true, resultJson = json)
        fun failure(error: String) = ExecutionResult(false, errorMessage = error)
    }
}