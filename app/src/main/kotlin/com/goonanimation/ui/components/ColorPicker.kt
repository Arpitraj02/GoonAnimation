package com.goonanimation.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.goonanimation.ui.theme.DarkPrimary
import com.goonanimation.ui.theme.DarkSurface
import com.goonanimation.ui.theme.DarkSurfaceVariant

/**
 * Full-featured color picker dialog with:
 * - HSL color wheel representation (via hue/saturation/lightness sliders)
 * - Opacity slider
 * - Recent colors grid
 * - Live preview
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Extract HSV components from the initial color once
    val initialHsv = remember(initialColor) {
        floatArrayOf(0f, 0f, 0f).also {
            android.graphics.Color.colorToHSV(initialColor, it)
        }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initialColor) / 255f) }

    val currentColor = remember(hue, saturation, value, alpha) {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        android.graphics.Color.argb(
            (alpha * 255).toInt(),
            android.graphics.Color.red(c),
            android.graphics.Color.green(c),
            android.graphics.Color.blue(c)
        )
    }

    // Common preset colors
    val presetColors = remember {
        listOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
            0xFFFF6600.toInt(), 0xFF9B59B6.toInt(), 0xFF3498DB.toInt(), 0xFF2ECC71.toInt(),
            0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFF1ABC9C.toInt(), 0xFF34495E.toInt()
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Color Picker", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))

            // Color preview
            Box(
                modifier = Modifier
                    .size(80.dp, 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(currentColor))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.height(16.dp))

            // Hue slider
            ColorSliderRow(label = "H", value = hue, range = 0f..360f) { hue = it }
            // Saturation slider
            ColorSliderRow(label = "S", value = saturation, range = 0f..1f) { saturation = it }
            // Value/Brightness slider
            ColorSliderRow(label = "V", value = value, range = 0f..1f) { value = it }
            // Alpha slider
            ColorSliderRow(label = "A", value = alpha, range = 0f..1f) { alpha = it }

            Spacer(Modifier.height(12.dp))

            // Preset colors grid
            Text("Presets", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presetColors.take(8).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, if (color == currentColor) Color.White else Color.Transparent, CircleShape)
                            .clickable {
                                val h = floatArrayOf(0f, 0f, 0f)
                                android.graphics.Color.colorToHSV(color, h)
                                hue = h[0]; saturation = h[1]; value = h[2]
                                alpha = android.graphics.Color.alpha(color) / 255f
                            }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presetColors.drop(8).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, if (color == currentColor) Color.White else Color.Transparent, CircleShape)
                            .clickable {
                                val h = floatArrayOf(0f, 0f, 0f)
                                android.graphics.Color.colorToHSV(color, h)
                                hue = h[0]; saturation = h[1]; value = h[2]
                                alpha = android.graphics.Color.alpha(color) / 255f
                            }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { onDismiss() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkPrimary)
                        .clickable { onColorSelected(currentColor) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(16.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
        )
        Text(
            text = if (range.endInclusive > 2f) value.toInt().toString() else "%.2f".format(value),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}
