package com.goonanimation.ui.screen.editor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goonanimation.ui.components.BrushSettingsRow
import com.goonanimation.ui.components.ColorPickerDialog
import com.goonanimation.ui.components.DrawingCanvas
import com.goonanimation.ui.components.LayersPanel
import com.goonanimation.ui.components.TimelineBar
import com.goonanimation.ui.components.ToolbarPanel
import com.goonanimation.ui.theme.DarkBackground
import com.goonanimation.ui.theme.DarkPrimary
import com.goonanimation.ui.theme.DarkSurface
import java.io.File

/**
 * Main animation editor screen.
 *
 * Layout (top to bottom, left to right):
 * ┌─────────────────────────────────────────┐
 * │            Top App Bar                  │
 * ├─────────────────────────────────────────┤
 * │         Brush Settings Row              │
 * ├──────┬──────────────────────────────────┤
 * │ Tool │                                  │
 * │ bar  │       Drawing Canvas             │
 * │      │                                  │
 * ├──────┴──────────────────────────────────┤
 * │           Timeline Bar                  │
 * └─────────────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String? = null,
    projectName: String = "New Animation",
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Load project if projectId is provided
    LaunchedEffect(projectId) {
        if (projectId != null) {
            viewModel.loadProject(projectId)
        } else {
            viewModel.newProject(projectName)
        }
    }

    val project by viewModel.project.collectAsState()
    val currentFrameIndex by viewModel.currentFrameIndex.collectAsState()
    val currentLayerIndex by viewModel.currentLayerIndex.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()
    val opacity by viewModel.opacity.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackFrame by viewModel.playbackFrame.collectAsState()
    val onionSkinEnabled by viewModel.onionSkinEnabled.collectAsState()
    val canvasVersion by viewModel.canvasVersion.collectAsState()
    val showLayersPanel by viewModel.showLayersPanel.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Show export messages as snackbar
    LaunchedEffect(exportMessage) {
        if (exportMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(exportMessage)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top App Bar ──
            TopAppBar(
                title = {
                    Text(
                        project.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveProject()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveProject() }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save", tint = Color.White)
                    }
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export GIF") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                showOverflowMenu = false
                                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                                viewModel.exportGif(dir)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export MP4") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = {
                                showOverflowMenu = false
                                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                                viewModel.exportMp4(dir)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Canvas") },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.clearCanvas()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )

            // Export progress bar
            if (exportProgress >= 0) {
                LinearProgressIndicator(
                    progress = { exportProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkPrimary
                )
            }

            // ── Brush Settings ──
            BrushSettingsRow(
                brushSize = brushSize,
                opacity = opacity,
                onBrushSizeChanged = { viewModel.setBrushSize(it) },
                onOpacityChanged = { viewModel.setOpacity(it) }
            )

            // ── Main area: toolbar + canvas + layers panel ──
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Left floating toolbar
                ToolbarPanel(
                    activeTool = activeTool,
                    brushSize = brushSize,
                    opacity = opacity,
                    selectedColor = selectedColor,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onToolSelected = { viewModel.setTool(it) },
                    onBrushSizeChanged = { viewModel.setBrushSize(it) },
                    onOpacityChanged = { viewModel.setOpacity(it) },
                    onColorPicked = { showColorPicker = true },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onClear = { viewModel.clearCanvas() },
                    onToggleLayers = { viewModel.toggleLayersPanel() }
                )

                // Drawing canvas (fills remaining space)
                DrawingCanvas(
                    viewModel = viewModel,
                    currentFrameIndex = currentFrameIndex,
                    canvasVersion = canvasVersion,
                    isPlaying = isPlaying,
                    playbackFrame = playbackFrame,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )

                // Right layers panel
                val currentFrame = project.frames.getOrNull(currentFrameIndex)
                if (currentFrame != null) {
                    LayersPanel(
                        visible = showLayersPanel,
                        layers = currentFrame.layers,
                        selectedLayerIndex = currentLayerIndex,
                        onLayerSelected = { viewModel.selectLayer(it) },
                        onLayerVisibilityToggled = { viewModel.toggleLayerVisibility(it) },
                        onLayerDeleted = { viewModel.deleteLayer(it) },
                        onAddLayer = { viewModel.addLayer() }
                    )
                }
            }

            // ── Timeline ──
            TimelineBar(
                frames = project.frames,
                currentFrameIndex = currentFrameIndex,
                isPlaying = isPlaying,
                canvasWidth = project.canvasWidth,
                canvasHeight = project.canvasHeight,
                onionSkinEnabled = onionSkinEnabled,
                onFrameSelected = { viewModel.selectFrame(it) },
                onAddFrame = { viewModel.addFrame() },
                onDuplicateFrame = { viewModel.duplicateFrame(it) },
                onDeleteFrame = { viewModel.deleteFrame(it) },
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
                onToggleOnionSkin = { viewModel.toggleOnionSkin() }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Color picker dialog
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = selectedColor,
            onColorSelected = { color ->
                viewModel.setColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}
