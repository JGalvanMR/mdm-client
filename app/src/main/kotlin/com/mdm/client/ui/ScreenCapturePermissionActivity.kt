package com.mdm.client.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import com.mdm.client.commands.handlers.ScreenStreamHandler
import com.mdm.client.utils.MediaProjectionHolder

class ScreenCapturePermissionActivity : Activity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
            MediaProjectionHolder.instance = projection

            // Obtener los parámetros guardados en el handler
            val handler = ScreenStreamHandler(applicationContext)
            handler.onPermissionGranted(
                    projection,
                    null
            ) // Los parámetros ya están en pendingParams
        } else {
            Log.w("ScreenCapture", "Permiso de captura denegado")
        }
        finish()
    }
}
