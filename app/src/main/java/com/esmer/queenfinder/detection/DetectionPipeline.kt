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
 *
 * Inference runs on the frame in its sensor orientation (landscape on phones),
 * which matches the model's 16:9 input; boxes are rotated to the upright frame
 * afterwards. That avoids a bitmap rotation per frame and keeps every pixel.
 */
class DetectionPipeline(
    private val detector: QueenDetector,
    private val onQueenStable: () -> Unit,
) : ImageAnalysis.Analyzer {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread({
            // Display-urgent priority keeps the scheduler from parking this work on small cores.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            r.run()
        }, "queen-inference")
    }

    private var frameCount = 0L

    private val _state = MutableStateFlow(DetectionState())
    val state: StateFlow<DetectionState> = _state.asStateFlow()

    /** Last sensor-orientation frame and its rotation, kept for snapshots. */
    @Volatile
    var lastFrame: Bitmap? = null
        private set
    @Volatile
    var lastRotation: Int = 0
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
            val rotation = image.imageInfo.rotationDegrees
            val frame = image.toBitmap()
            val tConv = SystemClock.elapsedRealtime()
            lastFrame = frame
            lastRotation = rotation

            val raw = detector.detect(frame, confidence)
            val detections = raw.map { rotateDetection(it, rotation) }
            val stable = tracker.update(detections.filter { it.isQueen })
            if (tracker.newlyStable) onQueenStable()
            val t1 = SystemClock.elapsedRealtime()

            if (++frameCount % 60L == 0L && detector is LiteRtQueenDetector) {
                Log.d(TAG, "frame ${t1 - t0} ms: toBitmap ${tConv - t0}, pre ${detector.lastPreMs}, " +
                    "model ${detector.lastModelMs}, post ${detector.lastPostMs}, " +
                    "size ${frame.width}x${frame.height}")
            }

            if (lastFrameAt != 0L) {
                val instant = 1000f / (t1 - lastFrameAt).coerceAtLeast(1)
                fpsSmoothed = if (fpsSmoothed == 0f) instant else fpsSmoothed * 0.8f + instant * 0.2f
            }
            lastFrameAt = t1

            val swap = rotation % 180 != 0
            _state.value = DetectionState(
                detections = detections,
                stableQueen = stable,
                frameWidth = if (swap) frame.height else frame.width,
                frameHeight = if (swap) frame.width else frame.height,
                fps = fpsSmoothed,
                inferenceMs = t1 - t0,
                modelMs = detector.lastModelMs,
                backend = detector.backend,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "analyze failed", t)
            _state.value = _state.value.copy(error = t.message ?: t.toString())
        } finally {
            image.close()
        }
    }

    /** The last frame rotated upright, for saving. Cheap enough for a button press. */
    fun uprightSnapshot(): Bitmap? = lastFrame?.let { FramePreprocessor.upright(it, lastRotation) }

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
