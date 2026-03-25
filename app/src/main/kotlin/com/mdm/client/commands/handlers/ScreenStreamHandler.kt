package com.mdm.client.commands.handlers

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.mdm.client.core.ExecutionResult
import com.mdm.client.data.network.MdmWebSocketClient
import com.mdm.client.utils.MediaProjectionHolder
import java.util.concurrent.atomic.AtomicBoolean

class ScreenStreamHandler(private val context: Context) {
    private val TAG = "ScreenStreamHandler"
    private var encoder: MediaCodec? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var wsClient: MdmWebSocketClient? = null
    private var mediaProjection: MediaProjection? = null   // ← AGREGADO
    private val isStreaming = AtomicBoolean(false)
    private val codecThread = HandlerThread("ScreenStreamEncoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)

    fun start(parametersJson: String?): ExecutionResult {
        if (isStreaming.get()) return ExecutionResult.failure("Streaming already active")

        // Asignar la propiedad mediaProjection
        mediaProjection = MediaProjectionHolder.instance
            ?: return ExecutionResult.failure(
                "MediaProjection not available. Grant screen capture permission first."
            )

        val width = 1280
        val height = 720
        val dpi = context.resources.displayMetrics.densityDpi
        val bitrate = 2_000_000
        val frameRate = 30

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                format.setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder!!.createInputSurface()
            encoder!!.start()

            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "ScreenStream", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            )

            isStreaming.set(true)
            wsClient = WebSocketHolder.instance

            codecHandler.post { encodingLoop() }

            return ExecutionResult.success("""{"status":"started","width":$width,"height":$height}""")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream", e)
            stop()
            return ExecutionResult.failure("Error: ${e.message}")
        }
    }

    private fun encodingLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isStreaming.get()) {
            val outputBufferIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
            when (outputBufferIndex) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> continue
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = encoder!!.outputFormat
                    val csd0 = format.getByteBuffer("csd-0")
                    val csd1 = format.getByteBuffer("csd-1")
                    if (csd0 != null && csd1 != null) {
                        val config = mapOf(
                            "type" to "config",
                            "csd0" to Base64.encodeToString(csd0.array(), Base64.NO_WRAP),
                            "csd1" to Base64.encodeToString(csd1.array(), Base64.NO_WRAP),
                            "width" to format.getInteger(MediaFormat.KEY_WIDTH),
                            "height" to format.getInteger(MediaFormat.KEY_HEIGHT)
                        )
                        wsClient?.sendJson(Gson().toJson(config))
                    }
                }
                else -> {
                    val outputBuffer = encoder!!.getOutputBuffer(outputBufferIndex) ?: continue
                    if (bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data, bufferInfo.offset, bufferInfo.size)
                        wsClient?.sendBinary(data)
                    }
                    encoder!!.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }
    }

    fun stop(): ExecutionResult {
        isStreaming.set(false)
        try {
            virtualDisplay?.release()
            encoder?.stop()
            encoder?.release()
            mediaProjection?.stop()   // ← ahora es válido
            wsClient = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping stream", e)
        }
        return ExecutionResult.success("""{"status":"stopped"}""")
    }

    object WebSocketHolder {
        var instance: MdmWebSocketClient? = null
    }
}