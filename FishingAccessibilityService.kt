package com.example.gpofishingmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility gesture helper. Automation is experimental and bounded by the capture service.
 */
class FishingAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: FishingAccessibilityService? = null
        @Volatile var lastGesture: String = "Sin gestos"
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { busy = false }
    override fun onDestroy() { busy = false; if (instance === this) instance = null; super.onDestroy() }

    // Test only: one short hold at lower-center screen position.
    fun testHold() { sendHold(700) }

    fun sendHold(durationMs: Long) {
        if (busy) return
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.50f
        val y = dm.heightPixels * 0.72f
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80, 350))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        busy = true
        lastGesture = "Pulsación enviada"
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                busy = false
                lastGesture = "Pulsación completada"
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                busy = false
                lastGesture = "Pulsación cancelada"
            }
        }, main)
    }

    fun isBusy() = busy
}
