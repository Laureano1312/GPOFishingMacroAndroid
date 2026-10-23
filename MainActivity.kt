package com.example.gpofishingmacro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    private val captureRequest = 301
    private lateinit var status: TextView
    private lateinit var refresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        val title = TextView(this).apply {
            text = "GPO Fishing Macro — V3"
            textSize = 22f; setTextColor(Color.BLACK)
        }
        val info = TextView(this).apply {
            text = "\\nAnálisis experimental de pantalla en tiempo real. Android pedirá permiso para compartir la pantalla. El control automático es heurístico, puede fallar y no garantiza pesca AFK."
            textSize = 15f; setTextColor(Color.DKGRAY)
        }
        val accessibility = Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val startCapture = Button(this).apply {
            text = "Iniciar análisis de pantalla"
            setOnClickListener {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
            }
        }
        val autoOn = Button(this).apply {
            text = "Activar control automático (experimental)"
            setOnClickListener {
                startService(Intent(this@MainActivity, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_AUTO_ON))
                status.text = "\\nEstado: control solicitado; límite de 30 segundos"
            }
        }
        val autoOff = Button(this).apply {
            text = "DETENER control automático"
            setOnClickListener {
                startService(Intent(this@MainActivity, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_AUTO_OFF))
                status.text = "\\nEstado: ${ScreenCaptureService.lastStatus}"
            }
        }
        val stopCapture = Button(this).apply {
            text = "Detener análisis"
            setOnClickListener {
                startService(Intent(this@MainActivity, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                status.text = ScreenCaptureService.lastStatus
            }
        }
        status = TextView(this).apply {
            text = "\\nEstado: ${ScreenCaptureService.lastStatus}"
            textSize = 14f; setTextColor(Color.rgb(20, 90, 50))
        }
        refresh = Button(this).apply {
            text = "Actualizar estado"
            setOnClickListener { status.text = "\\nEstado: ${ScreenCaptureService.lastStatus}" }
        }
        layout.addView(title); layout.addView(info); layout.addView(accessibility)
        layout.addView(startCapture); layout.addView(autoOn); layout.addView(autoOff)
        layout.addView(stopCapture); layout.addView(refresh); layout.addView(status)
        setContentView(layout)
    }

    @Deprecated("Deprecated in Android API, retained for compatibility with target SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            status.text = "\\nEstado: ${ScreenCaptureService.lastStatus}"
        }
    }
}
