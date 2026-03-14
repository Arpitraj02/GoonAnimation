package com.goonanimation.data.model

import java.util.UUID

/**
 * Top-level animation project model.
 * Holds all frames, metadata, and canvas dimensions.
 */
data class AnimationProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled",
    val frames: MutableList<Frame> = mutableListOf(),
    val fps: Int = 12,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1080,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /** Duration of the full animation in milliseconds */
    val totalDurationMs: Int
        get() = frames.sumOf { it.durationMs }

    /** Duration of each frame in milliseconds based on current fps */
    val frameDurationMs: Int
        get() = 1000 / fps
}
