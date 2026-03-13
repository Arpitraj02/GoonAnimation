package com.goonanimation.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.goonanimation.data.model.AnimationProject
import com.goonanimation.data.model.Frame
import com.goonanimation.data.model.Layer
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Repository for persisting and loading animation projects to/from internal storage.
 * Each project is stored in its own directory with JSON metadata and PNG frame bitmaps.
 */
class ProjectRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val projectsDir: File get() = File(context.filesDir, "projects")

    init {
        projectsDir.mkdirs()
    }

    /** Returns a list of saved project metadata summaries */
    suspend fun listProjects(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        projectsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val metaFile = File(dir, "meta.json")
                if (metaFile.exists()) {
                    try {
                        val json = metaFile.readText()
                        gson.fromJson(json, ProjectSummary::class.java)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    /** Saves a project to disk */
    suspend fun saveProject(project: AnimationProject) = withContext(Dispatchers.IO) {
        val projectDir = File(projectsDir, project.id)
        projectDir.mkdirs()

        // Save metadata (without bitmaps)
        val summary = ProjectSummary(
            id = project.id,
            name = project.name,
            fps = project.fps,
            canvasWidth = project.canvasWidth,
            canvasHeight = project.canvasHeight,
            frameCount = project.frames.size,
            createdAt = project.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        File(projectDir, "meta.json").writeText(gson.toJson(summary))

        // Save each frame's layers as PNG files
        project.frames.forEachIndexed { frameIndex, frame ->
            val frameDir = File(projectDir, "frame_${frameIndex}_${frame.id}")
            frameDir.mkdirs()
            // Save frame metadata
            val frameMeta = FrameMeta(
                id = frame.id,
                durationMs = frame.durationMs,
                index = frameIndex,
                layerCount = frame.layers.size
            )
            File(frameDir, "frame_meta.json").writeText(gson.toJson(frameMeta))
            // Save each layer
            frame.layers.forEachIndexed { layerIndex, layer ->
                val layerFile = File(frameDir, "layer_$layerIndex.png")
                FileOutputStream(layerFile).use { out ->
                    layer.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val layerMeta = LayerMeta(
                    id = layer.id,
                    name = layer.name,
                    isVisible = layer.isVisible,
                    opacity = layer.opacity,
                    isLocked = layer.isLocked
                )
                File(frameDir, "layer_${layerIndex}_meta.json").writeText(gson.toJson(layerMeta))
            }
        }
    }

    /** Loads a full project (with bitmaps) from disk */
    suspend fun loadProject(projectId: String): AnimationProject? = withContext(Dispatchers.IO) {
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) return@withContext null

        val metaFile = File(projectDir, "meta.json")
        val summary = try {
            gson.fromJson(metaFile.readText(), ProjectSummary::class.java)
        } catch (e: Exception) {
            return@withContext null
        }

        val frames = mutableListOf<Frame>()

        // Load frames sorted by index
        projectDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("frame_") }
            ?.sortedBy {
                it.name.removePrefix("frame_").split("_").firstOrNull()?.toIntOrNull() ?: 0
            }
            ?.forEach { frameDir ->
                val frameMeta = try {
                    gson.fromJson(
                        File(frameDir, "frame_meta.json").readText(),
                        FrameMeta::class.java
                    )
                } catch (e: Exception) {
                    return@forEach
                }

                val layers = mutableListOf<Layer>()
                var layerIndex = 0
                while (true) {
                    val layerFile = File(frameDir, "layer_$layerIndex.png")
                    val layerMetaFile = File(frameDir, "layer_${layerIndex}_meta.json")
                    if (!layerFile.exists()) break
                    val bitmap = BitmapFactory.decodeFile(layerFile.absolutePath)
                        ?.copy(Bitmap.Config.ARGB_8888, true) ?: break
                    val layerMeta = if (layerMetaFile.exists()) {
                        try {
                            gson.fromJson(layerMetaFile.readText(), LayerMeta::class.java)
                        } catch (e: Exception) {
                            LayerMeta()
                        }
                    } else LayerMeta()

                    layers.add(
                        Layer(
                            id = layerMeta.id ?: UUID.randomUUID().toString(),
                            name = layerMeta.name ?: "Layer ${layerIndex + 1}",
                            bitmap = bitmap,
                            isVisible = layerMeta.isVisible,
                            opacity = layerMeta.opacity,
                            isLocked = layerMeta.isLocked
                        )
                    )
                    layerIndex++
                }

                frames.add(
                    Frame(
                        id = frameMeta.id ?: UUID.randomUUID().toString(),
                        layers = layers,
                        durationMs = frameMeta.durationMs,
                        index = frameMeta.index
                    )
                )
            }

        AnimationProject(
            id = summary.id,
            name = summary.name,
            frames = frames,
            fps = summary.fps,
            canvasWidth = summary.canvasWidth,
            canvasHeight = summary.canvasHeight,
            createdAt = summary.createdAt,
            updatedAt = summary.updatedAt
        )
    }

    /** Deletes a project from disk */
    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsDir, projectId).deleteRecursively()
    }

    // Data transfer objects for JSON serialization
    data class ProjectSummary(
        val id: String = UUID.randomUUID().toString(),
        val name: String = "Untitled",
        val fps: Int = 12,
        val canvasWidth: Int = 1080,
        val canvasHeight: Int = 1080,
        val frameCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    private data class FrameMeta(
        val id: String? = null,
        val durationMs: Int = 83,
        val index: Int = 0,
        val layerCount: Int = 0
    )

    private data class LayerMeta(
        val id: String? = null,
        val name: String? = null,
        val isVisible: Boolean = true,
        val opacity: Float = 1f,
        val isLocked: Boolean = false
    )
}
