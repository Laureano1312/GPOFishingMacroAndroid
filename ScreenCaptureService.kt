package com.example.gpofishingmacro

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * User-authorized screen capture prototype. Analyzes frames and updates status only.
 * It does NOT send touches automatically.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val ACTION_STOP = "com.example.gpofishingmacro.STOP_CAPTURE"
        const val ACTION_AUTO_ON = "com.example.gpofishingmacro.AUTO_ON"
        const val ACTION_AUTO_OFF = "com.example.gpofishingmacro.AUTO_OFF"
        const val CHANNEL = "gpo_capture"
        @Volatile var lastStatus: String = "Captura no iniciada"
        @Volatile var autoEnabled: Boolean = false
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastAnalyzeAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            autoEnabled = false
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTO_ON) {
            autoEnabled = true
            lastStatus = "Control experimental activo (máximo 30 s)"
            handler?.postDelayed({ autoEnabled = false; lastStatus = "Control detenido por límite de seguridad" }, 30_000)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_AUTO_OFF) {
            autoEnabled = false
            lastStatus = "Control detenido por el usuario"
            return START_NOT_STICKY
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(51, makeNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(51, makeNotification())
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_RESULT_DATA))
        if (resultCode != Activity.RESULT_OK || data == null) {
            lastStatus = "Permiso de captura no recibido"
            stopSelf()
            return START_NOT_STICKY
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture(); stopSelf() }
        }, Handler(mainLooper))
        startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        thread = HandlerThread("GPOFrameAnalyzer").also { it.start() }
        handler = Handler(thread!!.looper)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader!!.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                // Analyze at most 5 frames per second to reduce heat and battery use.
                if (now - lastAnalyzeAt >= 200) {
                    lastAnalyzeAt = now
                    val bitmap = imageToBitmap(image)
                    if (bitmap != null) {
                        val d = FishingVisionAnalyzer.analyze(bitmap)
                        lastStatus = "${d.message}; verde=${d.targetCenterY?.let { "%.2f".format(it) } ?: "?"}; blanco=${d.whiteMarkerCenterY?.let { "%.2f".format(it) } ?: "?"}"
                        if (autoEnabled && d.targetCenterY != null && d.whiteMarkerCenterY != null && d.confidence >= 0.02f) {
                            val delta = d.whiteMarkerCenterY - d.targetCenterY
                            val access = FishingAccessibilityService.instance
                            // Conservative dead band reduces oscillation. Positive delta means
                            // marker is below the target, so a short hold attempts to move up.
                            if (access != null && !access.isBusy() && delta > 0.07f) {
                                access.sendHold(140)
                            }
                        }
                        bitmap.recycle()
                    }
                }
            } catch (_: Exception) {
                lastStatus = "Error analizando fotograma"
            } finally {
                image.close()
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "GPOFishingCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface, null, handler
        )
        lastStatus = "Captura activa: analizando pantalla; sin control táctil automático"
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val result = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return result
    }

    private fun makeNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "GPO screen analysis", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("GPO Fishing Macro")
            .setContentText("Analizando pantalla; toca Detener para finalizar")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(Notification.Action.Builder(null, "Detener", pending).build())
            .setOngoing(true).build()
    }

    private fun stopCapture() {
        autoEnabled = false
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        thread?.quitSafely(); thread = null; handler = null
        lastStatus = "Captura detenida"
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
}
