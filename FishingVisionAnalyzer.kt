package com.example.gpofishingmacro

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * Experimental pixel analyzer. This only proposes candidate positions; it does not
 * send touches or guarantee that detected pixels belong to the fishing UI.
 */
data class FishingDetection(
    val targetCenterY: Float?,
    val whiteMarkerCenterY: Float?,
    val confidence: Float,
    val message: String
)

object FishingVisionAnalyzer {
    // Estimated from the screenshot supplied by the user; needs live calibration.
    fun analyze(bitmap: Bitmap): FishingDetection {
        val left = (bitmap.width * 0.54f).toInt().coerceIn(0, bitmap.width - 2)
        val right = (bitmap.width * 0.66f).toInt().coerceIn(left + 1, bitmap.width - 1)
        val top = (bitmap.height * 0.25f).toInt().coerceIn(0, bitmap.height - 2)
        val bottom = (bitmap.height * 0.75f).toInt().coerceIn(top + 1, bitmap.height - 1)
        val w = right - left
        val h = bottom - top
        val greenRows = IntArray(h)
        val whiteRows = IntArray(h)
        val stepX = max(1, w / 60)
        val stepY = max(1, h / 240)
        var greenCount = 0
        var sampled = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bitmap.getPixel(left + x, top + y)
                val r = (c ushr 16) and 255
                val g = (c ushr 8) and 255
                val b = c and 255
                sampled++
                if (g > 145 && g > r * 1.35 && g > b * 1.15 && r < 150) {
                    greenRows[y]++
                    greenCount++
                }
                if (r > 220 && g > 220 && b > 220) whiteRows[y]++
                x += stepX
            }
            y += stepY
        }
        val target = strongestBand(greenRows, stepY, 2)
        val marker = strongestBand(whiteRows, stepY, 3)
        val confidence = min(1f, greenCount.toFloat() / max(1, sampled) * 80f)
        val message = when {
            target == null -> "Zona verde no detectada; recalibrar"
            marker == null -> "Zona verde candidata; marcador sin confirmar"
            else -> "Candidatos detectados; validar antes de automatizar"
        }
        return FishingDetection(target, marker, confidence, message)
    }

    private fun strongestBand(rows: IntArray, stepY: Int, minRun: Int): Float? {
        var bestStart = -1
        var bestEnd = -1
        var bestScore = 0
        var start = -1
        var score = 0
        for (i in rows.indices) {
            if (rows[i] > 0) {
                if (start < 0) { start = i; score = 0 }
                score += rows[i]
            } else if (start >= 0) {
                val end = i - 1
                if (end - start + 1 >= minRun && score > bestScore) {
                    bestStart = start; bestEnd = end; bestScore = score
                }
                start = -1; score = 0
            }
        }
        if (start >= 0 && rows.lastIndex - start + 1 >= minRun && score > bestScore) {
            bestStart = start; bestEnd = rows.lastIndex; bestScore = score
        }
        if (bestStart < 0) return null
        val center = (bestStart + bestEnd) / 2f * stepY
        return (center / (rows.size * stepY).toFloat()).coerceIn(0f, 1f)
    }
}
