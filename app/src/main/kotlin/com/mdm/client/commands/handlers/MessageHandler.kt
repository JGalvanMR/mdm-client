// commands/handlers/MessageHandler.kt
package com.mdm.client.commands.handlers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.core.MdmLog

class MessageHandler(private val context: Context) {

    private val TAG  = "MessageHandler"
    private val gson = Gson()
    // ID único que incremente para no reemplazar mensajes anteriores
    private var notifCounter = 1000

    fun execute(parametersJson: String?): ExecutionResult {
        return try {
            val params = gson.fromJson(parametersJson, Map::class.java)
            val title  = params["title"] as? String ?: "Mensaje del administrador"
            val body   = params["body"]  as? String
                ?: return ExecutionResult.failure("Falta parámetro 'body'.")
            val urgent = (params["urgent"] as? Boolean) ?: false

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal de alta prioridad
            nm.createNotificationChannel(
                NotificationChannel(
                    "mdm_admin_messages",
                    "Mensajes del Administrador",
                    if (urgent) NotificationManager.IMPORTANCE_HIGH
                    else         NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Mensajes enviados por el administrador MDM"
                    enableVibration(urgent)
                }
            )

            val notif = NotificationCompat.Builder(context, "mdm_admin_messages")
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(
                    if (urgent) NotificationCompat.PRIORITY_MAX
                    else         NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .apply { if (urgent) setFullScreenIntent(null, true) }
                .build()

            nm.notify(notifCounter++, notif)
            MdmLog.i(TAG, "Mensaje mostrado: '$title'")
            ExecutionResult.success(
                """{"delivered":true,"title":"${title.replace("\"","'")}","urgent":$urgent}"""
            )
        } catch (e: Exception) {
            MdmLog.e(TAG, "Error enviando mensaje: ${e.message}", e)
            ExecutionResult.failure("Error: ${e.message}")
        }
    }
}