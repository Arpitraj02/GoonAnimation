package com.goonanimation.engine

/**
 * All available drawing tools in the animation editor.
 */
enum class DrawingTool {
    /** Fine-tip pen for precise lines */
    PEN,
    /** Soft brush with feathered edges */
    BRUSH,
    /** Erases pixels back to transparent */
    ERASER,
    /** Flood-fills a region with the selected color */
    FILL
}
