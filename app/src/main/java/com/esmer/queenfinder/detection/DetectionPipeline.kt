package com.esmer.queenfinder.detection

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Wires camera frames to the detector and the track filter and publishes the
 * result as a [StateFlow]. Frames are analysed on a single background thread;
 * CameraX drops frames while that thread is busy.
 */
class DetectionPipeline(
    private val detector: QueenDetector,
    private val onQueenStable: () -> Unit,
) : ImageAnalysis.Analyzer {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "queen-inference").apply { priority = Thread.MAX_PRIORITY }
    }

    private val _state = MutableStateFlow(DetectionState())
    val state: StateFlow<DetectionState> = _state.asStateFlow()

    /** Last upright frame, kept for snapshots. Read on the inference thread or copy it. */
    @Volatile
    var lastFrame: Bitmap? = null
        private set

    @Volatile var confidence: Float = 0.5f
    @Volatile var stableFrames: Int = 5
        set(value) { field = value; tracker.stableFrames = value }

    private val tracker = TrackFilter(stableFrames)
    private var loaded = false
    private var loadFailed: String? = null
    private var lastFrameAt = 0L
    private var fpsSmoothed = 0f

    override fun analyze(image: ImageProxy) {
        try {
            if (!ensureLoaded()) return
            val t0 = SystemClock.elapsedRealtime()
            val frame = FramePreprocessor.upright(image.toBitmap(), image.imageInfo.rotationDegrees)
            lastFrame = frame
            val detections = detector.detect(frame, confidence)
            val stable = tracker.update(detections.filter { it.isQueen })
            if (tracker.newlyStable) onQueenStable()
            val t1 = SystemClock.elapsedRealtime()

            if (lastFrameAt != 0L) {
                val instant = 1000f / (t1 - lastFrameAt).coerceAtLeast(1)
                fpsSmoothed = if (fpsSmoothed == 0f) instant else fpsSmoothed * 0.8f + instant * 0.2f
            }
            lastFrameAt = t1

            _state.value = DetectionState(
                detections = detections,
                stableQueen = stable,
                frameWidth = frame.width,
                frameHeight = frame.height,
                fps = fpsSmoothed,
                inferenceMs = t1 - t0,
                backend = detector.backend,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "analyze failed", t)
            _state.value = _state.value.copy(error = t.message ?: t.toString())
        } finally {
            image.close()
        }
    }

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadFailed != null) return false
        return try {
            detector.load()
            loaded = true
            _state.value = _state.value.copy(backend = detector.backend, error = null)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "model load failed", t)
            loadFailed = t.message ?: t.toString()
            _state.value = _state.value.copy(error = loadFailed)
            false
        }
    }

    fun resetTracking() {
        executor.execute { tracker.reset() }
    }

    fun close() {
        executor.execute { detector.close() }
        executor.shutdown()
    }

    companion object {
        private const val TAG = "DetectionPipeline"
    }
}
