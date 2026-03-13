package com.goonanimation.data.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Represents a single animation frame.
 * Each frame can contain multiple layers composited together.
 * The [compositeBitmap] holds the final rendered result for display and export.
 */
data class Frame(
    val id: String = UUID.randomUUID().toString(),
    val layers: MutableList<Layer> = mutableListOf(),
    val durationMs: Int = 83, // ~12fps by default
    val index: Int = 0
) {
    /**
     * Returns a composite bitmap merging all visible layers from bottom to top.
     * This is used for onion skin and export purposes.
     */
    fun composite(width: Int, height: Int): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        for (layer in layers) {
            if (layer.isVisible) {
                val paint = android.graphics.Paint().apply {
                    alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                }
                canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
            }
        }
        return result
    }
}
