package com.goonanimation.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/**
 * Core drawing engine that renders strokes onto a mutable Bitmap.
 *
 * Architecture:
 * - Maintains a "working" canvas backed by [workingBitmap]
 * - Accumulates in-progress stroke paths for smooth real-time rendering
 * - Commits strokes to the target layer bitmap when touch ends
 * - Supports pen, brush, eraser, and fill bucket tools
 */
class DrawingEngine {

    // Current stroke path being drawn
    private val currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f
    private var isDrawing = false

    /** Paint configuration for current stroke */
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Paint for eraser mode using clear xfer-mode */
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // ──────────────────────── Touch Handling ────────────────────────

    /**
     * Called when the user touches the canvas. Starts a new stroke.
     * @param x,y Canvas coordinates (accounting for zoom/pan)
     * @param tool The active drawing tool
     * @param color ARGB color
     * @param size Stroke width in pixels
     * @param opacity 0..1 opacity
     * @param targetBitmap The layer bitmap to draw on
     */
    fun onTouchDown(
        x: Float, y: Float,
        tool: DrawingTool,
        color: Int,
        size: Float,
        opacity: Float,
        targetBitmap: Bitmap
    ) {
        if (tool == DrawingTool.FILL) {
            // Execute flood fill immediately on touch down
            floodFill(targetBitmap, x.toInt(), y.toInt(), color)
            return
        }
        currentPath.reset()
        currentPath.moveTo(x, y)
        lastX = x
        lastY = y
        isDrawing = true
        configurePaint(tool, color, size, opacity)
    }

    /**
     * Called on touch move events. Extends the current stroke using quadratic bezier
     * curves for smooth interpolation between touch points.
     */
    fun onTouchMove(
        x: Float, y: Float,
        tool: DrawingTool,
        color: Int,
        size: Float,
        opacity: Float,
        targetBitmap: Bitmap
    ) {
        if (!isDrawing || tool == DrawingTool.FILL) return
        // Quadratic bezier for smooth curves
        val midX = (lastX + x) / 2f
        val midY = (lastY + y) / 2f
        currentPath.quadTo(lastX, lastY, midX, midY)
        lastX = x
        lastY = y

        // Draw intermediate result to give real-time feedback
        val canvas = Canvas(targetBitmap)
        val paint = if (tool == DrawingTool.ERASER) {
            eraserPaint.apply { strokeWidth = size }
        } else {
            strokePaint.apply {
                this.color = color
                this.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                strokeWidth = size
            }
        }
        canvas.drawPath(currentPath, paint)
        currentPath.reset()
        currentPath.moveTo(x, y)
    }

    /**
     * Called when touch ends. Finalizes and commits the stroke to the bitmap.
     */
    fun onTouchUp(
        x: Float, y: Float,
        tool: DrawingTool,
        color: Int,
        size: Float,
        opacity: Float,
        targetBitmap: Bitmap
    ) {
        if (!isDrawing || tool == DrawingTool.FILL) return
        isDrawing = false
        // Draw a final dot if the path has no movement (tap)
        currentPath.lineTo(x, y)
        val canvas = Canvas(targetBitmap)
        val paint = if (tool == DrawingTool.ERASER) {
            eraserPaint.apply { strokeWidth = size }
        } else {
            strokePaint.apply {
                this.color = color
                this.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                strokeWidth = size
            }
        }
        canvas.drawPath(currentPath, paint)
        currentPath.reset()
    }

    // ──────────────────────── Fill Bucket ────────────────────────

    /**
     * Flood-fill algorithm using an iterative stack approach for performance.
     * Fills all connected pixels of the same color as the seed pixel
     * with the target [fillColor].
     */
    fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, fillColor: Int) {
        val width = bitmap.width
        val height = bitmap.height
        if (startX < 0 || startX >= width || startY < 0 || startY >= height) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val targetColor = pixels[startY * width + startX]

        if (targetColor == fillColor) return

        val stack = ArrayDeque<Int>()
        stack.addLast(startY * width + startX)
        val visited = BooleanArray(width * height)

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            if (idx < 0 || idx >= pixels.size) continue
            if (visited[idx]) continue
            if (pixels[idx] != targetColor) continue

            visited[idx] = true
            pixels[idx] = fillColor

            val x = idx % width
            val y = idx / width

            if (x > 0) stack.addLast(idx - 1)
            if (x < width - 1) stack.addLast(idx + 1)
            if (y > 0) stack.addLast(idx - width)
            if (y < height - 1) stack.addLast(idx + width)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    // ──────────────────────── Helpers ────────────────────────

    private fun configurePaint(tool: DrawingTool, color: Int, size: Float, opacity: Float) {
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        when (tool) {
            DrawingTool.PEN -> strokePaint.apply {
                this.color = color
                this.alpha = alpha
                strokeWidth = size
                maskFilter = null
            }
            DrawingTool.BRUSH -> strokePaint.apply {
                this.color = color
                this.alpha = alpha
                strokeWidth = size * 2.5f
                // Soft brush effect via BlurMaskFilter
                maskFilter = android.graphics.BlurMaskFilter(
                    size * 0.5f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            DrawingTool.ERASER -> eraserPaint.apply {
                strokeWidth = size
                maskFilter = null
            }
            DrawingTool.FILL -> { /* handled separately */ }
        }
    }

    /**
     * Creates a blank ARGB_8888 bitmap with transparent background.
     */
    fun createBlankBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
