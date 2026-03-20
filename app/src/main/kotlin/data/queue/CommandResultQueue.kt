package com.mdm.client.data.queue

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mdm.client.core.MdmLog
import com.mdm.client.data.models.CommandResultRequest

/**
 * Cola persistente de resultados de comandos para enviar cuando haya red.
 * Almacena los resultados pendientes en SharedPreferences como JSON array.
 * Si el dispositivo pierde red justo al reportar un resultado, no se pierde.
 */
class CommandResultQueue(context: Context) {

    private val TAG   = "CommandResultQueue"
    private val KEY   = "pending_results"
    private val gson  = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mdm_result_queue", Context.MODE_PRIVATE)

    private val listType = object : TypeToken<MutableList<CommandResultRequest>>() {}.type

    fun enqueue(result: CommandResultRequest) {
        val list = getAll().toMutableList()
        // Evitar duplicados por commandId
        list.removeAll { it.commandId == result.commandId }
        list.add(result)
        save(list)
        MdmLog.i(TAG, "Encolado resultado para commandId=${result.commandId}. Total en cola: ${list.size}")
    }

    fun dequeueAll(): List<CommandResultRequest> {
        val items = getAll()
        if (items.isNotEmpty()) {
            save(emptyList())
            MdmLog.i(TAG, "Desencolados ${items.size} resultados.")
        }
        return items
    }

    fun size(): Int = getAll().size

    fun remove(commandId: Int) {
        val list = getAll().toMutableList()
        list.removeAll { it.commandId == commandId }
        save(list)
    }

    private fun getAll(): List<CommandResultRequest> {
        return try {
            val json = prefs.getString(KEY, null) ?: return emptyList()
            gson.fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error leyendo cola: ${e.message}")
            emptyList()
        }
    }

    private fun save(list: List<CommandResultRequest>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }
}