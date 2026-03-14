package com.goonanimation.data.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Represents a single drawing layer within a frame.
 * Layers allow non-destructive editing by stacking transparent bitmaps.
 */
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Layer",
    val bitmap: Bitmap,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val isLocked: Boolean = false
) {
    /**
     * Creates a deep copy of this layer with a fresh bitmap.
     */
    fun deepCopy(): Layer = copy(
        id = UUID.randomUUID().toString(),
        bitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    )
}
