package com.esmer.queenfinder.detection

import android.graphics.Bitmap

/** Runs the queen model on one upright frame. Implementations are single-threaded. */
interface QueenDetector : AutoCloseable {
    /** Model input width and height in pixels. Valid after [load]. */
    val inputWidth: Int
    val inputHeight: Int
    val backend: Backend
    val labels: List<String>
    val modelName: String

    /** Milliseconds the last [detect] spent inside the interpreter only. */
    val lastModelMs: Long

    /** Loads the model on the calling thread. Must be called on the inference thread. */
    fun load()

    /**
     * Detects bees in a frame (any orientation; the caller maps boxes to upright space).
     * @param confidence minimum score to keep
     * @return detections with boxes normalized to the frame as given
     */
    fun detect(frame: Bitmap, confidence: Float): List<Detection>
}
