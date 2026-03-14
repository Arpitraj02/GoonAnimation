package com.goonanimation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goonanimation.data.model.Layer
import com.goonanimation.ui.theme.DarkPrimary
import com.goonanimation.ui.theme.DarkSurface
import com.goonanimation.ui.theme.DarkSurfaceVariant

/**
 * Side panel showing all layers for the current frame.
 * Allows adding, deleting, toggling visibility, and selecting layers.
 */
@Composable
fun LayersPanel(
    visible: Boolean,
    layers: List<Layer>,
    selectedLayerIndex: Int,
    onLayerSelected: (Int) -> Unit,
    onLayerVisibilityToggled: (Int) -> Unit,
    onLayerDeleted: (Int) -> Unit,
    onAddLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(DarkSurface)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Layers",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddLayer, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Layer", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(layers.reversed(), key = { _, layer -> layer.id }) { reversedIdx, layer ->
                    val actualIdx = layers.lastIndex - reversedIdx
                    LayerRow(
                        layer = layer,
                        isSelected = actualIdx == selectedLayerIndex,
                        canDelete = layers.size > 1,
                        onClick = { onLayerSelected(actualIdx) },
                        onVisibilityToggle = { onLayerVisibilityToggled(actualIdx) },
                        onDelete = { onLayerDeleted(actualIdx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: Layer,
    isSelected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onVisibilityToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) DarkPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) DarkPrimary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Visibility toggle
        IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(28.dp)) {
            Icon(
                if (layer.isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = "Toggle Visibility",
                tint = if (layer.isVisible) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = layer.name,
            color = if (layer.isVisible) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        // Delete
        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete Layer",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
