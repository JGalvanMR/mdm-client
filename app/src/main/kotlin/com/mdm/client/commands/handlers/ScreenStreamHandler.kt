package com.mdm.client.commands.handlers

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.mdm.client.core.ExecutionResult
import com.mdm.client.data.network.MdmWebSocketClient
import com.google.gson.Gson
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenStreamHandler(private val context: Context) {
    private val TAG = "ScreenStreamHandler"
    private var encoder: MediaCodec? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private val isStreaming = AtomicBoolean(false)
    private val codecThread = HandlerThread("ScreenStreamEncoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)

    private var currentBitrate = 2_000_000
    private val MIN_BITRATE = 500_000
    private val MAX_BITRATE = 4_000_000
    private var frameCount = 0
    private var lastKeyframeTime = 0L
    private val KEYFRAME_INTERVAL_MS = 2000

    // Dimensiones reales del stream (para incluir en video_config)
    private var streamWidth: Int = 0
    private var streamHeight: Int = 0

    // Guardar parámetros temporalmente mientras se obtiene el permiso
    private var pendingParams: String? = null

    fun start(parametersJson: String?): ExecutionResult {
        if (isStreaming.get()) return ExecutionResult.failure("Streaming ya activo")

        pendingParams = parametersJson

        Log.i(TAG, "Solicitando permiso de proyección...")
        try {
            val intent = Intent(context, com.mdm.client.ui.ScreenCapturePermissionActivity::class.java)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return ExecutionResult.success("""{"status":"waiting_permission"}""")
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar permiso", e)
            return ExecutionResult.failure("No se pudo solicitar permiso: ${e.message}")
        }
    }

    /** Llamado desde ScreenCapturePermissionActivity cuando el permiso fue concedido. */
    fun onPermissionGranted(projection: MediaProjection, params: String?) {
        mediaProjection = projection
        val quality = parseQualityParams(params)

        try {
            isStreaming.set(true)
            setupEncoder(quality)
            startEncodingLoop()

            WebSocketHolder.instance?.let { ws ->
                sendStreamConfig(ws, quality.width, quality.height)
            }

            Log.i(TAG, "Streaming iniciado: ${quality.width}x${quality.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar streaming", e)
            stop()
        } finally {
            pendingParams = null
        }
    }

    private data class QualityParams(
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val fps: Int
    )

    private fun parseQualityParams(json: String?): QualityParams {
        return try {
            if (json != null) {
                val obj = org.json.JSONObject(json)
                QualityParams(
                    width = obj.optInt("width", 1080),
                    height = obj.optInt("height", 2336),
                    bitrate = obj.optInt("bitrate", 2_000_000),
                    fps = obj.optInt("fps", 30)
                )
            } else {
                QualityParams(1080, 2336, 2_000_000, 30)
            }
        } catch (e: Exception) {
            QualityParams(1080, 2336, 2_000_000, 30)
        }
    }

    private fun setupEncoder(quality: QualityParams) {
        // Guardar dimensiones reales
        streamWidth = quality.width
        streamHeight = quality.height

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            quality.width,
            quality.height
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            )
            setInteger(
                MediaFormat.KEY_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            )
            setInteger(
                MediaFormat.KEY_LEVEL,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31
            )
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = createInputSurface()
            start()

            val dpi = context.resources.displayMetrics.densityDpi
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenStream",
                quality.width,
                quality.height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )
        }
    }

    private fun startEncodingLoop() {
        codecHandler.post(object : Runnable {
            override fun run() {
                if (!isStreaming.get()) return
                drainEncoder()
                codecHandler.postDelayed(this, 5)
            }
        })
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        encoder?.let { enc ->
            while (true) {
                val outIndex = enc.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sendCodecConfig(enc.outputFormat)
                    }
                    outIndex >= 0 -> {
                        val buffer = enc.getOutputBuffer(outIndex) ?: continue
                        handleEncodedData(buffer, bufferInfo)
                        enc.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        }
    }

    private fun handleEncodedData(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0) return

        val data = ByteArray(info.size)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.get(data)

        val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        if (frameCount < 5) {
            val hexPreview = data.take(8).joinToString(" ") { "%02X".format(it) }
            Log.d(
                TAG,
                "Frame #$frameCount (${if (isKeyframe) "IDR" else "P"}): $hexPreview ... (size=${data.size})"
            )
        }

        WebSocketHolder.instance?.sendBinary(data)

        frameCount++
        if (isKeyframe && System.currentTimeMillis() - lastKeyframeTime > KEYFRAME_INTERVAL_MS) {
            lastKeyframeTime = System.currentTimeMillis()
            adjustBitrate(data.size)
        }
    }

    private fun adjustBitrate(frameSize: Int) {
        if (frameSize > 50_000 && currentBitrate > MIN_BITRATE) {
            currentBitrate = (currentBitrate * 0.9).toInt()
            updateBitrate(currentBitrate)
        } else if (frameSize < 20_000 && currentBitrate < MAX_BITRATE) {
            currentBitrate = (currentBitrate * 1.1).toInt()
            updateBitrate(currentBitrate)
        }
    }

    private fun updateBitrate(newBitrate: Int) {
        try {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate)
            }
            encoder?.setParameters(params)
            Log.d(TAG, "Bitrate ajustado a: $newBitrate")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo ajustar bitrate")
        }
    }

    private fun sendCodecConfig(format: MediaFormat) {
        val sps = format.getByteBuffer("csd-0")?.let {
            val arr = ByteArray(it.remaining())
            it.get(arr)
            arr
        }
        val pps = format.getByteBuffer("csd-1")?.let {
            val arr = ByteArray(it.remaining())
            it.get(arr)
            arr
        }

        if (sps != null && pps != null) {
            val config = mutableMapOf<String, Any>(
                "type" to "video_config",
                "sps" to android.util.Base64.encodeToString(sps, android.util.Base64.NO_WRAP),
                "pps" to android.util.Base64.encodeToString(pps, android.util.Base64.NO_WRAP),
                "width" to streamWidth,
                "height" to streamHeight,
                "timestamp" to System.currentTimeMillis()
            )
            WebSocketHolder.instance?.sendJson(Gson().toJson(config))

            // Sincronizar dimensiones para el escalado táctil
            InputInjectionHandler.streamWidth = streamWidth
            InputInjectionHandler.streamHeight = streamHeight
        }
    }

    private fun sendStreamConfig(ws: MdmWebSocketClient, width: Int, height: Int) {
        val config = mapOf(
            "type" to "stream_start",
            "width" to width,
            "height" to height,
            "codec" to "avcc",                       // corregido de "vp9" a "avc"
            "timestamp" to System.currentTimeMillis()
        )
        ws.sendJson(Gson().toJson(config))
    }

    fun stop(): ExecutionResult {
        isStreaming.set(false)
        codecHandler.removeCallbacksAndMessages(null)

        try {
            virtualDisplay?.release()
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo stream", e)
        }

        WebSocketHolder.instance?.sendJson("""{"type":"stream_stop"}""")
        return ExecutionResult.success("""{"status":"stopped"}""")
    }

    object WebSocketHolder {
        var instance: MdmWebSocketClient? = null
    }
}