package com.esmer.queenfinder.detection

/**
 * Axis-aligned box in normalized coordinates (0..1) of the upright camera frame.
 * Pure Kotlin so the detection logic is unit-testable without Android.
 */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height
}

/**
 * One detected bee.
 *
 * @param classIndex index into the model's label list
 * @param label the label text, e.g. "queen" or "drone"
 * @param score confidence 0..1
 * @param box bounding box in normalized coordinates (0..1) of the upright camera frame
 */
data class Detection(
    val classIndex: Int,
    val label: String,
    val score: Float,
    val box: Box,
) {
    val isQueen: Boolean get() = label == LABEL_QUEEN
    val isDrone: Boolean get() = label == LABEL_DRONE

    companion object {
        const val LABEL_QUEEN = "queen"
        const val LABEL_DRONE = "drone"
    }
}

/** A queen that has been seen in enough consecutive frames to trust. */
data class StableQueen(
    val box: Box,
    val score: Float,
    val hits: Int,
)

/** What the camera screen draws and shows. */
data class DetectionState(
    val detections: List<Detection> = emptyList(),
    val stableQueen: StableQueen? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val fps: Float = 0f,
    /** Whole pipeline per frame: convert, letterbox, model, decode, track. */
    val inferenceMs: Long = 0,
    /** Interpreter only. */
    val modelMs: Long = 0,
    val backend: Backend = Backend.NONE,
    val error: String? = null,
)

enum class Backend { NONE, GPU, CPU }
