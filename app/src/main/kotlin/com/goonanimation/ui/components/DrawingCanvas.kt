package com.goonanimation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.goonanimation.ui.screen.editor.EditorViewModel
import com.goonanimation.ui.theme.CanvasBackground
import kotlin.math.min

/**
 * The main drawing canvas composable.
 *
 * Features:
 * - Pinch-to-zoom and pan gesture support
 * - Draws the checkerboard transparency pattern
 * - Renders all visible layers of the current frame
 * - Renders onion skin overlay
 * - Routes touch events to the DrawingEngine
 * - Recomposes efficiently using [canvasVersion] as a recomposition trigger
 */
@Composable
fun DrawingCanvas(
    viewModel: EditorViewModel,
    currentFrameIndex: Int,
    canvasVersion: Int,
    isPlaying: Boolean,
    playbackFrame: Int,
    modifier: Modifier = Modifier
) {
    // Transform state for zoom/pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Track whether a drawing gesture is in progress (vs pan gesture)
    var isDrawingGesture by remember { mutableStateOf(false) }
    var pointerCount by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .background(CanvasBackground)
            // Zoom and pan with 2+ fingers
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (pointerCount >= 2) {
                        scale = (scale * zoom).coerceIn(0.1f, 10f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            // Drawing with single finger
            .pointerInput(canvasVersion) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pointerCount = event.changes.size

                        when (event.type) {
                            PointerEventType.Press -> {
                                if (event.changes.size == 1 && !isPlaying) {
                                    isDrawingGesture = true
                                    val change = event.changes.first()
                                    val canvasCoord = screenToCanvas(
                                        change.position, scale, offsetX, offsetY,
                                        size.width.toFloat(), size.height.toFloat(),
                                        viewModel.project.value.canvasWidth.toFloat(),
                                        viewModel.project.value.canvasHeight.toFloat()
                                    )
                                    viewModel.onDrawingStart(canvasCoord.x, canvasCoord.y)
                                    change.consume()
                                }
                            }
                            PointerEventType.Move -> {
                                if (isDrawingGesture && event.changes.size == 1 && !isPlaying) {
                                    val change = event.changes.first()
                                    val canvasCoord = screenToCanvas(
                                        change.position, scale, offsetX, offsetY,
                                        size.width.toFloat(), size.height.toFloat(),
                                        viewModel.project.value.canvasWidth.toFloat(),
                                        viewModel.project.value.canvasHeight.toFloat()
                                    )
                                    viewModel.onDrawingMove(canvasCoord.x, canvasCoord.y)
                                    change.consume()
                                }
                            }
                            PointerEventType.Release -> {
                                if (isDrawingGesture && !isPlaying) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val canvasCoord = screenToCanvas(
                                            change.position, scale, offsetX, offsetY,
                                            size.width.toFloat(), size.height.toFloat(),
                                            viewModel.project.value.canvasWidth.toFloat(),
                                            viewModel.project.value.canvasHeight.toFloat()
                                        )
                                        viewModel.onDrawingEnd(canvasCoord.x, canvasCoord.y)
                                    }
                                }
                                isDrawingGesture = false
                            }
                            else -> {}
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val project = viewModel.project.value
            val cw = project.canvasWidth.toFloat()
            val ch = project.canvasHeight.toFloat()

            // Calculate canvas rect centered in the view
            val scaleX = size.width / cw
            val scaleY = size.height / ch
            val fitScale = min(scaleX, scaleY) * scale
            val canvasLeft = (size.width - cw * fitScale) / 2f + offsetX
            val canvasTop = (size.height - ch * fitScale) / 2f + offsetY

            // Draw canvas background with checkerboard transparency indicator
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val gridSize = (16 * fitScale).coerceAtLeast(4f)
                val right = canvasLeft + cw * fitScale
                val bottom = canvasTop + ch * fitScale

                // Checkerboard background
                val lightPaint = android.graphics.Paint().apply { color = 0xFFCCCCCC.toInt() }
                val darkPaint = android.graphics.Paint().apply { color = 0xFF999999.toInt() }
                var x = canvasLeft
                var rowEven = true
                while (x < right) {
                    var y = canvasTop
                    var colEven = rowEven
                    while (y < bottom) {
                        val tileRight = min(x + gridSize, right)
                        val tileBottom = min(y + gridSize, bottom)
                        nativeCanvas.drawRect(x, y, tileRight, tileBottom, if (colEven) lightPaint else darkPaint)
                        y += gridSize
                        colEven = !colEven
                    }
                    x += gridSize
                    rowEven = !rowEven
                }

                // Render frames
                val displayFrame = if (isPlaying) {
                    project.frames.getOrNull(playbackFrame)
                } else {
                    project.frames.getOrNull(currentFrameIndex)
                }

                // Draw onion skin (only in edit mode)
                if (!isPlaying) {
                    val onionBitmap = viewModel.buildOnionSkinBitmap(
                        project.canvasWidth, project.canvasHeight
                    )
                    if (onionBitmap != null) {
                        val dst = android.graphics.RectF(canvasLeft, canvasTop, right, bottom)
                        nativeCanvas.drawBitmap(onionBitmap, null, dst, null)
                    }
                }

                // Draw visible layers
                displayFrame?.layers?.forEach { layer ->
                    if (layer.isVisible) {
                        val paint = android.graphics.Paint().apply {
                            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                        }
                        val dst = android.graphics.RectF(canvasLeft, canvasTop, right, bottom)
                        nativeCanvas.drawBitmap(layer.bitmap, null, dst, paint)
                    }
                }

                // Canvas border
                val borderPaint = android.graphics.Paint().apply {
                    color = 0xFF555555.toInt()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2f
                }
                nativeCanvas.drawRect(canvasLeft, canvasTop, right, bottom, borderPaint)
            }
        }
    }
}

/**
 * Converts screen coordinates to canvas bitmap coordinates,
 * accounting for zoom and pan transforms.
 */
private fun screenToCanvas(
    screenPos: Offset,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    viewWidth: Float,
    viewHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    val fitScale = min(viewWidth / canvasWidth, viewHeight / canvasHeight) * scale
    val canvasLeft = (viewWidth - canvasWidth * fitScale) / 2f + offsetX
    val canvasTop = (viewHeight - canvasHeight * fitScale) / 2f + offsetY

    val canvasX = ((screenPos.x - canvasLeft) / fitScale).coerceIn(0f, canvasWidth - 1f)
    val canvasY = ((screenPos.y - canvasTop) / fitScale).coerceIn(0f, canvasHeight - 1f)
    return Offset(canvasX, canvasY)
}
