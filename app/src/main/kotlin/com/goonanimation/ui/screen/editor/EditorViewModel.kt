package com.goonanimation.ui.screen.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goonanimation.data.model.AnimationProject
import com.goonanimation.data.model.Frame
import com.goonanimation.data.model.Layer
import com.goonanimation.data.repository.ProjectRepository
import com.goonanimation.engine.DrawingEngine
import com.goonanimation.engine.DrawingTool
import com.goonanimation.engine.ExportEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ViewModel for the animation editor.
 *
 * Manages:
 * - Animation project state (frames, layers)
 * - Drawing tool state (active tool, color, size, opacity)
 * - Undo/redo history
 * - Onion skin rendering
 * - Animation preview playback
 * - Export operations
 * - Autosave
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    val drawingEngine = DrawingEngine()

    // ──────────────────────── Project State ────────────────────────

    private val _project = MutableStateFlow(createNewProject())
    val project: StateFlow<AnimationProject> = _project.asStateFlow()

    private val _currentFrameIndex = MutableStateFlow(0)
    val currentFrameIndex: StateFlow<Int> = _currentFrameIndex.asStateFlow()

    private val _currentLayerIndex = MutableStateFlow(0)
    val currentLayerIndex: StateFlow<Int> = _currentLayerIndex.asStateFlow()

    // ──────────────────────── Tool State ────────────────────────

    private val _activeTool = MutableStateFlow(DrawingTool.PEN)
    val activeTool: StateFlow<DrawingTool> = _activeTool.asStateFlow()

    private val _brushSize = MutableStateFlow(8f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _opacity = MutableStateFlow(1f)
    val opacity: StateFlow<Float> = _opacity.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.BLACK)
    val selectedColor: StateFlow<Int> = _selectedColor.asStateFlow()

    // ──────────────────────── Onion Skin ────────────────────────

    private val _onionSkinEnabled = MutableStateFlow(true)
    val onionSkinEnabled: StateFlow<Boolean> = _onionSkinEnabled.asStateFlow()

    private val _onionSkinOpacity = MutableStateFlow(0.35f)
    val onionSkinOpacity: StateFlow<Float> = _onionSkinOpacity.asStateFlow()

    // ──────────────────────── Playback ────────────────────────

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackFrame = MutableStateFlow(0)
    val playbackFrame: StateFlow<Int> = _playbackFrame.asStateFlow()

    private var playbackJob: Job? = null

    // ──────────────────────── Export ────────────────────────

    private val _exportProgress = MutableStateFlow(-1)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    private val _exportMessage = MutableStateFlow("")
    val exportMessage: StateFlow<String> = _exportMessage.asStateFlow()

    // ──────────────────────── Undo/Redo ────────────────────────

    /** Stack of bitmap snapshots for undo operations */
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private val maxUndoSteps = 30

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ──────────────────────── Canvas Invalidation ────────────────────────

    /** Incremented to force canvas recomposition after drawing changes */
    private val _canvasVersion = MutableStateFlow(0)
    val canvasVersion: StateFlow<Int> = _canvasVersion.asStateFlow()

    // ──────────────────────── Layers Panel ────────────────────────

    private val _showLayersPanel = MutableStateFlow(false)
    val showLayersPanel: StateFlow<Boolean> = _showLayersPanel.asStateFlow()

    // ──────────────────────── Autosave ────────────────────────

    private var autosaveJob: Job? = null

    // ──────────────────────── Project Management ────────────────────────

    private fun createNewProject(name: String = "New Animation"): AnimationProject {
        val canvasW = 1080
        val canvasH = 1080
        val layer = Layer(
            name = "Layer 1",
            bitmap = drawingEngine.createBlankBitmap(canvasW, canvasH)
        )
        val frame = Frame(layers = mutableListOf(layer), index = 0)
        return AnimationProject(
            name = name,
            frames = mutableListOf(frame),
            canvasWidth = canvasW,
            canvasHeight = canvasH
        )
    }

    fun newProject(name: String = "New Animation") {
        playbackJob?.cancel()
        _isPlaying.value = false
        undoStack.clear()
        redoStack.clear()
        _project.value = createNewProject(name)
        _currentFrameIndex.value = 0
        _currentLayerIndex.value = 0
        updateUndoRedoState()
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val loaded = repository.loadProject(projectId)
            if (loaded != null && loaded.frames.isNotEmpty()) {
                _project.value = loaded
                _currentFrameIndex.value = 0
                _currentLayerIndex.value = 0
                undoStack.clear()
                redoStack.clear()
                updateUndoRedoState()
                invalidateCanvas()
            }
        }
    }

    fun saveProject() {
        viewModelScope.launch {
            repository.saveProject(_project.value.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(3000) // Autosave 3 seconds after last change
            repository.saveProject(_project.value.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // ──────────────────────── Frame Operations ────────────────────────

    /** Returns the currently active frame */
    fun currentFrame(): Frame? {
        val proj = _project.value
        val idx = _currentFrameIndex.value
        return proj.frames.getOrNull(idx)
    }

    /** Returns the currently active layer bitmap */
    fun currentLayerBitmap(): Bitmap? {
        val frame = currentFrame() ?: return null
        val layerIdx = _currentLayerIndex.value
        return frame.layers.getOrNull(layerIdx)?.bitmap
    }

    fun selectFrame(index: Int) {
        val proj = _project.value
        if (index in proj.frames.indices) {
            _currentFrameIndex.value = index
            _currentLayerIndex.value = 0
            invalidateCanvas()
        }
    }

    fun addFrame() {
        val proj = _project.value
        val newLayer = Layer(
            name = "Layer 1",
            bitmap = drawingEngine.createBlankBitmap(proj.canvasWidth, proj.canvasHeight)
        )
        val newFrame = Frame(
            layers = mutableListOf(newLayer),
            index = proj.frames.size
        )
        val updatedFrames = proj.frames.toMutableList().apply { add(newFrame) }
        _project.value = proj.copy(frames = updatedFrames)
        _currentFrameIndex.value = updatedFrames.lastIndex
        _currentLayerIndex.value = 0
        invalidateCanvas()
        scheduleAutosave()
    }

    fun duplicateFrame(index: Int) {
        val proj = _project.value
        val frame = proj.frames.getOrNull(index) ?: return
        val dupLayers = frame.layers.map { it.deepCopy() }.toMutableList()
        val dupFrame = Frame(
            id = UUID.randomUUID().toString(),
            layers = dupLayers,
            durationMs = frame.durationMs,
            index = index + 1
        )
        val updatedFrames = proj.frames.toMutableList().apply { add(index + 1, dupFrame) }
        _project.value = proj.copy(frames = updatedFrames)
        _currentFrameIndex.value = index + 1
        invalidateCanvas()
        scheduleAutosave()
    }

    fun deleteFrame(index: Int) {
        val proj = _project.value
        if (proj.frames.size <= 1) return // Keep at least one frame
        val updatedFrames = proj.frames.toMutableList().apply { removeAt(index) }
        _project.value = proj.copy(frames = updatedFrames)
        val newIdx = (index - 1).coerceAtLeast(0)
        _currentFrameIndex.value = newIdx
        invalidateCanvas()
        scheduleAutosave()
    }

    // ──────────────────────── Layer Operations ────────────────────────

    fun addLayer() {
        val proj = _project.value
        val frame = currentFrame() ?: return
        val newLayer = Layer(
            name = "Layer ${frame.layers.size + 1}",
            bitmap = drawingEngine.createBlankBitmap(proj.canvasWidth, proj.canvasHeight)
        )
        frame.layers.add(newLayer)
        _currentLayerIndex.value = frame.layers.lastIndex
        invalidateCanvas()
    }

    fun selectLayer(index: Int) {
        val frame = currentFrame() ?: return
        if (index in frame.layers.indices) {
            _currentLayerIndex.value = index
        }
    }

    fun toggleLayerVisibility(layerIndex: Int) {
        val frame = currentFrame() ?: return
        val layer = frame.layers.getOrNull(layerIndex) ?: return
        frame.layers[layerIndex] = layer.copy(isVisible = !layer.isVisible)
        invalidateCanvas()
    }

    fun deleteLayer(layerIndex: Int) {
        val frame = currentFrame() ?: return
        if (frame.layers.size <= 1) return
        frame.layers.removeAt(layerIndex)
        _currentLayerIndex.value = (_currentLayerIndex.value - 1).coerceAtLeast(0)
        invalidateCanvas()
    }

    // ──────────────────────── Drawing Operations ────────────────────────

    fun onDrawingStart(x: Float, y: Float) {
        val bitmap = currentLayerBitmap() ?: return
        // Save snapshot for undo before modifying
        pushUndoSnapshot(bitmap)
        drawingEngine.onTouchDown(
            x, y,
            _activeTool.value,
            _selectedColor.value,
            _brushSize.value,
            _opacity.value,
            bitmap
        )
        invalidateCanvas()
    }

    fun onDrawingMove(x: Float, y: Float) {
        val bitmap = currentLayerBitmap() ?: return
        drawingEngine.onTouchMove(
            x, y,
            _activeTool.value,
            _selectedColor.value,
            _brushSize.value,
            _opacity.value,
            bitmap
        )
        invalidateCanvas()
    }

    fun onDrawingEnd(x: Float, y: Float) {
        val bitmap = currentLayerBitmap() ?: return
        drawingEngine.onTouchUp(
            x, y,
            _activeTool.value,
            _selectedColor.value,
            _brushSize.value,
            _opacity.value,
            bitmap
        )
        invalidateCanvas()
        scheduleAutosave()
    }

    // ──────────────────────── Undo / Redo ────────────────────────

    private fun pushUndoSnapshot(bitmap: Bitmap) {
        val snapshot = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        undoStack.addLast(snapshot)
        if (undoStack.size > maxUndoSteps) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val bitmap = currentLayerBitmap() ?: return
        // Push current state to redo stack
        redoStack.addLast(bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true))
        // Restore previous state
        val previous = undoStack.removeLast()
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(previous, 0f, 0f, null)
        previous.recycle()
        updateUndoRedoState()
        invalidateCanvas()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val bitmap = currentLayerBitmap() ?: return
        undoStack.addLast(bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true))
        val next = redoStack.removeLast()
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(next, 0f, 0f, null)
        next.recycle()
        updateUndoRedoState()
        invalidateCanvas()
    }

    fun clearCanvas() {
        val bitmap = currentLayerBitmap() ?: return
        pushUndoSnapshot(bitmap)
        bitmap.eraseColor(Color.TRANSPARENT)
        invalidateCanvas()
        scheduleAutosave()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // ──────────────────────── Tool Settings ────────────────────────

    fun setTool(tool: DrawingTool) { _activeTool.value = tool }
    fun setBrushSize(size: Float) { _brushSize.value = size.coerceIn(1f, 80f) }
    fun setOpacity(opacity: Float) { _opacity.value = opacity.coerceIn(0.01f, 1f) }
    fun setColor(color: Int) { _selectedColor.value = color }
    fun toggleOnionSkin() { _onionSkinEnabled.value = !_onionSkinEnabled.value }
    fun setOnionSkinOpacity(opacity: Float) { _onionSkinOpacity.value = opacity }
    fun toggleLayersPanel() { _showLayersPanel.value = !_showLayersPanel.value }

    // ──────────────────────── Playback ────────────────────────

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        _playbackFrame.value = _currentFrameIndex.value
        val proj = _project.value
        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                val frame = proj.frames.getOrNull(_playbackFrame.value)
                delay(frame?.durationMs?.toLong() ?: (1000L / proj.fps))
                _playbackFrame.value = (_playbackFrame.value + 1) % proj.frames.size
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun stopPlayback() {
        pause()
        _playbackFrame.value = 0
    }

    // ──────────────────────── Export ────────────────────────

    fun exportGif(outputDir: File) {
        viewModelScope.launch {
            _exportProgress.value = 0
            _exportMessage.value = "Exporting GIF..."
            val proj = _project.value
            val frames = proj.frames.map { it.composite(proj.canvasWidth, proj.canvasHeight) }
            val file = File(outputDir, "${proj.name.replace(" ", "_")}.gif")
            ExportEngine.exportGif(frames, file, proj.fps) { progress ->
                _exportProgress.value = progress
            }
            _exportMessage.value = "GIF saved to ${file.absolutePath}"
            _exportProgress.value = -1
        }
    }

    fun exportMp4(outputDir: File) {
        viewModelScope.launch {
            _exportProgress.value = 0
            _exportMessage.value = "Exporting MP4..."
            val proj = _project.value
            val frames = proj.frames.map { it.composite(proj.canvasWidth, proj.canvasHeight) }
            val file = File(outputDir, "${proj.name.replace(" ", "_")}.mp4")
            ExportEngine.exportMp4(frames, file, proj.fps) { progress ->
                _exportProgress.value = progress
            }
            _exportMessage.value = "MP4 saved to ${file.absolutePath}"
            _exportProgress.value = -1
        }
    }

    // ──────────────────────── Onion Skin ────────────────────────

    /**
     * Returns a composited onion skin bitmap showing the previous and next frames
     * with reduced opacity for animation reference.
     */
    fun buildOnionSkinBitmap(width: Int, height: Int): Bitmap? {
        if (!_onionSkinEnabled.value) return null
        val proj = _project.value
        val currentIdx = _currentFrameIndex.value
        val opacity = _onionSkinOpacity.value

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = android.graphics.Paint()

        // Previous frame in red tint
        if (currentIdx > 0) {
            val prev = proj.frames.getOrNull(currentIdx - 1)?.composite(width, height)
            if (prev != null) {
                paint.alpha = (opacity * 0.7f * 255).toInt().coerceIn(0, 255)
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        setScale(1f, 0.2f, 0.2f, 1f) // Red tint
                    }
                )
                canvas.drawBitmap(prev, 0f, 0f, paint)
                prev.recycle()
            }
        }

        // Next frame in blue tint
        if (currentIdx < proj.frames.lastIndex) {
            val next = proj.frames.getOrNull(currentIdx + 1)?.composite(width, height)
            if (next != null) {
                paint.alpha = (opacity * 0.5f * 255).toInt().coerceIn(0, 255)
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply {
                        setScale(0.2f, 0.2f, 1f, 1f) // Blue tint
                    }
                )
                canvas.drawBitmap(next, 0f, 0f, paint)
                next.recycle()
            }
        }

        return result
    }

    // ──────────────────────── Helpers ────────────────────────

    private fun invalidateCanvas() {
        _canvasVersion.value += 1
    }

    override fun onCleared() {
        super.onCleared()
        autosaveJob?.cancel()
        playbackJob?.cancel()
        // Recycle undo/redo bitmaps
        undoStack.forEach { it.recycle() }
        redoStack.forEach { it.recycle() }
    }
}
