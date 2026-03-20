package com.mdm.client.core

/**
 * Tipo suma que encapsula éxito o fallo en todas las operaciones MDM.
 * Evita excepciones como flujo de control.
 */
sealed class MdmResult<out T> {

    data class Success<T>(val data: T) : MdmResult<T>()

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null,
        val errorCode: Int = -1
    ) : MdmResult<Nothing>()

    val isSuccess get() = this is Success
    val isFailure get() = this is Failure

    fun getOrNull(): T? = if (this is Success) data else null

    fun getErrorOrNull(): String? = if (this is Failure) errorMessage else null

    inline fun onSuccess(block: (T) -> Unit): MdmResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (String, Throwable?) -> Unit): MdmResult<T> {
        if (this is Failure) block(errorMessage, cause)
        return this
    }

    companion object {
        fun <T> of(block: () -> T): MdmResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(e.message ?: "Error desconocido", e)
            }
        }

        suspend fun <T> ofSuspend(block: suspend () -> T): MdmResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(e.message ?: "Error desconocido", e)
            }
        }
    }
}