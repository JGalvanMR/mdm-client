package com.mdm.client.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import com.mdm.client.utils.MediaProjectionHolder

class ScreenCapturePermissionActivity : Activity() {  // Cambiado a Activity simple
    
    private val TAG = "ScreenCapturePerm"
    private val REQUEST_CODE_SCREEN_CAPTURE = 1001
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Iniciando solicitud de permiso MediaProjection")
        
        try {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = projectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando captura: ${e.message}", e)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "Resultado: requestCode=$requestCode, resultCode=$resultCode")
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            try {
                val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                MediaProjectionHolder.instance = mediaProjection
                Log.i(TAG, "MediaProjection guardado exitosamente")
                
                // Notificar al servicio que ya hay permiso (opcional, para auto-reintentar)
                sendBroadcast(Intent("com.mdm.client.SCREEN_CAPTURE_GRANTED"))
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando MediaProjection: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Permiso denegado o cancelado por usuario")
        }
        
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity destruida")
    }
}