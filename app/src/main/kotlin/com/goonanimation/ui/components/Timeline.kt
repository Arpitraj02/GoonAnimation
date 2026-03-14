package com.goonanimation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goonanimation.data.model.Frame
import com.goonanimation.ui.theme.DarkPrimary
import com.goonanimation.ui.theme.DarkSurface
import com.goonanimation.ui.theme.FrameBorder
import com.goonanimation.ui.theme.FrameSelected
import com.goonanimation.ui.theme.FrameUnselected
import com.goonanimation.ui.theme.TimelineBackground

/**
 * Bottom timeline bar showing all animation frames as thumbnails.
 * Allows frame selection, addition, duplication, deletion, and playback control.
 */
@Composable
fun TimelineBar(
    frames: List<Frame>,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    canvasWidth: Int,
    canvasHeight: Int,
    onionSkinEnabled: Boolean,
    onFrameSelected: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: (Int) -> Unit,
    onDeleteFrame: (Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onToggleOnionSkin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Scroll to selected frame
    LaunchedEffect(currentFrameIndex) {
        if (currentFrameIndex in frames.indices) {
            listState.animateScrollToItem(currentFrameIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TimelineBackground)
            .padding(vertical = 4.dp)
    ) {
        // ── Controls row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Play / Pause
            IconButton(onClick = if (isPlaying) onPause else onPlay, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Frame counter
            Text(
                text = "${currentFrameIndex + 1}/${frames.size}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // Onion skin toggle
            IconButton(
                onClick = onToggleOnionSkin,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.RemoveRedEye,
                    contentDescription = "Onion Skin",
                    tint = if (onionSkinEnabled) DarkPrimary else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add frame
            IconButton(onClick = onAddFrame, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add Frame", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Duplicate current frame
            IconButton(onClick = { onDuplicateFrame(currentFrameIndex) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate Frame", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Delete current frame
            IconButton(
                onClick = { onDeleteFrame(currentFrameIndex) },
                enabled = frames.size > 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete Frame",
                    tint = if (frames.size > 1) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── Frame thumbnails ──
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(frames, key = { _, frame -> frame.id }) { index, frame ->
                FrameThumbnail(
                    frame = frame,
                    index = index,
                    isSelected = index == currentFrameIndex,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    onClick = { onFrameSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun FrameThumbnail(
    frame: Frame,
    index: Int,
    isSelected: Boolean,
    canvasWidth: Int,
    canvasHeight: Int,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) FrameSelected else FrameBorder
    val bgColor = if (isSelected) FrameSelected.copy(alpha = 0.15f) else FrameUnselected

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        // Thumbnail canvas
        Box(
            modifier = Modifier
                .size(52.dp, 52.dp)
                .background(Color(0xFF888888), RoundedCornerShape(2.dp))
        ) {
            Canvas(modifier = Modifier.size(52.dp, 52.dp)) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    // Draw checkerboard
                    val paint = android.graphics.Paint()
                    val cellSize = 8f
                    var cx = 0f; var rowEven2 = true
                    while (cx < 52.dp.toPx()) {
                        var cy = 0f; var colEven2 = rowEven2
                        while (cy < 52.dp.toPx()) {
                            paint.color = if (colEven2) 0xFFCCCCCC.toInt() else 0xFF999999.toInt()
                            nativeCanvas.drawRect(cx, cy, cx + cellSize, cy + cellSize, paint)
                            cy += cellSize; colEven2 = !colEven2
                        }
                        cx += cellSize; rowEven2 = !rowEven2
                    }
                    // Draw composited layers
                    for (layer in frame.layers) {
                        if (layer.isVisible) {
                            val layerPaint = android.graphics.Paint().apply {
                                alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                            }
                            val dst = android.graphics.RectF(0f, 0f, 52.dp.toPx(), 52.dp.toPx())
                            nativeCanvas.drawBitmap(layer.bitmap, null, dst, layerPaint)
                        }
                    }
                }
            }
        }

        // Frame number
        Text(
            text = "${index + 1}",
            color = if (isSelected) DarkPrimary else Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
