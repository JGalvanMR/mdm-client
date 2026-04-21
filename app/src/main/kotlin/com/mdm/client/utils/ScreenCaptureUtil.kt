package com.mdm.client.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap

object ScreenCaptureUtil {

    fun capture(context: Context): Bitmap {
        val activity = context as Activity
        val view = activity.window.decorView.rootView

        view.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(view.drawingCache)
        view.isDrawingCacheEnabled = false

        return bitmap
    }
}
