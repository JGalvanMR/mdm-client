// app/src/main/kotlin/com/mdm/client/data/queue/CommandResultQueue.kt
package com.mdm.client.data.queue

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdm.client.core.MdmLog
import com.mdm.client.data.models.CommandResultRequest

data class QueuedResult(
    val request:    CommandResultRequest,
    val retryCount: Int  = 0,
    val enqueuedAt: Long = System.currentTimeMillis()
)

class CommandResultQueue(context: Context) {

    private val TAG       = "CommandResultQueue"
    private val KEY       = "pending_results"
    private val MAX_RETRY = 5
    private val MAX_AGE_MS = 24L * 60 * 60 * 1000   // 24 horas
    private val gson      = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mdm_result_queue", Context.MODE_PRIVATE)
    private val listType = object : TypeToken<MutableList<QueuedResult>>() {}.type

    fun enqueue(result: CommandResultRequest, currentRetry: Int = 0) {
        if (currentRetry >= MAX_RETRY) {
            MdmLog.w(TAG, "commandId=${result.commandId} descartado tras $MAX_RETRY reintentos.")
            return
        }
        val list = getAll().toMutableList()
        list.removeAll { it.request.commandId == result.commandId }
        list.add(QueuedResult(request = result, retryCount = currentRetry))
        save(list)
        MdmLog.i(TAG, "Encolado commandId=${result.commandId} retry=$currentRetry. Cola: ${list.size}")
    }

    fun dequeueAll(): List<QueuedResult> {
        val now   = System.currentTimeMillis()
        val all   = getAll()
        val valid = all.filter { it.retryCount < MAX_RETRY && (now - it.enqueuedAt) < MAX_AGE_MS }
        val dropped = all.size - valid.size
        if (dropped > 0) {
            MdmLog.w(TAG, "Descartados $dropped resultados expirados/agotados.")
            save(valid)
        }
        return valid
    }

    fun markSuccess(commandId: Int) {
        val list = getAll().toMutableList()
        list.removeAll { it.request.commandId == commandId }
        save(list)
        MdmLog.d(TAG, "Removido commandId=$commandId de la cola.")
    }

    fun size(): Int = getAll().size

    // Block body: permite usar 'return' dentro del try
    private fun getAll(): List<QueuedResult> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error leyendo cola: ${e.message}")
            emptyList()
        }
    }

    private fun save(list: List<QueuedResult>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }
}