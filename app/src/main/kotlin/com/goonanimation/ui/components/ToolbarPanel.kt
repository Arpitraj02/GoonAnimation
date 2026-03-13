package com.goonanimation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goonanimation.engine.DrawingTool
import com.goonanimation.ui.theme.DarkPrimary
import com.goonanimation.ui.theme.DarkSurface
import com.goonanimation.ui.theme.DarkSurfaceVariant
import com.goonanimation.ui.theme.ToolSelected
import com.goonanimation.ui.theme.ToolUnselected

/**
 * Floating toolbar panel docked to the left side of the editor.
 * Contains drawing tools, undo/redo, brush/opacity sliders, and color picker.
 */
@Composable
fun ToolbarPanel(
    activeTool: DrawingTool,
    brushSize: Float,
    opacity: Float,
    selectedColor: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolSelected: (DrawingTool) -> Unit,
    onBrushSizeChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onColorPicked: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onToggleLayers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(DarkSurface, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Drawing Tools ──
        ToolButton(
            icon = Icons.Filled.Edit,
            label = "Pen",
            selected = activeTool == DrawingTool.PEN,
            onClick = { onToolSelected(DrawingTool.PEN) }
        )
        ToolButton(
            icon = Icons.Filled.Brush,
            label = "Brush",
            selected = activeTool == DrawingTool.BRUSH,
            onClick = { onToolSelected(DrawingTool.BRUSH) }
        )
        ToolButton(
            icon = Icons.Filled.Clear,
            label = "Eraser",
            selected = activeTool == DrawingTool.ERASER,
            onClick = { onToolSelected(DrawingTool.ERASER) }
        )
        ToolButton(
            icon = Icons.Filled.FormatColorFill,
            label = "Fill",
            selected = activeTool == DrawingTool.FILL,
            onClick = { onToolSelected(DrawingTool.FILL) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Color Swatch ──
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(selectedColor))
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable { onColorPicked() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Undo / Redo ──
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Layers ──
        IconButton(
            onClick = onToggleLayers,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.Layers,
                contentDescription = "Layers",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ToolSelected else ToolUnselected)
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Brush settings panel showing size and opacity sliders.
 * Displayed as a horizontal row above the canvas.
 */
@Composable
fun BrushSettingsRow(
    brushSize: Float,
    opacity: Float,
    onBrushSizeChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Straighten, contentDescription = "Size", tint = Color.White, modifier = Modifier.size(18.dp))
        Slider(
            value = brushSize,
            onValueChange = onBrushSizeChanged,
            valueRange = 1f..80f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
        )
        Text(
            text = brushSize.toInt().toString(),
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.width(8.dp))

        Icon(Icons.Filled.Opacity, contentDescription = "Opacity", tint = Color.White, modifier = Modifier.size(18.dp))
        Slider(
            value = opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0.01f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
        )
        Text(
            text = "${(opacity * 100).toInt()}%",
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )
    }
}
