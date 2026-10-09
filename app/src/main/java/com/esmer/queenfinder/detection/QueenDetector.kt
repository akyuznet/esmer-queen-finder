package com.esmer.queenfinder.detection

import android.graphics.Bitmap

/** Runs the queen model on one upright frame. Implementations are single-threaded. */
interface QueenDetector : AutoCloseable {
    /** Model input edge in pixels (square). Valid after [load]. */
    val inputSize: Int
    val backend: Backend
    val labels: List<String>
    val modelName: String

    /** Loads the model on the calling thread. Must be called on the inference thread. */
    fun load()

    /**
     * Detects bees in an upright frame.
     * @param confidence minimum score to keep
     * @return detections with boxes normalized to the frame
     */
    fun detect(frame: Bitmap, confidence: Float): List<Detection>
}
